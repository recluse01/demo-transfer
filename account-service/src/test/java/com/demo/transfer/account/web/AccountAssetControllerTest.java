package com.demo.transfer.account.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.demo.transfer.account.service.AccountAssetService;
import com.demo.transfer.common.ApiResponse;
import com.demo.transfer.common.AssetOperationRequest;
import com.demo.transfer.common.AssetOperationResponse;
import com.demo.transfer.common.OperationType;
import com.demo.transfer.common.TransferDirection;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccountAssetControllerTest {
    private AccountAssetService service;
    private AccountAssetController controller;

    @BeforeEach
    void setUp() {
        service = mock(AccountAssetService.class);
        controller = new AccountAssetController(service);
    }

    @Test
    void freezeReturnsFailureAndDoesNotCallServiceForTodoAmount() {
        ApiResponse<AssetOperationResponse> response = controller.freeze(request("100.00"));

        assertFailure(response, "---测试冻结资产失败");
        verifyNoInteractions(service);
    }

    @Test
    void confirmDebitReturnsFailureAndDoesNotCallServiceForTodoAmount() {
        ApiResponse<AssetOperationResponse> response = controller.confirmDebit(request("50.00"));

        assertFailure(response, "---测试确认扣减资产失败");
        verifyNoInteractions(service);
    }

    @Test
    void cancelFreezeReturnsFailureAndDoesNotCallServiceForTodoAmount() {
        ApiResponse<AssetOperationResponse> response = controller.cancelFreeze(request("80.00"));

        assertFailure(response, "++++测试解冻资产失败");
        verifyNoInteractions(service);
    }

    @Test
    void creditReturnsFailureAndDoesNotCallServiceForTodoAmount() {
        ApiResponse<AssetOperationResponse> response = controller.credit(request("30.00"));

        assertFailure(response, "++++测试确认入账失败");
        verifyNoInteractions(service);
    }

    @Test
    void freezeWrapsServiceIllegalStateExceptionAsAccountOperationFailure() {
        AssetOperationRequest request = request("10.00");
        when(service.freeze(request)).thenThrow(new IllegalStateException("insufficient available balance"));

        ApiResponse<AssetOperationResponse> response = controller.freeze(request);

        assertFailure(response, "insufficient available balance");
        verify(service).freeze(request);
    }

    @Test
    void creditReturnsSuccessWhenServiceSucceeds() {
        AssetOperationRequest request = request("10.00");
        AssetOperationResponse serviceResponse = new AssetOperationResponse(
                request.getTransferId(), OperationType.CREDIT, true, "success");
        when(service.credit(request)).thenReturn(serviceResponse);

        ApiResponse<AssetOperationResponse> response = controller.credit(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("OK");
        assertThat(response.getData()).isSameAs(serviceResponse);
        verify(service).credit(request);
    }

    private void assertFailure(ApiResponse<AssetOperationResponse> response, String messagePart) {
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo("ACCOUNT_OPERATION_FAILED");
        assertThat(response.getMessage()).contains(messagePart);
        assertThat(response.getData()).isNull();
    }

    private AssetOperationRequest request(String amount) {
        return new AssetOperationRequest("transfer-1", "user-1", "USDT", new BigDecimal(amount),
                TransferDirection.A_TO_B);
    }
}
