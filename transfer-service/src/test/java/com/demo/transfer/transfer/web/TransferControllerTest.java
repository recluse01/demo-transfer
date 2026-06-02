package com.demo.transfer.transfer.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.demo.transfer.common.AccountType;
import com.demo.transfer.common.TransferDirection;
import com.demo.transfer.common.TransferMode;
import com.demo.transfer.transfer.domain.TransferOrder;
import com.demo.transfer.transfer.service.TransferRetryService;
import com.demo.transfer.transfer.service.TransferSagaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link TransferController} 的 Web 切片测试。
 *
 * <p>覆盖 create/review/withdraw-result/retry/get 五端点的成功路径、统一 {@code ApiResponse} 结构、
 * create 的入参校验（400）、业务异常（200+success=false），以及 review 将路径转账号透传给服务层。
 */
@WebMvcTest(TransferController.class)
class TransferControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransferSagaService sagaService;

    @MockBean
    private TransferRetryService retryService;

    private TransferOrder sampleOrder(String transferId) {
        return TransferOrder.create(transferId, "user-1", AccountType.ACCOUNT_A, AccountType.ACCOUNT_B,
                "USDT", new BigDecimal("100.00000000"), TransferMode.MANUAL_REVIEW);
    }

    @Test
    void createReturnsOkWithOrder() throws Exception {
        given(sagaService.createTransfer(any())).willReturn(sampleOrder("t-1"));
        CreateTransferRequest request = new CreateTransferRequest("user-1", "USDT", new BigDecimal("100"),
                TransferDirection.A_TO_B, TransferMode.MANUAL_REVIEW);

        mockMvc.perform(post("/transfers").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.transferId").value("t-1"))
                .andExpect(jsonPath("$.data.status").value("CREATED"));
    }

    @Test
    void createRejectsInvalidRequest() throws Exception {
        // userId 空、amount 为 0、direction/mode 缺失，违反 @Valid 约束
        CreateTransferRequest invalid = new CreateTransferRequest("", "USDT", BigDecimal.ZERO, null, null);

        mockMvc.perform(post("/transfers").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

        verify(sagaService, never()).createTransfer(any());
    }

    @Test
    void reviewPassesPathTransferIdToService() throws Exception {
        given(sagaService.review(any())).willReturn(sampleOrder("t-2"));
        ReviewTransferRequest body = new ReviewTransferRequest(null, true, "人工审核通过");

        mockMvc.perform(post("/transfers/t-2/review").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<ReviewTransferRequest> captor = ArgumentCaptor.forClass(ReviewTransferRequest.class);
        verify(sagaService).review(captor.capture());
        assertThat(captor.getValue().getTransferId()).isEqualTo("t-2");
        assertThat(captor.getValue().isApproved()).isTrue();
    }

    @Test
    void withdrawResultDelegatesToService() throws Exception {
        given(sagaService.handleWithdrawResult(eq("t-3"), eq(true), any())).willReturn(sampleOrder("t-3"));
        WithdrawResultRequest body = new WithdrawResultRequest(true, "提币成功");

        mockMvc.perform(post("/transfers/t-3/withdraw-result").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transferId").value("t-3"));

        verify(sagaService).handleWithdrawResult(eq("t-3"), eq(true), any());
    }

    @Test
    void retryDelegatesToRetryService() throws Exception {
        given(retryService.retryOne("t-4")).willReturn(sampleOrder("t-4"));

        mockMvc.perform(post("/transfers/t-4/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transferId").value("t-4"));

        verify(retryService).retryOne("t-4");
    }

    @Test
    void getReturnsOrder() throws Exception {
        given(sagaService.get("t-5")).willReturn(sampleOrder("t-5"));

        mockMvc.perform(get("/transfers/t-5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transferId").value("t-5"));
    }

    @Test
    void businessFailureReturnsFailEnvelopeWithHttp200() throws Exception {
        given(sagaService.get("missing")).willThrow(new IllegalArgumentException("transfer not found"));

        mockMvc.perform(get("/transfers/missing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("TRANSFER_OPERATION_FAILED"))
                .andExpect(jsonPath("$.message").value("transfer not found"));
    }
}
