package com.demo.transfer.transfer.web;

import io.swagger.v3.oas.annotations.media.Schema;

/** 自动提现结果回调请求。 */
@Schema(description = "自动提现结果回调请求")
public class WithdrawResultRequest {
    /** 提现是否成功。 */
    @Schema(description = "提现是否成功", example = "true")
    private boolean success;
    /** 提现结果说明。 */
    @Schema(description = "提现结果说明", example = "提币成功")
    private String message;

    public WithdrawResultRequest() {
    }

    public WithdrawResultRequest(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}
