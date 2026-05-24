package com.demo.transfer.transfer.service;

import com.demo.transfer.common.TransferStatus;
import com.demo.transfer.transfer.domain.TransferOrder;
import com.demo.transfer.transfer.repository.TransferOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 转账主单状态服务。
 *
 * <p>每个方法在独立事务中执行，供 TransferActivitiesImpl 在 Feign 调用完成后单独调用，
 * 确保数据库连接不在远程调用期间被持有。
 */
@Service
public class TransferOrderStateService {
    private final TransferOrderRepository orderRepository;

    public TransferOrderStateService(TransferOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public TransferOrder loadOrder(String transferId) {
        return orderRepository.findByTransferId(transferId)
                .orElseThrow(() -> new IllegalArgumentException("transfer not found: " + transferId));
    }

    @Transactional
    public TransferOrder createOrder(TransferOrder order) {
        return orderRepository.saveAndFlush(order);
    }

    @Transactional
    public void markWaitReview(String transferId) {
        TransferOrder order = find(transferId);
        order.markStatus(TransferStatus.WAIT_REVIEW);
        orderRepository.saveAndFlush(order);
    }

    @Transactional
    public void markDebitSuccess(String transferId) {
        TransferOrder order = find(transferId);
        order.markStatus(TransferStatus.DEBIT_SUCCESS);
        orderRepository.saveAndFlush(order);
    }

    @Transactional
    public void markFreezeFailed(String transferId, String code, String message) {
        TransferOrder order = find(transferId);
        order.markFailure(TransferStatus.FREEZE_FAILED, code, message);
        orderRepository.saveAndFlush(order);
    }

    @Transactional
    public void markCreditFailed(String transferId, String code, String message) {
        TransferOrder order = find(transferId);
        order.markFailure(TransferStatus.CREDIT_FAILED, code, message);
        orderRepository.saveAndFlush(order);
    }

    @Transactional
    public void markSuccess(String transferId) {
        TransferOrder order = find(transferId);
        order.markStatus(TransferStatus.SUCCESS);
        orderRepository.saveAndFlush(order);
    }

    @Transactional
    public void markRejected(String transferId) {
        TransferOrder order = find(transferId);
        order.markStatus(TransferStatus.REJECTED);
        orderRepository.saveAndFlush(order);
    }

    private TransferOrder find(String transferId) {
        return orderRepository.findByTransferId(transferId)
                .orElseThrow(() -> new IllegalArgumentException("transfer not found: " + transferId));
    }
}
