package com.demo.transfer.transfer.service;

import com.demo.transfer.common.AccountType;
import com.demo.transfer.common.ApiResponse;
import com.demo.transfer.common.AssetOperationRequest;
import com.demo.transfer.common.AssetOperationResponse;
import com.demo.transfer.common.TransferDirection;
import com.demo.transfer.common.TransferMode;
import com.demo.transfer.common.TransferStatus;
import com.demo.transfer.transfer.client.AccountOperationsClient;
import com.demo.transfer.transfer.domain.TransferOrder;
import com.demo.transfer.transfer.domain.TransferStepLog;
import com.demo.transfer.transfer.repository.TransferOrderRepository;
import com.demo.transfer.transfer.repository.TransferStepLogRepository;
import com.demo.transfer.transfer.web.CreateTransferRequest;
import com.demo.transfer.transfer.web.ReviewTransferRequest;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 跨账户转账的 Saga 编排服务。
 *
 * <p>职责包括：
 * 1. 创建转账单并冻结源账户资产。
 * 2. 根据转账模式推进人工审核或提现回调后的后续步骤。
 * 3. 串联源账户扣减、目标账户入账、失败回滚等流程。
 */
@Service
public class TransferSagaService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransferSagaService.class);

    /** 转账主单持久化仓储。 */
    private final TransferOrderRepository orderRepository;
    /** Saga 步骤日志仓储，用于审计和排障。 */
    private final TransferStepLogRepository stepLogRepository;
    /** 账户客户端路由器，用于按账户类型选择实际调用方。 */
    private final AccountClientRouter router;

    public TransferSagaService(TransferOrderRepository orderRepository, TransferStepLogRepository stepLogRepository,
            AccountClientRouter router) {
        this.orderRepository = orderRepository;
        this.stepLogRepository = stepLogRepository;
        this.router = router;
    }

    @Transactional
    public TransferOrder createTransfer(CreateTransferRequest request) {
        // 基础版自动提现只支持 A 到 B，避免进入未实现分支。
        if (TransferMode.AUTO_WITHDRAW == request.getMode() && TransferDirection.A_TO_B != request.getDirection()) {
            throw new IllegalArgumentException("AUTO_WITHDRAW only supports A_TO_B in the basic version");
        }
        AccountType source = router.sourceType(request.getDirection());
        AccountType target = router.targetType(request.getDirection());
        TransferOrder order = TransferOrder.create(UUID.randomUUID().toString(), request.getUserId(), source, target,
                request.getAssetCode(), request.getAmount(), request.getMode());
        LOGGER.info("开始创建转账，transferId={}, userId={}, direction={}, sourceAccount={}, targetAccount={}, assetCode={}, amount={}, mode={}",
                order.getTransferId(), order.getUserId(), request.getDirection(), source, target, order.getAssetCode(),
                order.getAmount(), order.getTransferMode());
        orderRepository.save(order);

        // Saga 第一步：先冻结源账户可用余额，确保后续流程具备幂等和资金约束。
        LOGGER.info("开始冻结源账户资产，transferId={}, userId={}, sourceAccount={}, assetCode={}, amount={}",
                order.getTransferId(), order.getUserId(), source, order.getAssetCode(), order.getAmount());
        ApiResponse<AssetOperationResponse> response = router.client(source).freeze(assetRequest(order, direction(order)));
        if (response.isSuccess()) {
            // 冻结成功后，根据模式进入人工审核或等待提现结果两个分支。
            order.markStatus(TransferMode.MANUAL_REVIEW == request.getMode() ? TransferStatus.WAIT_REVIEW
                    : TransferStatus.WITHDRAW_PENDING);
            log(order, "FREEZE", "SUCCESS", null);
            LOGGER.info("转账冻结成功，transferId={}, userId={}, sourceAccount={}, nextStatus={}",
                    order.getTransferId(), order.getUserId(), source, order.getStatus());
            return orderRepository.saveAndFlush(order);
        }
        order.markFailure(TransferStatus.FREEZE_FAILED, response.getCode(), response.getMessage());
        log(order, "FREEZE", "FAILED", response.getMessage());
        LOGGER.warn("转账冻结失败，transferId={}, userId={}, sourceAccount={}, status={}, errorCode={}, errorMessage={}",
                order.getTransferId(), order.getUserId(), source, order.getStatus(), response.getCode(),
                response.getMessage());
        return orderRepository.saveAndFlush(order);
    }

    @Transactional
    public TransferOrder review(ReviewTransferRequest request) {
        // 人工审核只允许在待审核状态下推进。
        TransferOrder order = load(request.getTransferId());
        requireStatus(order, TransferStatus.WAIT_REVIEW);
        LOGGER.info("开始审核转账，transferId={}, userId={}, approved={}, currentStatus={}",
                order.getTransferId(), order.getUserId(), request.isApproved(), order.getStatus());
        if (request.isApproved()) {
            LOGGER.info("审核通过，开始确认扣减，transferId={}, userId={}, sourceAccount={}, targetAccount={}",
                    order.getTransferId(), order.getUserId(), order.getSourceAccountType(), order.getTargetAccountType());
            return approve(order);
        }
        LOGGER.info("审核拒绝，开始解冻源账户资产，transferId={}, userId={}, sourceAccount={}",
                order.getTransferId(), order.getUserId(), order.getSourceAccountType());
        return cancel(order);
    }

    @Transactional
    public TransferOrder handleWithdrawResult(String transferId, boolean success, String message) {
        // 自动提现模式下，提现结果决定继续扣减还是执行取消冻结。
        TransferOrder order = load(transferId);
        requireStatus(order, TransferStatus.WITHDRAW_PENDING);
        LOGGER.info("收到提现结果，transferId={}, userId={}, success={}, message={}",
                order.getTransferId(), order.getUserId(), success, message);
        if (success) {
            LOGGER.info("提现成功，继续确认扣减，transferId={}, userId={}, sourceAccount={}",
                    order.getTransferId(), order.getUserId(), order.getSourceAccountType());
            return approve(order);
        }
        order.markStatus(TransferStatus.WITHDRAW_FAILED);
        orderRepository.saveAndFlush(order);
        LOGGER.warn("提现失败，开始取消冻结，transferId={}, userId={}, status={}, message={}",
                order.getTransferId(), order.getUserId(), order.getStatus(), message);
        return cancel(order);
    }

    @Transactional(readOnly = true)
    public TransferOrder get(String transferId) {
        return load(transferId);
    }

    /**
     * Saga 第二步：确认扣减源账户冻结余额。
     *
     * <p>只有在冻结成功后才能进入该步骤，失败时保留失败状态，等待重试或人工处理。
     */
    TransferOrder approve(TransferOrder order) {
        LOGGER.info("调用源账户确认扣减，transferId={}, userId={}, sourceAccount={}, assetCode={}, amount={}",
                order.getTransferId(), order.getUserId(), order.getSourceAccountType(), order.getAssetCode(),
                order.getAmount());
        ApiResponse<AssetOperationResponse> debit = sourceClient(order).confirmDebit(assetRequest(order, direction(order)));
        if (!debit.isSuccess()) {
            order.markFailure(TransferStatus.DEBIT_FAILED, debit.getCode(), debit.getMessage());
            log(order, "CONFIRM_DEBIT", "FAILED", debit.getMessage());
            LOGGER.warn("源账户扣减确认失败，transferId={}, userId={}, sourceAccount={}, status={}, errorCode={}, errorMessage={}",
                    order.getTransferId(), order.getUserId(), order.getSourceAccountType(), order.getStatus(),
                    debit.getCode(), debit.getMessage());
            return orderRepository.saveAndFlush(order);
        }
        order.markStatus(TransferStatus.DEBIT_SUCCESS);
        orderRepository.saveAndFlush(order);
        log(order, "CONFIRM_DEBIT", "SUCCESS", null);
        LOGGER.info("源账户扣减确认成功，transferId={}, userId={}, sourceAccount={}, nextStatus={}",
                order.getTransferId(), order.getUserId(), order.getSourceAccountType(), order.getStatus());
        return creditTarget(order);
    }

    /**
     * Saga 第三步：向目标账户入账。
     *
     * <p>该步骤成功后整笔转账结束；失败时进入可重试状态。
     */
    TransferOrder creditTarget(TransferOrder order) {
        LOGGER.info("调用目标账户入账，transferId={}, userId={}, targetAccount={}, assetCode={}, amount={}",
                order.getTransferId(), order.getUserId(), order.getTargetAccountType(), order.getAssetCode(),
                order.getAmount());
        ApiResponse<AssetOperationResponse> credit = targetClient(order).credit(assetRequest(order, direction(order)));
        if (!credit.isSuccess()) {
            order.markFailure(TransferStatus.CREDIT_FAILED, credit.getCode(), credit.getMessage());
            log(order, "CREDIT", "FAILED", credit.getMessage());
            LOGGER.warn("目标账户入账失败，transferId={}, userId={}, targetAccount={}, status={}, errorCode={}, errorMessage={}",
                    order.getTransferId(), order.getUserId(), order.getTargetAccountType(), order.getStatus(),
                    credit.getCode(), credit.getMessage());
            return orderRepository.saveAndFlush(order);
        }
        order.markStatus(TransferStatus.SUCCESS);
        log(order, "CREDIT", "SUCCESS", null);
        LOGGER.info("目标账户入账成功，转账完成，transferId={}, userId={}, targetAccount={}, finalStatus={}",
                order.getTransferId(), order.getUserId(), order.getTargetAccountType(), order.getStatus());
        return orderRepository.saveAndFlush(order);
    }

    /**
     * 回滚步骤：取消源账户冻结。
     *
     * <p>用于审核拒绝、提现失败或其他需要终止流程的场景。
     */
    TransferOrder cancel(TransferOrder order) {
        LOGGER.info("调用源账户取消冻结，transferId={}, userId={}, sourceAccount={}, assetCode={}, amount={}",
                order.getTransferId(), order.getUserId(), order.getSourceAccountType(), order.getAssetCode(),
                order.getAmount());
        ApiResponse<AssetOperationResponse> response = sourceClient(order).cancelFreeze(assetRequest(order, direction(order)));
        if (!response.isSuccess()) {
            order.markFailure(TransferStatus.CANCEL_FAILED, response.getCode(), response.getMessage());
            log(order, "CANCEL_FREEZE", "FAILED", response.getMessage());
            LOGGER.warn("源账户取消冻结失败，transferId={}, userId={}, sourceAccount={}, status={}, errorCode={}, errorMessage={}",
                    order.getTransferId(), order.getUserId(), order.getSourceAccountType(), order.getStatus(),
                    response.getCode(), response.getMessage());
            return orderRepository.saveAndFlush(order);
        }
        order.markStatus(TransferStatus.REJECTED);
        log(order, "CANCEL_FREEZE", "SUCCESS", null);
        LOGGER.info("源账户取消冻结成功，转账已拒绝，transferId={}, userId={}, sourceAccount={}, finalStatus={}",
                order.getTransferId(), order.getUserId(), order.getSourceAccountType(), order.getStatus());
        return orderRepository.saveAndFlush(order);
    }

    TransferOrder load(String transferId) {
        return orderRepository.findByTransferId(transferId)
                .orElseThrow(() -> new IllegalArgumentException("transfer not found"));
    }

    private AccountOperationsClient sourceClient(TransferOrder order) {
        return router.client(order.getSourceAccountType());
    }

    private AccountOperationsClient targetClient(TransferOrder order) {
        return router.client(order.getTargetAccountType());
    }

    private AssetOperationRequest assetRequest(TransferOrder order, TransferDirection direction) {
        // 统一构造账户服务请求，保证所有步骤使用同一组业务主键和资产信息。
        return new AssetOperationRequest(order.getTransferId(), order.getUserId(), order.getAssetCode(),
                order.getAmount(), direction);
    }

    private TransferDirection direction(TransferOrder order) {
        if (AccountType.ACCOUNT_A == order.getSourceAccountType()) {
            return TransferDirection.A_TO_B;
        }
        return TransferDirection.B_TO_A;
    }

    private void requireStatus(TransferOrder order, TransferStatus status) {
        // 状态机保护，避免重复推进或跳步执行。
        if (order.getStatus() != status) {
            throw new IllegalStateException("transfer status must be " + status);
        }
    }

    private void log(TransferOrder order, String step, String status, String error) {
        // 当前示例只记录步骤结果，保留 request/response 字段以便后续扩展更细的审计明细。
        stepLogRepository.save(TransferStepLog.of(order.getTransferId(), step, status, null, null, error));
    }
}
