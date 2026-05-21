package com.demo.transfer.transfer.schedule;

import com.demo.transfer.common.TransferStatus;
import com.demo.transfer.transfer.domain.TransferOrder;
import com.demo.transfer.transfer.repository.TransferOrderRepository;
import com.demo.transfer.transfer.service.TransferRetryService;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TransferRetryScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransferRetryScheduler.class);

    private final TransferOrderRepository orderRepository;
    private final TransferRetryService retryService;

    public TransferRetryScheduler(TransferOrderRepository orderRepository, TransferRetryService retryService) {
        this.orderRepository = orderRepository;
        this.retryService = retryService;
    }

    @Scheduled(fixedDelay = 30000)
    public void retryFailedSteps() {
        List<TransferOrder> orders = orderRepository.findTop100ByStatusInOrderByUpdatedAtAsc(Arrays.asList(
                TransferStatus.DEBIT_FAILED, TransferStatus.CREDIT_FAILED, TransferStatus.CANCEL_FAILED));
        for (TransferOrder order : orders) {
            try {
                retryService.retryOne(order.getTransferId());
            } catch (RuntimeException ex) {
                LOGGER.warn("retry transfer failed, transferId={}", order.getTransferId(), ex);
            }
        }
    }
}
