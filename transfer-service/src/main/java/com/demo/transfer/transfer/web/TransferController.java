package com.demo.transfer.transfer.web;

import com.demo.transfer.common.ApiResponse;
import com.demo.transfer.transfer.domain.TransferOrder;
import com.demo.transfer.transfer.service.TransferRetryService;
import com.demo.transfer.transfer.service.TransferSagaService;
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
    @PostMapping
    public ApiResponse<TransferOrder> create(@Valid @RequestBody CreateTransferRequest request) {
        return execute(() -> sagaService.createTransfer(request));
    }

    /** 处理人工审核结果。 */
    @PostMapping("/{transferId}/review")
    public ApiResponse<TransferOrder> review(@PathVariable String transferId,
            @RequestBody ReviewTransferRequest request) {
        return execute(() -> sagaService.review(new ReviewTransferRequest(transferId, request.isApproved(),
                request.getMessage())));
    }

    /** 接收自动提现结果回调。 */
    @PostMapping("/{transferId}/withdraw-result")
    public ApiResponse<TransferOrder> withdrawResult(@PathVariable String transferId,
            @RequestBody WithdrawResultRequest request) {
        return execute(() -> sagaService.handleWithdrawResult(transferId, request.isSuccess(), request.getMessage()));
    }

    /** 对可重试失败状态发起一次手动重试。 */
    @PostMapping("/{transferId}/retry")
    public ApiResponse<TransferOrder> retry(@PathVariable String transferId) {
        return execute(() -> retryService.retryOne(transferId));
    }

    /** 查询转账主单详情。 */
    @GetMapping("/{transferId}")
    public ApiResponse<TransferOrder> get(@PathVariable String transferId) {
        return execute(() -> sagaService.get(transferId));
    }

    private ApiResponse<TransferOrder> execute(Operation operation) {
        try {
            return ApiResponse.ok(operation.apply());
        } catch (RuntimeException ex) {
            return ApiResponse.fail("TRANSFER_OPERATION_FAILED", ex.getMessage());
        }
    }

    /** 控制器内部统一执行模板。 */
    private interface Operation {
        TransferOrder apply();
    }
}
