package com.demo.transfer.transfer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.demo.transfer.common.AccountType;
import com.demo.transfer.common.ApiResponse;
import com.demo.transfer.common.AssetOperationResponse;
import com.demo.transfer.common.OperationType;
import com.demo.transfer.common.TransferDirection;
import com.demo.transfer.common.TransferMode;
import com.demo.transfer.common.TransferStatus;
import com.demo.transfer.transfer.client.AccountAClient;
import com.demo.transfer.transfer.client.AccountBClient;
import com.demo.transfer.transfer.domain.TransferOrder;
import com.demo.transfer.transfer.repository.TransferOrderRepository;
import com.demo.transfer.transfer.repository.TransferStepLogRepository;
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
@ContextConfiguration(classes = TransferSagaServiceTest.SagaConfig.class)
class TransferSagaServiceTest {
    @Autowired
    private TransferSagaService sagaService;

    @Autowired
    private TransferOrderRepository orderRepository;

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
    void createsAToBManualTransferAndFreezesAccountA() {
        TransferOrder order = sagaService.createTransfer(request(TransferDirection.A_TO_B, TransferMode.MANUAL_REVIEW));

        assertThat(order.getStatus()).isEqualTo(TransferStatus.WAIT_REVIEW);
        assertThat(order.getSourceAccountType()).isEqualTo(AccountType.ACCOUNT_A);
        assertThat(order.getTargetAccountType()).isEqualTo(AccountType.ACCOUNT_B);
        verify(accountAClient).freeze(any());
    }

    @Test
    void createsBToAManualTransferAndFreezesAccountB() {
        TransferOrder order = sagaService.createTransfer(request(TransferDirection.B_TO_A, TransferMode.MANUAL_REVIEW));

        assertThat(order.getStatus()).isEqualTo(TransferStatus.WAIT_REVIEW);
        assertThat(order.getSourceAccountType()).isEqualTo(AccountType.ACCOUNT_B);
        verify(accountBClient).freeze(any());
    }

    @Test
    void approvalConfirmsSourceDebitCreditsTargetAndSucceeds() {
        TransferOrder order = sagaService.createTransfer(request(TransferDirection.A_TO_B, TransferMode.MANUAL_REVIEW));

        TransferOrder reviewed = sagaService.review(new ReviewTransferRequest(order.getTransferId(), true, "approved"));

        assertThat(reviewed.getStatus()).isEqualTo(TransferStatus.SUCCESS);
        verify(accountAClient).confirmDebit(any());
        verify(accountBClient).credit(any());
    }

    @Test
    void rejectionCancelsSourceFreezeAndRejects() {
        TransferOrder order = sagaService.createTransfer(request(TransferDirection.A_TO_B, TransferMode.MANUAL_REVIEW));

        TransferOrder reviewed = sagaService.review(new ReviewTransferRequest(order.getTransferId(), false, "rejected"));

        assertThat(reviewed.getStatus()).isEqualTo(TransferStatus.REJECTED);
        verify(accountAClient).cancelFreeze(any());
    }

    @Test
    void autoWithdrawCreatesFrozenOrderWaitingForWithdrawResult() {
        TransferOrder order = sagaService.createTransfer(request(TransferDirection.A_TO_B, TransferMode.AUTO_WITHDRAW));

        assertThat(order.getStatus()).isEqualTo(TransferStatus.WITHDRAW_PENDING);
        verify(accountAClient).freeze(any());
    }

    private CreateTransferRequest request(TransferDirection direction, TransferMode mode) {
        return new CreateTransferRequest("user-1", "USDT", new BigDecimal("10.00"), direction, mode);
    }

    private ApiResponse<AssetOperationResponse> ok(OperationType operationType) {
        return ApiResponse.ok(new AssetOperationResponse("transfer", operationType, true, "success"));
    }

    @EnableJpaRepositories(basePackages = "com.demo.transfer.transfer.repository")
    @EntityScan(basePackages = "com.demo.transfer.transfer.domain")
    @Import({TransferSagaService.class, AccountClientRouter.class})
    static class SagaConfig {
    }
}
