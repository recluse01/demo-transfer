package com.demo.transfer.transfer.workflow;

import com.demo.transfer.common.AccountType;
import com.demo.transfer.common.ApiResponse;
import com.demo.transfer.common.AssetOperationRequest;
import com.demo.transfer.common.AssetOperationResponse;
import com.demo.transfer.common.TransferDirection;
import com.demo.transfer.common.TransferMode;
import com.demo.transfer.transfer.domain.TransferOrder;
import com.demo.transfer.transfer.service.AccountClientRouter;
import com.demo.transfer.transfer.service.TransferOrderStateService;
import io.temporal.failure.ApplicationFailure;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * TransferActivities 实现。
 *
 * <p>每个方法遵循同一模式：
 * 1. 从数据库加载订单（独立只读事务，随即释放连接）。
 * 2. 在无事务环境下发起 Feign 调用。
 * 3. 根据结果在独立事务内更新 transfer_order 状态。
 * 失败时抛出 RuntimeException，由 Temporal 的 RetryPolicy 自动重试。
 */
@Slf4j
@Component
public class TransferActivitiesImpl implements TransferActivities {
    private final AccountClientRouter router;
    private final TransferOrderStateService stateService;

    public TransferActivitiesImpl(AccountClientRouter router, TransferOrderStateService stateService) {
        this.router = router;
        this.stateService = stateService;
    }

    @Override
    public void freeze(String transferId) {
        log.info("开始执行冻结 Activity，transferId={}", transferId);
        TransferOrder order = stateService.loadOrder(transferId);
        ApiResponse<AssetOperationResponse> response = router.client(order.getSourceAccountType())
                .freeze(buildRequest(order));
        if (!response.isSuccess()) {
            log.warn("冻结 Activity 执行失败，transferId={}, code={}, message={}",
                    transferId, response.getCode(), response.getMessage());
            stateService.markFreezeFailed(transferId, response.getCode(), response.getMessage());
            throw ApplicationFailure.newNonRetryableFailure(
                    "freeze failed: " + response.getMessage(), response.getCode());
        }
        if (TransferMode.MANUAL_REVIEW == order.getTransferMode()) {
            stateService.markWaitReview(transferId);
        }
        // AUTO_WITHDRAW：冻结成功后 Workflow 直接推进到 confirmDebit，无需写入中间状态
        log.info("冻结 Activity 执行完成，transferId={}, mode={}", transferId, order.getTransferMode());
    }

    @Override
    public void confirmDebit(String transferId) {
        log.info("开始执行确认扣减 Activity，transferId={}", transferId);
        TransferOrder order = stateService.loadOrder(transferId);
        ApiResponse<AssetOperationResponse> response = router.client(order.getSourceAccountType())
                .confirmDebit(buildRequest(order));
        if (!response.isSuccess()) {
            log.warn("确认扣减 Activity 执行失败，transferId={}, code={}, message={}",
                    transferId, response.getCode(), response.getMessage());
            throw new RuntimeException("confirmDebit failed: " + response.getMessage());
        }
        stateService.markDebitSuccess(transferId);
        log.info("确认扣减 Activity 执行完成，transferId={}", transferId);
    }

    @Override
    public void credit(String transferId) {
        log.info("开始执行入账 Activity，transferId={}", transferId);
        TransferOrder order = stateService.loadOrder(transferId);
        ApiResponse<AssetOperationResponse> response = router.client(order.getTargetAccountType())
                .credit(buildRequest(order));
        if (!response.isSuccess()) {
            // credit 失败：持久化失败状态后上抛，不触发反向补偿
            log.warn("入账 Activity 执行失败，transferId={}, code={}, message={}",
                    transferId, response.getCode(), response.getMessage());
            stateService.markCreditFailed(transferId, response.getCode(), response.getMessage());
            throw new RuntimeException("credit failed: " + response.getMessage());
        }
        stateService.markSuccess(transferId);
        log.info("入账 Activity 执行完成，transferId={}", transferId);
    }

    @Override
    public void cancelFreeze(String transferId) {
        log.info("开始执行取消冻结 Activity，transferId={}", transferId);
        TransferOrder order = stateService.loadOrder(transferId);
        ApiResponse<AssetOperationResponse> response = router.client(order.getSourceAccountType())
                .cancelFreeze(buildRequest(order));
        if (!response.isSuccess()) {
            log.warn("取消冻结 Activity 执行失败，transferId={}, code={}, message={}",
                    transferId, response.getCode(), response.getMessage());
            throw new RuntimeException("cancelFreeze failed: " + response.getMessage());
        }
        stateService.markRejected(transferId);
        log.info("取消冻结 Activity 执行完成，transferId={}", transferId);
    }

    private AssetOperationRequest buildRequest(TransferOrder order) {
        TransferDirection direction = AccountType.ACCOUNT_A == order.getSourceAccountType()
                ? TransferDirection.A_TO_B : TransferDirection.B_TO_A;
        return new AssetOperationRequest(order.getTransferId(), order.getUserId(),
                order.getAssetCode(), order.getAmount(), direction);
    }
}
