package com.demo.transfer.transfer.web;

import javax.validation.constraints.NotBlank;

/** 人工审核请求。 */
public class ReviewTransferRequest {
    /** 待审核的转账号。 */
    @NotBlank
    private String transferId;

    /** 是否审核通过。 */
    private boolean approved;

    /** 审核备注。 */
    private String message;

    public ReviewTransferRequest() {
    }

    public ReviewTransferRequest(String transferId, boolean approved, String message) {
        this.transferId = transferId;
        this.approved = approved;
        this.message = message;
    }

    public String getTransferId() {
        return transferId;
    }

    public boolean isApproved() {
        return approved;
    }

    public String getMessage() {
        return message;
    }
}
