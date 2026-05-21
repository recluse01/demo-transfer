package com.demo.transfer.common;

/** 账户服务支持的资产操作类型。 */
public enum OperationType {
    /** 冻结源账户可用余额。 */
    FREEZE,
    /** 将冻结余额确认为实际扣减。 */
    CONFIRM_DEBIT,
    /** 取消冻结并回退到可用余额。 */
    CANCEL_FREEZE,
    /** 向目标账户增加可用余额。 */
    CREDIT
}
