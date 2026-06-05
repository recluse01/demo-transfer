package com.demo.transfer.transfer.service;

import com.demo.transfer.common.TransferStatus;
import com.demo.transfer.transfer.domain.TransferOrder;
import com.demo.transfer.transfer.repository.TransferOrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 转账主单状态服务。
 *
 * <p>每个方法在独立事务中执行，供 TransferActivitiesImpl 在 Feign 调用完成后单独调用，
 * 确保数据库连接不在远程调用期间被持有。
 */
@Slf4j
@Service
public class TransferOrderStateService {
    private final TransferOrderRepository orderRepository;

    public TransferOrderStateService(TransferOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public TransferOrder loadOrder(String transferId) {
        log.debug("正在加载转账订单，transferId={}", transferId);
        return orderRepository.findByTransferId(transferId)
                .orElseThrow(() -> new IllegalArgumentException("transfer not found: " + transferId));
    }

    @Transactional
    public TransferOrder createOrder(TransferOrder order) {
        TransferOrder saved = orderRepository.saveAndFlush(order);
        log.info("转账订单已创建，transferId={}, userId={}, sourceAccountType={}, targetAccountType={}, amount={}, mode={}",
                saved.getTransferId(), saved.getUserId(), saved.getSourceAccountType(), saved.getTargetAccountType(),
                saved.getAmount(), saved.getTransferMode());
        return saved;
    }

    @Transactional
    public void markWaitReview(String transferId) {
        TransferOrder order = find(transferId);
        order.markStatus(TransferStatus.WAIT_REVIEW);
        orderRepository.saveAndFlush(order);
        log.info("转账订单已标记为待审核，transferId={}", transferId);
    }

    @Transactional
    public void markDebitSuccess(String transferId) {
        TransferOrder order = find(transferId);
        order.markStatus(TransferStatus.DEBIT_SUCCESS);
        orderRepository.saveAndFlush(order);
        log.info("转账订单已标记为扣减成功，transferId={}", transferId);
    }

    @Transactional
    public void markFreezeFailed(String transferId, String code, String message) {
        TransferOrder order = find(transferId);
        order.markFailure(TransferStatus.FREEZE_FAILED, code, message);
        orderRepository.saveAndFlush(order);
        log.warn("转账订单已标记为冻结失败，transferId={}, code={}, message={}",
                transferId, code, message);
    }

    @Transactional
    public void markInitFailed(String transferId, String message) {
        TransferOrder order = find(transferId);
        order.markFailure(TransferStatus.INIT_FAILED, "WORKFLOW_START_FAILED", message);
        orderRepository.saveAndFlush(order);
        log.warn("转账订单已标记为初始化失败，transferId={}, message={}", transferId, message);
    }

    @Transactional
    public void markCreditFailed(String transferId, String code, String message) {
        TransferOrder order = find(transferId);
        order.markFailure(TransferStatus.CREDIT_FAILED, code, message);
        orderRepository.saveAndFlush(order);
        log.warn("转账订单已标记为入账失败，transferId={}, code={}, message={}",
                transferId, code, message);
    }

    @Transactional
    public void markSuccess(String transferId) {
        TransferOrder order = find(transferId);
        order.markStatus(TransferStatus.SUCCESS);
        orderRepository.saveAndFlush(order);
        log.info("转账订单已标记为成功，transferId={}", transferId);
    }

    @Transactional
    public void markRejected(String transferId) {
        TransferOrder order = find(transferId);
        order.markStatus(TransferStatus.REJECTED);
        orderRepository.saveAndFlush(order);
        log.info("转账订单已标记为已拒绝，transferId={}", transferId);
    }

    private TransferOrder find(String transferId) {
        return orderRepository.findByTransferId(transferId)
                .orElseThrow(() -> new IllegalArgumentException("transfer not found: " + transferId));
    }
}
