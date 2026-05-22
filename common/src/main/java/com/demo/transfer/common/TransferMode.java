package com.demo.transfer.common;

/** 转账处理模式。 */
public enum TransferMode {
    /** 冻结成功后进入人工审核。 */
    MANUAL_REVIEW,
    /** 冻结成功后由系统自动完成站内划转。 */
    AUTO_WITHDRAW
}
