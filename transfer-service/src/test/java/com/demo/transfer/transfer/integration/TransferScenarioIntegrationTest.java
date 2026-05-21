package com.demo.transfer.transfer.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.demo.transfer.common.ApiResponse;
import com.demo.transfer.common.AssetOperationResponse;
import com.demo.transfer.common.OperationType;
import com.demo.transfer.common.TransferDirection;
import com.demo.transfer.common.TransferMode;
import com.demo.transfer.common.TransferStatus;
import com.demo.transfer.transfer.client.AccountAClient;
import com.demo.transfer.transfer.client.AccountBClient;
import com.demo.transfer.transfer.domain.TransferOrder;
import com.demo.transfer.transfer.service.AccountClientRouter;
import com.demo.transfer.transfer.service.TransferRetryService;
import com.demo.transfer.transfer.service.TransferSagaService;
import com.demo.transfer.transfer.web.CreateTransferRequest;
import com.demo.transfer.transfer.web.ReviewTransferRequest;
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
@ContextConfiguration(classes = TransferScenarioIntegrationTest.ScenarioConfig.class)
class TransferScenarioIntegrationTest {
    @Autowired
    private TransferSagaService sagaService;

    @Autowired
    private TransferRetryService retryService;

    @MockBean
    private AccountAClient accountAClient;

    @MockBean
    private AccountBClient accountBClient;

    @BeforeEach
    void setUp() {
        when(accountAClient.freeze(any())).thenReturn(ok(OperationType.FREEZE));
        when(accountBClient.freeze(any())).thenReturn(ok(OperationType.FREEZE));
        when(accountAClient.confirmDebit(any())).thenReturn(ok(OperationType.CONFIRM_DEBIT));
        when(accountBClient.confirmDebit(any())).thenReturn(ok(OperationType.CONFIRM_DEBIT));
        when(accountAClient.cancelFreeze(any())).thenReturn(ok(OperationType.CANCEL_FREEZE));
        when(accountBClient.cancelFreeze(any())).thenReturn(ok(OperationType.CANCEL_FREEZE));
        when(accountAClient.credit(any())).thenReturn(ok(OperationType.CREDIT));
        when(accountBClient.credit(any())).thenReturn(ok(OperationType.CREDIT));
    }

    @Test
    void scenarioOneApprovalCompletesAToBManualTransfer() {
        TransferOrder order = sagaService.createTransfer(request(TransferDirection.A_TO_B, TransferMode.MANUAL_REVIEW));

        TransferOrder result = sagaService.review(new ReviewTransferRequest(order.getTransferId(), true, "approved"));

        assertThat(result.getStatus()).isEqualTo(TransferStatus.SUCCESS);
        verify(accountAClient).freeze(any());
        verify(accountAClient).confirmDebit(any());
        verify(accountBClient).credit(any());
    }

    @Test
    void scenarioOneRejectionCancelsAToBManualTransfer() {
        TransferOrder order = sagaService.createTransfer(request(TransferDirection.A_TO_B, TransferMode.MANUAL_REVIEW));

        TransferOrder result = sagaService.review(new ReviewTransferRequest(order.getTransferId(), false, "rejected"));

        assertThat(result.getStatus()).isEqualTo(TransferStatus.REJECTED);
        verify(accountAClient).cancelFreeze(any());
        verify(accountBClient, never()).credit(any());
    }

    @Test
    void scenarioTwoApprovalCompletesBToAManualTransfer() {
        TransferOrder order = sagaService.createTransfer(request(TransferDirection.B_TO_A, TransferMode.MANUAL_REVIEW));

        TransferOrder result = sagaService.review(new ReviewTransferRequest(order.getTransferId(), true, "approved"));

        assertThat(result.getStatus()).isEqualTo(TransferStatus.SUCCESS);
        verify(accountBClient).freeze(any());
        verify(accountBClient).confirmDebit(any());
        verify(accountAClient).credit(any());
    }

    @Test
    void scenarioThreeAutoWithdrawSuccessCompletesTransfer() {
        TransferOrder order = sagaService.createTransfer(request(TransferDirection.A_TO_B, TransferMode.AUTO_WITHDRAW));

        TransferOrder result = sagaService.handleWithdrawResult(order.getTransferId(), true, "withdraw success");

        assertThat(result.getStatus()).isEqualTo(TransferStatus.SUCCESS);
        verify(accountAClient).confirmDebit(any());
        verify(accountBClient).credit(any());
    }

    @Test
    void scenarioThreeAutoWithdrawFailureCancelsFreeze() {
        TransferOrder order = sagaService.createTransfer(request(TransferDirection.A_TO_B, TransferMode.AUTO_WITHDRAW));

        TransferOrder result = sagaService.handleWithdrawResult(order.getTransferId(), false, "withdraw failed");

        assertThat(result.getStatus()).isEqualTo(TransferStatus.REJECTED);
        verify(accountAClient).cancelFreeze(any());
        verify(accountBClient, never()).credit(any());
    }

    @Test
    void targetCreditFailureLeavesCreditFailedAndRetryOnlyCreditsTarget() {
        when(accountBClient.credit(any()))
                .thenReturn(ApiResponse.fail("TIMEOUT", "credit timeout"))
                .thenReturn(ok(OperationType.CREDIT));
        TransferOrder order = sagaService.createTransfer(request(TransferDirection.A_TO_B, TransferMode.MANUAL_REVIEW));

        TransferOrder failed = sagaService.review(new ReviewTransferRequest(order.getTransferId(), true, "approved"));
        TransferStatus failedStatus = failed.getStatus();
        TransferOrder retried = retryService.retryOne(order.getTransferId());

        assertThat(failedStatus).isEqualTo(TransferStatus.CREDIT_FAILED);
        assertThat(retried.getStatus()).isEqualTo(TransferStatus.SUCCESS);
        verify(accountAClient).confirmDebit(any());
        verify(accountBClient, org.mockito.Mockito.times(2)).credit(any());
    }

    private CreateTransferRequest request(TransferDirection direction, TransferMode mode) {
        return new CreateTransferRequest("user-1", "USDT", new BigDecimal("10.00"), direction, mode);
    }

    private ApiResponse<AssetOperationResponse> ok(OperationType operationType) {
        return ApiResponse.ok(new AssetOperationResponse("transfer", operationType, true, "success"));
    }

    @EnableJpaRepositories(basePackages = "com.demo.transfer.transfer.repository")
    @EntityScan(basePackages = "com.demo.transfer.transfer.domain")
    @Import({TransferSagaService.class, TransferRetryService.class, AccountClientRouter.class})
    static class ScenarioConfig {
    }
}
