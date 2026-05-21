package com.demo.transfer.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.demo.transfer.account.domain.AccountBalance;
import com.demo.transfer.account.repository.AccountBalanceRepository;
import com.demo.transfer.account.repository.FinanceLedgerRepository;
import com.demo.transfer.common.AssetOperationRequest;
import com.demo.transfer.common.AssetOperationResponse;
import com.demo.transfer.common.OperationType;
import com.demo.transfer.common.TransferDirection;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@ContextConfiguration(classes = AccountAssetServiceTest.AccountServiceConfig.class)
@Transactional
class AccountAssetServiceTest {
    @Autowired
    private AccountAssetService service;

    @Autowired
    private AccountBalanceRepository balanceRepository;

    @Autowired
    private FinanceLedgerRepository ledgerRepository;

    @BeforeEach
    void setUp() {
        balanceRepository.save(AccountBalance.create("user-1", "USDT", new BigDecimal("100.00")));
    }

    @Test
    void freezeMovesAvailableAmountToFrozenAmount() {
        AssetOperationResponse response = service.freeze(request("transfer-1", "10.00"));

        AccountBalance balance = loadBalance();
        assertThat(response.isApplied()).isTrue();
        assertThat(balance.getAvailableAmount()).isEqualByComparingTo("90.00");
        assertThat(balance.getFrozenAmount()).isEqualByComparingTo("10.00");
        assertThat(ledgerRepository.countByTransferIdAndOperationType("transfer-1", OperationType.FREEZE)).isEqualTo(1);
    }

    @Test
    void freezeRejectsInsufficientAvailableAmount() {
        assertThatThrownBy(() -> service.freeze(request("transfer-2", "101.00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("insufficient available balance");
    }

    @Test
    void duplicateFreezeDoesNotApplyBalanceOrLedgerAgain() {
        service.freeze(request("transfer-3", "10.00"));

        AssetOperationResponse duplicate = service.freeze(request("transfer-3", "10.00"));

        AccountBalance balance = loadBalance();
        assertThat(duplicate.isApplied()).isFalse();
        assertThat(balance.getAvailableAmount()).isEqualByComparingTo("90.00");
        assertThat(balance.getFrozenAmount()).isEqualByComparingTo("10.00");
        assertThat(ledgerRepository.countByTransferIdAndOperationType("transfer-3", OperationType.FREEZE)).isEqualTo(1);
    }

    @Test
    void confirmDebitDecreasesFrozenAmount() {
        service.freeze(request("transfer-4", "10.00"));

        service.confirmDebit(request("transfer-4", "10.00"));

        AccountBalance balance = loadBalance();
        assertThat(balance.getAvailableAmount()).isEqualByComparingTo("90.00");
        assertThat(balance.getFrozenAmount()).isEqualByComparingTo("0.00");
        assertThat(ledgerRepository.countByTransferIdAndOperationType("transfer-4", OperationType.CONFIRM_DEBIT)).isEqualTo(1);
    }

    @Test
    void cancelFreezeReturnsFrozenAmountToAvailableAmount() {
        service.freeze(request("transfer-5", "10.00"));

        service.cancelFreeze(request("transfer-5", "10.00"));

        AccountBalance balance = loadBalance();
        assertThat(balance.getAvailableAmount()).isEqualByComparingTo("100.00");
        assertThat(balance.getFrozenAmount()).isEqualByComparingTo("0.00");
        assertThat(ledgerRepository.countByTransferIdAndOperationType("transfer-5", OperationType.CANCEL_FREEZE)).isEqualTo(1);
    }

    @Test
    void creditIncreasesAvailableAmount() {
        service.credit(request("transfer-6", "10.00"));

        AccountBalance balance = loadBalance();
        assertThat(balance.getAvailableAmount()).isEqualByComparingTo("110.00");
        assertThat(balance.getFrozenAmount()).isEqualByComparingTo("0.00");
        assertThat(ledgerRepository.countByTransferIdAndOperationType("transfer-6", OperationType.CREDIT)).isEqualTo(1);
    }

    private AccountBalance loadBalance() {
        return balanceRepository.findByUserIdAndAssetCode("user-1", "USDT").orElseThrow(AssertionError::new);
    }

    private AssetOperationRequest request(String transferId, String amount) {
        return new AssetOperationRequest(transferId, "user-1", "USDT", new BigDecimal(amount), TransferDirection.A_TO_B);
    }

    @EnableJpaRepositories(basePackages = "com.demo.transfer.account.repository")
    @EntityScan(basePackages = "com.demo.transfer.account.domain")
    @Import(AccountAssetService.class)
    static class AccountServiceConfig {
    }
}
