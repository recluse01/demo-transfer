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

@RestController
@RequestMapping("/transfers")
public class TransferController {
    private final TransferSagaService sagaService;
    private final TransferRetryService retryService;

    public TransferController(TransferSagaService sagaService, TransferRetryService retryService) {
        this.sagaService = sagaService;
        this.retryService = retryService;
    }

    @PostMapping
    public ApiResponse<TransferOrder> create(@Valid @RequestBody CreateTransferRequest request) {
        return execute(() -> sagaService.createTransfer(request));
    }

    @PostMapping("/{transferId}/review")
    public ApiResponse<TransferOrder> review(@PathVariable String transferId,
            @RequestBody ReviewTransferRequest request) {
        return execute(() -> sagaService.review(new ReviewTransferRequest(transferId, request.isApproved(),
                request.getMessage())));
    }

    @PostMapping("/{transferId}/withdraw-result")
    public ApiResponse<TransferOrder> withdrawResult(@PathVariable String transferId,
            @RequestBody WithdrawResultRequest request) {
        return execute(() -> sagaService.handleWithdrawResult(transferId, request.isSuccess(), request.getMessage()));
    }

    @PostMapping("/{transferId}/retry")
    public ApiResponse<TransferOrder> retry(@PathVariable String transferId) {
        return execute(() -> retryService.retryOne(transferId));
    }

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

    private interface Operation {
        TransferOrder apply();
    }
}
