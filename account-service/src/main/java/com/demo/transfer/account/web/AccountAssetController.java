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

/**
 * 账户服务内部资产操作接口。
 *
 * <p>仅供转账服务等内部系统调用，不面向终端用户直接开放。
 */
@RestController
@RequestMapping("/internal/accounts/assets")
public class AccountAssetController {
    /** 账户资产领域服务。 */
    private final AccountAssetService service;

    public AccountAssetController(AccountAssetService service) {
        this.service = service;
    }

    /** 冻结可用余额。 */
    @PostMapping("/freeze")
    public ApiResponse<AssetOperationResponse> freeze(@Valid @RequestBody AssetOperationRequest request) {
        return execute(() -> service.freeze(request));
    }

    /** 确认扣减冻结余额。 */
    @PostMapping("/confirm-debit")
    public ApiResponse<AssetOperationResponse> confirmDebit(@Valid @RequestBody AssetOperationRequest request) {
        return execute(() -> service.confirmDebit(request));
    }

    /** 取消冻结。 */
    @PostMapping("/cancel-freeze")
    public ApiResponse<AssetOperationResponse> cancelFreeze(@Valid @RequestBody AssetOperationRequest request) {
        return execute(() -> service.cancelFreeze(request));
    }

    /** 向目标账户入账。 */
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

    /** 控制器内部统一执行模板。 */
    private interface Operation {
        AssetOperationResponse apply();
    }
}
