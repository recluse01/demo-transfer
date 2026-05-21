package com.demo.transfer.transfer.web;

/** 自动提现结果回调请求。 */
public class WithdrawResultRequest {
    /** 提现是否成功。 */
    private boolean success;
    /** 提现结果说明。 */
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
