package com.demo.transfer.account.web;

import com.demo.transfer.account.service.AccountAssetService;
import com.demo.transfer.common.ApiResponse;
import com.demo.transfer.common.AssetOperationRequest;
import com.demo.transfer.common.AssetOperationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账户服务内部资产操作接口。
 *
 * <p>仅供转账服务等内部系统调用，不面向终端用户直接开放。
 */
@Slf4j
@Tag(name = "Account Asset API", description = "账户资产内部操作接口")
@RestController
@RequestMapping("/internal/accounts/assets")
public class AccountAssetController {
    /** 账户资产领域服务。 */
    private final AccountAssetService service;

    public AccountAssetController(AccountAssetService service) {
        this.service = service;
    }

    /** 冻结可用余额。 */
    @Operation(summary = "冻结资产", description = "将可用余额转入冻结余额。")
    @PostMapping("/freeze")
    public ApiResponse<AssetOperationResponse> freeze(@Valid @RequestBody AssetOperationRequest request) {
        log.info("收到冻结资产请求，transferId={}, userId={}, assetCode={}, amount={}",
                request.getTransferId(), request.getUserId(), request.getAssetCode(), request.getAmount());
        return execute(() -> service.freeze(request));
    }

    /** 确认扣减冻结余额。 */
    @Operation(summary = "确认扣减", description = "将冻结余额确认为实际扣减。")
    @PostMapping("/confirm-debit")
    public ApiResponse<AssetOperationResponse> confirmDebit(@Valid @RequestBody AssetOperationRequest request) {
        log.info("收到确认扣减请求，transferId={}, userId={}, assetCode={}, amount={}",
                request.getTransferId(), request.getUserId(), request.getAssetCode(), request.getAmount());
        return execute(() -> service.confirmDebit(request));
    }

    /** 取消冻结。 */
    @Operation(summary = "取消冻结", description = "将冻结余额恢复到可用余额。")
    @PostMapping("/cancel-freeze")
    public ApiResponse<AssetOperationResponse> cancelFreeze(@Valid @RequestBody AssetOperationRequest request) {
        log.info("收到取消冻结请求，transferId={}, userId={}, assetCode={}, amount={}",
                request.getTransferId(), request.getUserId(), request.getAssetCode(), request.getAmount());
        return execute(() -> service.cancelFreeze(request));
    }

    /** 向目标账户入账。 */
    @Operation(summary = "目标账户入账", description = "向目标账户增加可用余额。")
    @PostMapping("/credit")
    public ApiResponse<AssetOperationResponse> credit(@Valid @RequestBody AssetOperationRequest request) {
        log.info("收到资产入账请求，transferId={}, userId={}, assetCode={}, amount={}",
                request.getTransferId(), request.getUserId(), request.getAssetCode(), request.getAmount());
        return execute(() -> service.credit(request));
    }

    private ApiResponse<AssetOperationResponse> execute(Handler handler) {
        try {
            return ApiResponse.ok(handler.apply());
        } catch (IllegalStateException ex) {
            log.warn("账户资产操作失败，message={}", ex.getMessage(), ex);
            return ApiResponse.fail("ACCOUNT_OPERATION_FAILED", ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("账户资产操作发生非预期异常，message={}", ex.getMessage(), ex);
            return ApiResponse.fail("ACCOUNT_OPERATION_FAILED", ex.getMessage());
        }
    }

    /** 控制器内部统一执行模板。 */
    private interface Handler {
        AssetOperationResponse apply();
    }
}
