package com.demo.transfer.common;

/**
 * 转账 Workflow 状态。
 *
 * <p>由 Temporal Workflow 推进，精简后仅保留可观测的关键节点状态：
 * CREATED → (WAIT_REVIEW →) DEBIT_SUCCESS → SUCCESS
 *         ↘ FREEZE_FAILED（冻结失败，不重试）
 *         ↘ REJECTED（审核驳回）
 *         ↘ CREDIT_FAILED（入账失败，不补偿）
 */
public enum TransferStatus {
    /** 主单已创建，Workflow 已启动，冻结步骤尚未完成。 */
    CREATED,
    /** 冻结业务失败，Workflow 已不可重试地终止。 */
    FREEZE_FAILED,
    /** 冻结成功，等待人工审核（仅 MANUAL_REVIEW 模式）。 */
    WAIT_REVIEW,
    /** 审核驳回，冻结已成功取消。 */
    REJECTED,
    /** 源账户确认扣减成功，目标账户入账进行中。 */
    DEBIT_SUCCESS,
    /** 目标账户入账失败，Workflow 重试中或已耗尽重试（不反向补偿）。 */
    CREDIT_FAILED,
    /** 全部步骤完成。 */
    SUCCESS
}
