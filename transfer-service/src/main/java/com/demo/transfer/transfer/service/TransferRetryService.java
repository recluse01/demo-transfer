package com.demo.transfer.transfer.service;

import com.demo.transfer.common.TransferStatus;
import com.demo.transfer.transfer.domain.TransferOrder;
import com.demo.transfer.transfer.repository.TransferOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferRetryService {
    private final TransferOrderRepository orderRepository;
    private final TransferSagaService sagaService;

    public TransferRetryService(TransferOrderRepository orderRepository, TransferSagaService sagaService) {
        this.orderRepository = orderRepository;
        this.sagaService = sagaService;
    }

    @Transactional
    public TransferOrder retryOne(String transferId) {
        TransferOrder order = orderRepository.findByTransferId(transferId)
                .orElseThrow(() -> new IllegalArgumentException("transfer not found"));
        if (TransferStatus.DEBIT_FAILED == order.getStatus()) {
            return sagaService.approve(order);
        }
        if (TransferStatus.CREDIT_FAILED == order.getStatus()) {
            return sagaService.creditTarget(order);
        }
        if (TransferStatus.CANCEL_FAILED == order.getStatus()) {
            return sagaService.cancel(order);
        }
        return order;
    }
}
