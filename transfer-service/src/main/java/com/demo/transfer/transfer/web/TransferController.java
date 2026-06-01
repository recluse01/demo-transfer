package com.demo.transfer.transfer.web;

import com.demo.transfer.common.ApiResponse;
import com.demo.transfer.transfer.domain.TransferOrder;
import com.demo.transfer.transfer.service.TransferRetryService;
import com.demo.transfer.transfer.service.TransferSagaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 转账领域对外接口。
 *
 * <p>提供创建转账、人工审核、提现结果回调、失败重试和详情查询能力。
 */
@Tag(name = "Transfer API", description = "跨账户转账主流程接口")
@RestController
@RequestMapping("/transfers")
public class TransferController {
    /** Saga 主流程服务。 */
    private final TransferSagaService sagaService;
    /** 失败重试服务。 */
    private final TransferRetryService retryService;

    public TransferController(TransferSagaService sagaService, TransferRetryService retryService) {
        this.sagaService = sagaService;
        this.retryService = retryService;
    }

    /** 创建一笔新的跨账户转账。 */
    @Operation(summary = "创建转账", description = "创建转账单并立即触发源账户冻结。")
    @PostMapping
    public ApiResponse<TransferOrder> create(@Valid @RequestBody CreateTransferRequest request) {
        return execute(() -> sagaService.createTransfer(request));
    }

    /** 处理人工审核结果。 */
    @Operation(summary = "人工审核", description = "对待审核转账执行通过或驳回。")
    @PostMapping("/{transferId}/review")
    public ApiResponse<TransferOrder> review(
            @Parameter(description = "转账唯一标识", required = true) @PathVariable String transferId,
            @RequestBody ReviewTransferRequest request) {
        return execute(() -> sagaService.review(new ReviewTransferRequest(transferId, request.isApproved(),
                request.getMessage())));
    }

    /** 接收兼容旧流程的自动提现结果回调。 */
    @Operation(summary = "兼容旧流程的提现结果回调", description = "仅处理仍停留在 WITHDRAW_PENDING 的历史流程。")
    @PostMapping("/{transferId}/withdraw-result")
    public ApiResponse<TransferOrder> withdrawResult(
            @Parameter(description = "转账唯一标识", required = true) @PathVariable String transferId,
            @RequestBody WithdrawResultRequest request) {
        return execute(() -> sagaService.handleWithdrawResult(transferId, request.isSuccess(), request.getMessage()));
    }

    /** 对可重试失败状态发起一次手动重试。 */
    @Operation(summary = "重试失败步骤", description = "对 DEBIT_FAILED、CREDIT_FAILED、CANCEL_FAILED 状态执行重试。")
    @PostMapping("/{transferId}/retry")
    public ApiResponse<TransferOrder> retry(
            @Parameter(description = "转账唯一标识", required = true) @PathVariable String transferId) {
        return execute(() -> retryService.retryOne(transferId));
    }

    /** 查询转账主单详情。 */
    @Operation(summary = "查询转账单", description = "查询转账当前状态、金额、模式和最近一次错误信息。")
    @GetMapping("/{transferId}")
    public ApiResponse<TransferOrder> get(
            @Parameter(description = "转账唯一标识", required = true) @PathVariable String transferId) {
        return execute(() -> sagaService.get(transferId));
    }

    private ApiResponse<TransferOrder> execute(Handler handler) {
        try {
            TransferOrder order = handler.apply();
            return ApiResponse.ok(order);
        } catch (RuntimeException ex) {
            return ApiResponse.fail("TRANSFER_OPERATION_FAILED", ex.getMessage());
        }
    }

    /** 控制器内部统一执行模板。 */
    private interface Handler {
        TransferOrder apply();
    }
}
