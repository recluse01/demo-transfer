package com.demo.transfer.transfer.web;

public class WithdrawResultRequest {
    private boolean success;
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
