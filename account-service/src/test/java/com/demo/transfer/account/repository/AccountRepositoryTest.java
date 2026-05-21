package com.demo.transfer.account.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.demo.transfer.account.domain.AccountBalance;
import com.demo.transfer.account.domain.AssetOperation;
import com.demo.transfer.common.OperationType;
import java.math.BigDecimal;
import javax.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@ContextConfiguration(classes = AccountRepositoryTest.JpaConfig.class)
class AccountRepositoryTest {
    @Autowired
    private AccountBalanceRepository balanceRepository;

    @Autowired
    private AssetOperationRepository operationRepository;

    @Autowired
    private FinanceLedgerRepository ledgerRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void savesAndLoadsAccountBalance() {
        AccountBalance balance = AccountBalance.create("user-1", "USDT", new BigDecimal("100.00"));

        balanceRepository.saveAndFlush(balance);

        AccountBalance loaded = balanceRepository.findByUserIdAndAssetCode("user-1", "USDT").orElseThrow(AssertionError::new);
        assertThat(loaded.getAvailableAmount()).isEqualByComparingTo("100.00");
        assertThat(loaded.getFrozenAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void rejectsDuplicateTransferOperation() {
        AssetOperation first = AssetOperation.success("transfer-1", OperationType.FREEZE, "user-1", "USDT",
                new BigDecimal("10.00"), "success");
        AssetOperation duplicate = AssetOperation.success("transfer-1", OperationType.FREEZE, "user-1", "USDT",
                new BigDecimal("10.00"), "success");

        operationRepository.saveAndFlush(first);

        assertThatThrownBy(() -> {
            operationRepository.saveAndFlush(duplicate);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void loadsBalanceForUpdateInsideTransaction() {
        balanceRepository.saveAndFlush(AccountBalance.create("user-1", "USDT", new BigDecimal("100.00")));

        AccountBalance locked = balanceRepository.findByUserIdAndAssetCodeForUpdate("user-1", "USDT")
                .orElseThrow(AssertionError::new);

        assertThat(locked.getUserId()).isEqualTo("user-1");
        assertThat(ledgerRepository.countByTransferIdAndOperationType("missing", OperationType.FREEZE)).isZero();
    }

    @EnableJpaRepositories(basePackages = "com.demo.transfer.account.repository")
    @EntityScan(basePackages = "com.demo.transfer.account.domain")
    static class JpaConfig {
    }
}
