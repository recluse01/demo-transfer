package com.demo.transfer.transfer.domain;

import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.PrePersist;
import javax.persistence.Table;

@Entity
@Table(name = "transfer_step_log")
public class TransferStepLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transfer_id", nullable = false, length = 64)
    private String transferId;

    @Column(name = "step_name", nullable = false, length = 64)
    private String stepName;

    @Column(name = "step_status", nullable = false, length = 32)
    private String stepStatus;

    @Lob
    @Column(name = "request_body")
    private String requestBody;

    @Lob
    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static TransferStepLog of(String transferId, String stepName, String stepStatus, String requestBody,
            String responseBody, String errorMessage) {
        TransferStepLog log = new TransferStepLog();
        log.transferId = transferId;
        log.stepName = stepName;
        log.stepStatus = stepStatus;
        log.requestBody = requestBody;
        log.responseBody = responseBody;
        log.errorMessage = errorMessage;
        return log;
    }

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
