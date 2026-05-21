package com.demo.transfer.transfer.web;

import javax.validation.constraints.NotBlank;

public class ReviewTransferRequest {
    @NotBlank
    private String transferId;

    private boolean approved;

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
