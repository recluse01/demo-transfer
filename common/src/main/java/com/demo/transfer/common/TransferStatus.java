package com.demo.transfer.common;

/**
 * 转账 Saga 状态。
 *
 * <p>状态推进顺序大致为：
 * CREATED -> WAIT_REVIEW/WITHDRAW_PENDING -> DEBIT_SUCCESS -> SUCCESS，
 * 过程中可能进入各类 FAILED 或 REJECTED 分支。
 */
public enum TransferStatus {
    /** 主单已创建，尚未完成冻结。 */
    CREATED,
    /** 源账户冻结失败。 */
    FREEZE_FAILED,
    /** 冻结成功，等待人工审核。 */
    WAIT_REVIEW,
    /** 冻结成功，等待自动提现结果。 */
    WITHDRAW_PENDING,
    /** 自动提现失败。 */
    WITHDRAW_FAILED,
    /** 取消冻结失败。 */
    CANCEL_FAILED,
    /** 转账被拒绝，且已成功取消冻结。 */
    REJECTED,
    /** 确认扣减失败。 */
    DEBIT_FAILED,
    /** 确认扣减成功，待目标账户入账。 */
    DEBIT_SUCCESS,
    /** 目标账户入账失败。 */
    CREDIT_FAILED,
    /** 全部步骤完成。 */
    SUCCESS
}
