package com.demo.transfer.account.web;

import com.demo.transfer.account.service.AccountAssetService;
import com.demo.transfer.common.ApiResponse;
import com.demo.transfer.common.AssetOperationRequest;
import com.demo.transfer.common.AssetOperationResponse;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/accounts/assets")
public class AccountAssetController {
    private final AccountAssetService service;

    public AccountAssetController(AccountAssetService service) {
        this.service = service;
    }

    @PostMapping("/freeze")
    public ApiResponse<AssetOperationResponse> freeze(@Valid @RequestBody AssetOperationRequest request) {
        return execute(() -> service.freeze(request));
    }

    @PostMapping("/confirm-debit")
    public ApiResponse<AssetOperationResponse> confirmDebit(@Valid @RequestBody AssetOperationRequest request) {
        return execute(() -> service.confirmDebit(request));
    }

    @PostMapping("/cancel-freeze")
    public ApiResponse<AssetOperationResponse> cancelFreeze(@Valid @RequestBody AssetOperationRequest request) {
        return execute(() -> service.cancelFreeze(request));
    }

    @PostMapping("/credit")
    public ApiResponse<AssetOperationResponse> credit(@Valid @RequestBody AssetOperationRequest request) {
        return execute(() -> service.credit(request));
    }

    private ApiResponse<AssetOperationResponse> execute(Operation operation) {
        try {
            return ApiResponse.ok(operation.apply());
        } catch (IllegalStateException ex) {
            return ApiResponse.fail("ACCOUNT_OPERATION_FAILED", ex.getMessage());
        }
    }

    private interface Operation {
        AssetOperationResponse apply();
    }
}
