package com.demo.transfer.transfer.client;

import com.demo.transfer.common.ApiResponse;
import com.demo.transfer.common.AssetOperationRequest;
import com.demo.transfer.common.AssetOperationResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

public interface AccountOperationsClient {
    @PostMapping("/internal/accounts/assets/freeze")
    ApiResponse<AssetOperationResponse> freeze(@RequestBody AssetOperationRequest request);

    @PostMapping("/internal/accounts/assets/confirm-debit")
    ApiResponse<AssetOperationResponse> confirmDebit(@RequestBody AssetOperationRequest request);

    @PostMapping("/internal/accounts/assets/cancel-freeze")
    ApiResponse<AssetOperationResponse> cancelFreeze(@RequestBody AssetOperationRequest request);

    @PostMapping("/internal/accounts/assets/credit")
    ApiResponse<AssetOperationResponse> credit(@RequestBody AssetOperationRequest request);
}
