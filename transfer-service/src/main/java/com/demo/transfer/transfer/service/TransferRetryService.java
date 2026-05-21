package com.demo.transfer.transfer.service;

import com.demo.transfer.common.TransferStatus;
import com.demo.transfer.transfer.domain.TransferOrder;
import com.demo.transfer.transfer.repository.TransferOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 转账失败步骤重试服务。
 *
 * <p>根据主单当前状态把流程重新路由到对应的 Saga 步骤，
 * 不额外引入新的状态机分支。
 */
@Service
public class TransferRetryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransferRetryService.class);

    /** 转账主单仓储。 */
    private final TransferOrderRepository orderRepository;
    /** 复用 Saga 编排服务中的实际步骤实现。 */
    private final TransferSagaService sagaService;

    public TransferRetryService(TransferOrderRepository orderRepository, TransferSagaService sagaService) {
        this.orderRepository = orderRepository;
        this.sagaService = sagaService;
    }

    @Transactional
    public TransferOrder retryOne(String transferId) {
        TransferOrder order = orderRepository.findByTransferId(transferId)
                .orElseThrow(() -> new IllegalArgumentException("transfer not found"));
        LOGGER.info("开始重试转账失败步骤，transferId={}, userId={}, currentStatus={}",
                order.getTransferId(), order.getUserId(), order.getStatus());
        // 仅对明确失败的中间状态执行重试，避免对终态或未开始状态做无意义调用。
        if (TransferStatus.DEBIT_FAILED == order.getStatus()) {
            LOGGER.info("重试源账户确认扣减，transferId={}, userId={}, sourceAccount={}",
                    order.getTransferId(), order.getUserId(), order.getSourceAccountType());
            return sagaService.approve(order);
        }
        if (TransferStatus.CREDIT_FAILED == order.getStatus()) {
            LOGGER.info("重试目标账户入账，transferId={}, userId={}, targetAccount={}",
                    order.getTransferId(), order.getUserId(), order.getTargetAccountType());
            return sagaService.creditTarget(order);
        }
        if (TransferStatus.CANCEL_FAILED == order.getStatus()) {
            LOGGER.info("重试源账户取消冻结，transferId={}, userId={}, sourceAccount={}",
                    order.getTransferId(), order.getUserId(), order.getSourceAccountType());
            return sagaService.cancel(order);
        }
        LOGGER.info("当前状态无需重试，transferId={}, userId={}, currentStatus={}",
                order.getTransferId(), order.getUserId(), order.getStatus());
        return order;
    }
}
