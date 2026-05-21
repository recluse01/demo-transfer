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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferSagaService {
    private final TransferOrderRepository orderRepository;
    private final TransferStepLogRepository stepLogRepository;
    private final AccountClientRouter router;

    public TransferSagaService(TransferOrderRepository orderRepository, TransferStepLogRepository stepLogRepository,
            AccountClientRouter router) {
        this.orderRepository = orderRepository;
        this.stepLogRepository = stepLogRepository;
        this.router = router;
    }

    @Transactional
    public TransferOrder createTransfer(CreateTransferRequest request) {
        if (TransferMode.AUTO_WITHDRAW == request.getMode() && TransferDirection.A_TO_B != request.getDirection()) {
            throw new IllegalArgumentException("AUTO_WITHDRAW only supports A_TO_B in the basic version");
        }
        AccountType source = router.sourceType(request.getDirection());
        AccountType target = router.targetType(request.getDirection());
        TransferOrder order = TransferOrder.create(UUID.randomUUID().toString(), request.getUserId(), source, target,
                request.getAssetCode(), request.getAmount(), request.getMode());
        orderRepository.save(order);

        ApiResponse<AssetOperationResponse> response = router.client(source).freeze(assetRequest(order, direction(order)));
        if (response.isSuccess()) {
            order.markStatus(TransferMode.MANUAL_REVIEW == request.getMode() ? TransferStatus.WAIT_REVIEW
                    : TransferStatus.WITHDRAW_PENDING);
            log(order, "FREEZE", "SUCCESS", null);
            return orderRepository.saveAndFlush(order);
        }
        order.markFailure(TransferStatus.FREEZE_FAILED, response.getCode(), response.getMessage());
        log(order, "FREEZE", "FAILED", response.getMessage());
        return orderRepository.saveAndFlush(order);
    }

    @Transactional
    public TransferOrder review(ReviewTransferRequest request) {
        TransferOrder order = load(request.getTransferId());
        requireStatus(order, TransferStatus.WAIT_REVIEW);
        if (request.isApproved()) {
            return approve(order);
        }
        return cancel(order);
    }

    @Transactional
    public TransferOrder handleWithdrawResult(String transferId, boolean success, String message) {
        TransferOrder order = load(transferId);
        requireStatus(order, TransferStatus.WITHDRAW_PENDING);
        if (success) {
            return approve(order);
        }
        order.markStatus(TransferStatus.WITHDRAW_FAILED);
        orderRepository.saveAndFlush(order);
        return cancel(order);
    }

    @Transactional(readOnly = true)
    public TransferOrder get(String transferId) {
        return load(transferId);
    }

    TransferOrder approve(TransferOrder order) {
        ApiResponse<AssetOperationResponse> debit = sourceClient(order).confirmDebit(assetRequest(order, direction(order)));
        if (!debit.isSuccess()) {
            order.markFailure(TransferStatus.DEBIT_FAILED, debit.getCode(), debit.getMessage());
            log(order, "CONFIRM_DEBIT", "FAILED", debit.getMessage());
            return orderRepository.saveAndFlush(order);
        }
        order.markStatus(TransferStatus.DEBIT_SUCCESS);
        orderRepository.saveAndFlush(order);
        log(order, "CONFIRM_DEBIT", "SUCCESS", null);
        return creditTarget(order);
    }

    TransferOrder creditTarget(TransferOrder order) {
        ApiResponse<AssetOperationResponse> credit = targetClient(order).credit(assetRequest(order, direction(order)));
        if (!credit.isSuccess()) {
            order.markFailure(TransferStatus.CREDIT_FAILED, credit.getCode(), credit.getMessage());
            log(order, "CREDIT", "FAILED", credit.getMessage());
            return orderRepository.saveAndFlush(order);
        }
        order.markStatus(TransferStatus.SUCCESS);
        log(order, "CREDIT", "SUCCESS", null);
        return orderRepository.saveAndFlush(order);
    }

    TransferOrder cancel(TransferOrder order) {
        ApiResponse<AssetOperationResponse> response = sourceClient(order).cancelFreeze(assetRequest(order, direction(order)));
        if (!response.isSuccess()) {
            order.markFailure(TransferStatus.CANCEL_FAILED, response.getCode(), response.getMessage());
            log(order, "CANCEL_FREEZE", "FAILED", response.getMessage());
            return orderRepository.saveAndFlush(order);
        }
        order.markStatus(TransferStatus.REJECTED);
        log(order, "CANCEL_FREEZE", "SUCCESS", null);
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
        if (order.getStatus() != status) {
            throw new IllegalStateException("transfer status must be " + status);
        }
    }

    private void log(TransferOrder order, String step, String status, String error) {
        stepLogRepository.save(TransferStepLog.of(order.getTransferId(), step, status, null, null, error));
    }
}
