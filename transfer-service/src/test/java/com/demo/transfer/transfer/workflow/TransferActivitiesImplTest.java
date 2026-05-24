package com.demo.transfer.transfer.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.demo.transfer.common.AccountType;
import com.demo.transfer.common.ApiResponse;
import com.demo.transfer.common.AssetOperationResponse;
import com.demo.transfer.common.OperationType;
import com.demo.transfer.common.TransferMode;
import com.demo.transfer.common.TransferStatus;
import com.demo.transfer.transfer.client.AccountAClient;
import com.demo.transfer.transfer.client.AccountBClient;
import com.demo.transfer.transfer.domain.TransferOrder;
import com.demo.transfer.transfer.repository.TransferOrderRepository;
import com.demo.transfer.transfer.service.AccountClientRouter;
import com.demo.transfer.transfer.service.TransferOrderStateService;
import io.temporal.failure.ApplicationFailure;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@ContextConfiguration(classes = TransferActivitiesImplTest.Config.class)
class TransferActivitiesImplTest {
    private static final String TRANSFER_ID = "txn-001";

    @Autowired
    private TransferActivitiesImpl activities;

    @Autowired
    private TransferOrderRepository orderRepository;

    @MockBean
    private AccountAClient accountAClient;

    @MockBean
    private AccountBClient accountBClient;

    @BeforeEach
    void setUp() {
        when(accountAClient.freeze(any())).thenReturn(ok(OperationType.FREEZE));
        when(accountAClient.confirmDebit(any())).thenReturn(ok(OperationType.CONFIRM_DEBIT));
        when(accountAClient.cancelFreeze(any())).thenReturn(ok(OperationType.CANCEL_FREEZE));
        when(accountAClient.credit(any())).thenReturn(ok(OperationType.CREDIT));
        when(accountBClient.freeze(any())).thenReturn(ok(OperationType.FREEZE));
        when(accountBClient.confirmDebit(any())).thenReturn(ok(OperationType.CONFIRM_DEBIT));
        when(accountBClient.cancelFreeze(any())).thenReturn(ok(OperationType.CANCEL_FREEZE));
        when(accountBClient.credit(any())).thenReturn(ok(OperationType.CREDIT));
    }

    @Test
    void freezeManualReviewSetsWaitReview() {
        save(order(TransferMode.MANUAL_REVIEW));

        activities.freeze(TRANSFER_ID);

        assertThat(reload().getStatus()).isEqualTo(TransferStatus.WAIT_REVIEW);
    }

    @Test
    void freezeAutoWithdrawLeavesStatusUnchanged() {
        save(order(TransferMode.AUTO_WITHDRAW));

        activities.freeze(TRANSFER_ID);

        assertThat(reload().getStatus()).isEqualTo(TransferStatus.CREATED);
    }

    @Test
    void freezeBusinessFailureSetsFreezeFailedAndThrowsNonRetryableFailure() {
        when(accountAClient.freeze(any())).thenReturn(ApiResponse.fail("ACCOUNT_OPERATION_FAILED", "余额不足"));
        save(order(TransferMode.MANUAL_REVIEW));

        assertThatThrownBy(() -> activities.freeze(TRANSFER_ID))
                .isInstanceOfSatisfying(ApplicationFailure.class, failure -> {
                    assertThat(failure.isNonRetryable()).isTrue();
                    assertThat(failure.getType()).isEqualTo("ACCOUNT_OPERATION_FAILED");
                    assertThat(failure).hasMessageContaining("freeze failed: 余额不足");
                });
        TransferOrder reloaded = reload();
        assertThat(reloaded.getStatus()).isEqualTo(TransferStatus.FREEZE_FAILED);
        assertThat(reloaded.getLastErrorCode()).isEqualTo("ACCOUNT_OPERATION_FAILED");
        assertThat(reloaded.getLastErrorMessage()).isEqualTo("余额不足");
    }

    @Test
    void confirmDebitSetsDebitSuccess() {
        save(order(TransferMode.AUTO_WITHDRAW));

        activities.confirmDebit(TRANSFER_ID);

        assertThat(reload().getStatus()).isEqualTo(TransferStatus.DEBIT_SUCCESS);
    }

    @Test
    void confirmDebitFailureThrows() {
        when(accountAClient.confirmDebit(any())).thenReturn(ApiResponse.fail("DEBIT_TIMEOUT", "超时"));
        save(order(TransferMode.AUTO_WITHDRAW));

        assertThatThrownBy(() -> activities.confirmDebit(TRANSFER_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("confirmDebit failed");
    }

    @Test
    void creditSetsSuccess() {
        save(order(TransferMode.AUTO_WITHDRAW));

        activities.credit(TRANSFER_ID);

        assertThat(reload().getStatus()).isEqualTo(TransferStatus.SUCCESS);
    }

    @Test
    void creditFailureSetsCreditFailedAndThrows() {
        when(accountBClient.credit(any())).thenReturn(ApiResponse.fail("CREDIT_TIMEOUT", "入账超时"));
        save(order(TransferMode.AUTO_WITHDRAW));

        assertThatThrownBy(() -> activities.credit(TRANSFER_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("credit failed");
        TransferOrder reloaded = reload();
        assertThat(reloaded.getStatus()).isEqualTo(TransferStatus.CREDIT_FAILED);
        assertThat(reloaded.getLastErrorCode()).isEqualTo("CREDIT_TIMEOUT");
    }

    @Test
    void cancelFreezeSetsRejected() {
        save(order(TransferMode.MANUAL_REVIEW));

        activities.cancelFreeze(TRANSFER_ID);

        assertThat(reload().getStatus()).isEqualTo(TransferStatus.REJECTED);
    }

    @Test
    void cancelFreezeFailureThrows() {
        when(accountAClient.cancelFreeze(any())).thenReturn(ApiResponse.fail("CANCEL_TIMEOUT", "超时"));
        save(order(TransferMode.MANUAL_REVIEW));

        assertThatThrownBy(() -> activities.cancelFreeze(TRANSFER_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("cancelFreeze failed");
    }

    private TransferOrder order(TransferMode mode) {
        return TransferOrder.create(TRANSFER_ID, "user-1", AccountType.ACCOUNT_A, AccountType.ACCOUNT_B,
                "USDT", new BigDecimal("10.00"), mode);
    }

    private void save(TransferOrder order) {
        orderRepository.saveAndFlush(order);
    }

    private TransferOrder reload() {
        return orderRepository.findByTransferId(TRANSFER_ID).orElseThrow();
    }

    private ApiResponse<AssetOperationResponse> ok(OperationType type) {
        return ApiResponse.ok(new AssetOperationResponse(TRANSFER_ID, type, true, "success"));
    }

    @EnableJpaRepositories(basePackages = "com.demo.transfer.transfer.repository")
    @EntityScan(basePackages = "com.demo.transfer.transfer.domain")
    @Import({TransferActivitiesImpl.class, TransferOrderStateService.class, AccountClientRouter.class})
    static class Config {
    }
}
