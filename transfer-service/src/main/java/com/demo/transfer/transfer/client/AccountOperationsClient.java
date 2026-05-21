package com.demo.transfer.transfer.client;

import com.demo.transfer.common.ApiResponse;
import com.demo.transfer.common.AssetOperationRequest;
import com.demo.transfer.common.AssetOperationResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 账户服务资产操作接口定义。
 *
 * <p>由不同账户服务客户端复用同一组方法签名，保证转账服务调用方式一致。
 */
public interface AccountOperationsClient {
    /** 冻结源账户可用余额。 */
    @PostMapping("/internal/accounts/assets/freeze")
    ApiResponse<AssetOperationResponse> freeze(@RequestBody AssetOperationRequest request);

    /** 把冻结余额确认为实际扣减。 */
    @PostMapping("/internal/accounts/assets/confirm-debit")
    ApiResponse<AssetOperationResponse> confirmDebit(@RequestBody AssetOperationRequest request);

    /** 取消冻结并退回到可用余额。 */
    @PostMapping("/internal/accounts/assets/cancel-freeze")
    ApiResponse<AssetOperationResponse> cancelFreeze(@RequestBody AssetOperationRequest request);

    /** 向目标账户增加可用余额。 */
    @PostMapping("/internal/accounts/assets/credit")
    ApiResponse<AssetOperationResponse> credit(@RequestBody AssetOperationRequest request);
}
