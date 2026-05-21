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
/**
 * 转账 Saga 步骤日志。
 *
 * <p>按步骤维度记录冻结、扣减、入账、解冻等执行结果，便于审计和失败追踪。
 */
public class TransferStepLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联的业务转账号。 */
    @Column(name = "transfer_id", nullable = false, length = 64)
    private String transferId;

    /** 步骤名称，例如 FREEZE、CONFIRM_DEBIT、CREDIT。 */
    @Column(name = "step_name", nullable = false, length = 64)
    private String stepName;

    /** 步骤状态，例如 SUCCESS、FAILED。 */
    @Column(name = "step_status", nullable = false, length = 32)
    private String stepStatus;

    /** 可选的请求快照，当前示例未填充。 */
    @Lob
    @Column(name = "request_body")
    private String requestBody;

    /** 可选的响应快照，当前示例未填充。 */
    @Lob
    @Column(name = "response_body")
    private String responseBody;

    /** 失败时的错误信息。 */
    @Column(name = "error_message", length = 512)
    private String errorMessage;

    /** 日志创建时间。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 构造一条步骤日志。 */
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
