package com.demo.transfer.transfer.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.demo.transfer.common.AccountType;
import com.demo.transfer.common.TransferMode;
import com.demo.transfer.common.TransferStatus;
import com.demo.transfer.transfer.domain.TransferOrder;
import com.demo.transfer.transfer.domain.TransferStepLog;
import java.math.BigDecimal;
import javax.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@ContextConfiguration(classes = TransferRepositoryTest.JpaConfig.class)
class TransferRepositoryTest {
    @Autowired
    private TransferOrderRepository orderRepository;

    @Autowired
    private TransferStepLogRepository stepLogRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void savesAndFindsTransferByTransferId() {
        orderRepository.saveAndFlush(order("transfer-1"));

        TransferOrder loaded = orderRepository.findByTransferId("transfer-1").orElseThrow(AssertionError::new);

        assertThat(loaded.getStatus()).isEqualTo(TransferStatus.CREATED);
        assertThat(loaded.getAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    void rejectsDuplicateTransferId() {
        orderRepository.saveAndFlush(order("transfer-1"));

        assertThatThrownBy(() -> {
            orderRepository.saveAndFlush(order("transfer-1"));
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void appendsStepLogsForTransfer() {
        stepLogRepository.save(TransferStepLog.of("transfer-1", "FREEZE", "SUCCESS", "{}", "{}", null));
        stepLogRepository.save(TransferStepLog.of("transfer-1", "CREDIT", "FAILED", "{}", "{}", "timeout"));

        assertThat(stepLogRepository.findByTransferIdOrderByCreatedAtAsc("transfer-1")).hasSize(2);
    }

    private TransferOrder order(String transferId) {
        return TransferOrder.create(transferId, "user-1", AccountType.ACCOUNT_A, AccountType.ACCOUNT_B, "USDT",
                new BigDecimal("10.00"), TransferMode.MANUAL_REVIEW);
    }

    @EnableJpaRepositories(basePackages = "com.demo.transfer.transfer.repository")
    @EntityScan(basePackages = "com.demo.transfer.transfer.domain")
    static class JpaConfig {
    }
}
