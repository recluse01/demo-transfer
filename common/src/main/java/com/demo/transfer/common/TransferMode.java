package com.demo.transfer.common;

/** 转账处理模式。 */
public enum TransferMode {
    /** 冻结成功后进入人工审核。 */
    MANUAL_REVIEW,
    /** 冻结成功后等待自动提现结果回调。 */
    AUTO_WITHDRAW
}
