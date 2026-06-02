package com.demo.transfer.account.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.demo.transfer.account.service.AccountAssetService;
import com.demo.transfer.common.AssetOperationRequest;
import com.demo.transfer.common.AssetOperationResponse;
import com.demo.transfer.common.OperationType;
import com.demo.transfer.common.TransferDirection;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link AccountAssetController} 的 Web 切片测试。
 *
 * <p>验证四个端点的成功路径、统一 {@code ApiResponse} 结构、入参校验失败（400）与业务失败（200+success=false）。
 * 业务逻辑由 {@link AccountAssetService} 承担，此处以 {@code @MockBean} 隔离。
 */
@WebMvcTest(AccountAssetController.class)
class AccountAssetControllerTest {

    private static final String BASE = "/internal/accounts/assets";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AccountAssetService service;

    private String validBody() throws Exception {
        return objectMapper.writeValueAsString(new AssetOperationRequest(
                "transfer-1", "user-1", "USDT", new BigDecimal("10.00000000"), TransferDirection.A_TO_B));
    }

    @Test
    void freezeReturnsOkWithUnifiedResponse() throws Exception {
        given(service.freeze(any())).willReturn(
                new AssetOperationResponse("transfer-1", OperationType.FREEZE, true, "FREEZE success"));

        mockMvc.perform(post(BASE + "/freeze").contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.applied").value(true))
                .andExpect(jsonPath("$.data.operationType").value("FREEZE"));
    }

    @Test
    void confirmDebitReturnsOk() throws Exception {
        given(service.confirmDebit(any())).willReturn(
                new AssetOperationResponse("transfer-1", OperationType.CONFIRM_DEBIT, true, "CONFIRM_DEBIT success"));

        mockMvc.perform(post(BASE + "/confirm-debit").contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.operationType").value("CONFIRM_DEBIT"));
    }

    @Test
    void cancelFreezeReturnsOk() throws Exception {
        given(service.cancelFreeze(any())).willReturn(
                new AssetOperationResponse("transfer-1", OperationType.CANCEL_FREEZE, true, "CANCEL_FREEZE success"));

        mockMvc.perform(post(BASE + "/cancel-freeze").contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operationType").value("CANCEL_FREEZE"));
    }

    @Test
    void creditReturnsOk() throws Exception {
        given(service.credit(any())).willReturn(
                new AssetOperationResponse("transfer-1", OperationType.CREDIT, true, "CREDIT success"));

        mockMvc.perform(post(BASE + "/credit").contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operationType").value("CREDIT"));
    }

    @Test
    void rejectsInvalidRequestWithoutTouchingService() throws Exception {
        // amount 为 0，违反 @DecimalMin("0.00000001")；transferId 留空违反 @NotBlank
        AssetOperationRequest invalid = new AssetOperationRequest(
                "", "user-1", "USDT", BigDecimal.ZERO, TransferDirection.A_TO_B);

        mockMvc.perform(post(BASE + "/freeze").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

        verify(service, never()).freeze(any());
    }

    @Test
    void businessFailureReturnsFailEnvelopeWithHttp200() throws Exception {
        given(service.freeze(any())).willThrow(new IllegalStateException("insufficient available balance"));

        mockMvc.perform(post(BASE + "/freeze").contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ACCOUNT_OPERATION_FAILED"))
                .andExpect(jsonPath("$.message").value("insufficient available balance"));
    }
}
