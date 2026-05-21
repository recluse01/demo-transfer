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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@ContextConfiguration(classes = TransferSagaServiceTest.SagaConfig.class)
@ExtendWith(OutputCaptureExtension.class)
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
    void createsAToBManualTransferAndFreezesAccountA(CapturedOutput output) {
        TransferOrder order = sagaService.createTransfer(request(TransferDirection.A_TO_B, TransferMode.MANUAL_REVIEW));

        assertThat(order.getStatus()).isEqualTo(TransferStatus.WAIT_REVIEW);
        assertThat(order.getSourceAccountType()).isEqualTo(AccountType.ACCOUNT_A);
        assertThat(order.getTargetAccountType()).isEqualTo(AccountType.ACCOUNT_B);
        verify(accountAClient).freeze(any());
        assertThat(output).contains("开始创建转账")
                .contains("转账冻结成功")
                .contains("transferId=" + order.getTransferId())
                .contains("userId=user-1")
                .contains("direction=A_TO_B")
                .contains("amount=10.00");
    }

    @Test
    void createsBToAManualTransferAndFreezesAccountB() {
        TransferOrder order = sagaService.createTransfer(request(TransferDirection.B_TO_A, TransferMode.MANUAL_REVIEW));

        assertThat(order.getStatus()).isEqualTo(TransferStatus.WAIT_REVIEW);
        assertThat(order.getSourceAccountType()).isEqualTo(AccountType.ACCOUNT_B);
        verify(accountBClient).freeze(any());
    }

    @Test
    void approvalConfirmsSourceDebitCreditsTargetAndSucceeds(CapturedOutput output) {
        TransferOrder order = sagaService.createTransfer(request(TransferDirection.A_TO_B, TransferMode.MANUAL_REVIEW));

        TransferOrder reviewed = sagaService.review(new ReviewTransferRequest(order.getTransferId(), true, "approved"));

        assertThat(reviewed.getStatus()).isEqualTo(TransferStatus.SUCCESS);
        verify(accountAClient).confirmDebit(any());
        verify(accountBClient).credit(any());
        assertThat(output).contains("开始审核转账")
                .contains("审核通过，开始确认扣减")
                .contains("源账户扣减确认成功")
                .contains("目标账户入账成功，转账完成")
                .contains("transferId=" + order.getTransferId());
    }

    @Test
    void logsFreezeFailureWithErrorMessage(CapturedOutput output) {
        when(accountAClient.freeze(any())).thenReturn(ApiResponse.fail("BALANCE_NOT_ENOUGH", "余额不足"));

        TransferOrder order = sagaService.createTransfer(request(TransferDirection.A_TO_B, TransferMode.MANUAL_REVIEW));

        assertThat(order.getStatus()).isEqualTo(TransferStatus.FREEZE_FAILED);
        assertThat(output).contains("转账冻结失败")
                .contains("transferId=" + order.getTransferId())
                .contains("errorCode=BALANCE_NOT_ENOUGH")
                .contains("errorMessage=余额不足");
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
