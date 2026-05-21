package com.demo.transfer.transfer.web;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotBlank;

/** 人工审核请求。 */
@Schema(description = "人工审核请求")
public class ReviewTransferRequest {
    /** 待审核的转账号。 */
    @Schema(description = "待审核的转账 ID", example = "0b3540d5-ec2a-4a64-a0d1-3b77fa8d9310")
    @NotBlank
    private String transferId;

    /** 是否审核通过。 */
    @Schema(description = "是否审核通过", example = "true")
    private boolean approved;

    /** 审核备注。 */
    @Schema(description = "审核备注", example = "人工审核通过")
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
