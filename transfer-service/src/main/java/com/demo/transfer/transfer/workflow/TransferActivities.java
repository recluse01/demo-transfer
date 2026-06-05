package com.demo.transfer.transfer.workflow;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Temporal Activity 接口，声明转账流程中的四个原子步骤。
 *
 * <p>每个 Activity 方法负责：向账户服务发起 Feign 调用，并将结果写入 transfer_order。
 * 两个操作在各自独立的事务中执行，不共享同一个数据库连接。
 */
@ActivityInterface
public interface TransferActivities {
    @ActivityMethod
    void freeze(String transferId);

    @ActivityMethod
    void confirmDebit(String transferId);

    @ActivityMethod
    void credit(String transferId);

    @ActivityMethod
    void cancelFreeze(String transferId);
}
