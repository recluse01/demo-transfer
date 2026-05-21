package com.demo.transfer.transfer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
@ContextConfiguration(classes = TransferRetryServiceTest.RetryConfig.class)
class TransferRetryServiceTest {
    @Autowired
    private TransferRetryService retryService;

    @Autowired
    private TransferOrderRepository orderRepository;

    @MockBean
    private AccountAClient accountAClient;

    @MockBean
    private AccountBClient accountBClient;

    @BeforeEach
    void setUp() {
        when(accountAClient.confirmDebit(any())).thenReturn(ok(OperationType.CONFIRM_DEBIT));
        when(accountAClient.cancelFreeze(any())).thenReturn(ok(OperationType.CANCEL_FREEZE));
        when(accountBClient.credit(any())).thenReturn(ok(OperationType.CREDIT));
    }

    @Test
    void debitFailedRetriesSourceDebitThenTargetCredit() {
        TransferOrder order = saveOrder("transfer-1", TransferStatus.DEBIT_FAILED);

        TransferOrder retried = retryService.retryOne(order.getTransferId());

        assertThat(retried.getStatus()).isEqualTo(TransferStatus.SUCCESS);
        verify(accountAClient).confirmDebit(any());
        verify(accountBClient).credit(any());
    }

    @Test
    void creditFailedRetriesOnlyTargetCredit() {
        TransferOrder order = saveOrder("transfer-2", TransferStatus.CREDIT_FAILED);

        TransferOrder retried = retryService.retryOne(order.getTransferId());

        assertThat(retried.getStatus()).isEqualTo(TransferStatus.SUCCESS);
        verify(accountAClient, never()).confirmDebit(any());
        verify(accountBClient).credit(any());
    }

    @Test
    void cancelFailedRetriesOnlySourceCancelFreeze() {
        TransferOrder order = saveOrder("transfer-3", TransferStatus.CANCEL_FAILED);

        TransferOrder retried = retryService.retryOne(order.getTransferId());

        assertThat(retried.getStatus()).isEqualTo(TransferStatus.REJECTED);
        verify(accountAClient).cancelFreeze(any());
        verify(accountBClient, never()).credit(any());
    }

    @Test
    void freezeFailedDoesNotRetryAutomatically() {
        TransferOrder order = saveOrder("transfer-4", TransferStatus.FREEZE_FAILED);

        TransferOrder retried = retryService.retryOne(order.getTransferId());

        assertThat(retried.getStatus()).isEqualTo(TransferStatus.FREEZE_FAILED);
        verify(accountAClient, never()).confirmDebit(any());
        verify(accountBClient, never()).credit(any());
    }

    private TransferOrder saveOrder(String transferId, TransferStatus status) {
        TransferOrder order = TransferOrder.create(transferId, "user-1", AccountType.ACCOUNT_A, AccountType.ACCOUNT_B,
                "USDT", new BigDecimal("10.00"), TransferMode.MANUAL_REVIEW);
        order.markStatus(status);
        return orderRepository.saveAndFlush(order);
    }

    private ApiResponse<AssetOperationResponse> ok(OperationType operationType) {
        return ApiResponse.ok(new AssetOperationResponse("transfer", operationType, true, "success"));
    }

    @EnableJpaRepositories(basePackages = "com.demo.transfer.transfer.repository")
    @EntityScan(basePackages = "com.demo.transfer.transfer.domain")
    @Import({TransferRetryService.class, TransferSagaService.class, AccountClientRouter.class})
    static class RetryConfig {
    }
}
