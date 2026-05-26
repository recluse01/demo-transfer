package com.demo.transfer.transfer.web;

import com.demo.transfer.common.AccountType;
import com.demo.transfer.common.ApiResponse;
import com.demo.transfer.transfer.domain.TransferOrder;
import com.demo.transfer.transfer.service.AccountClientRouter;
import com.demo.transfer.transfer.service.TransferOrderStateService;
import com.demo.transfer.transfer.workflow.ReviewDecision;
import com.demo.transfer.transfer.workflow.TransferWorkflow;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;

import java.math.BigDecimal;
import java.util.UUID;
import javax.validation.Valid;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 转账领域对外接口。
 *
 * <p>提供创建转账、人工审核和详情查询能力。
 * 创建转账时持久化主单并启动 Temporal Workflow；
 * 审核时通过 Signal 通知对应 Workflow；
 * 查询直接读取 transfer_order 表。
 */
@Slf4j
@Tag(name = "Transfer API", description = "跨账户转账主流程接口")
@RestController
@RequestMapping("/transfers")
public class TransferController {
    private final TransferOrderStateService stateService;
    private final WorkflowClient workflowClient;
    private final AccountClientRouter router;
    private final String taskQueue;

    public TransferController(TransferOrderStateService stateService,
            WorkflowClient workflowClient,
            AccountClientRouter router,
            @Value("${temporal.task-queue:transfer-queue}") String taskQueue) {
        this.stateService = stateService;
        this.workflowClient = workflowClient;
        this.router = router;
        this.taskQueue = taskQueue;
    }

    /** 创建一笔新的跨账户转账并启动 Temporal Workflow。 */
    @Operation(summary = "创建转账", description = "持久化转账单后立即触发 Temporal Workflow 执行 Saga 流程。")
    @PostMapping
    public ApiResponse<TransferOrder> create(@Valid @RequestBody CreateTransferRequest request) {
        log.info("Received create transfer request, userId={}, direction={}, assetCode={}, amount={}, mode={}",
                request.getUserId(), request.getDirection(), request.getAssetCode(), request.getAmount(),
                request.getMode());
        return execute(() -> {
            AccountType source = router.sourceType(request.getDirection());
            AccountType target = router.targetType(request.getDirection());
            String transferId = UUID.randomUUID().toString();
            TransferOrder order = TransferOrder.create(transferId, request.getUserId(), source, target,
                    request.getAssetCode(), request.getAmount(), request.getMode());
            if (request.getAmount().compareTo(BigDecimal.valueOf(110)) == 0) {
                throw new RuntimeException("模拟创建订单失败的情况...");
            }
            stateService.createOrder(order);

            WorkflowOptions options = WorkflowOptions.newBuilder()
                    .setWorkflowId(transferId)
                    .setTaskQueue(taskQueue)
                    .build();
            try {
                if (request.getAmount().compareTo(BigDecimal.valueOf(90)) == 0) {
                    throw new RuntimeException("模拟创建订单成功，workflow发送的情况...");
                }
                TransferWorkflow workflow = workflowClient.newWorkflowStub(TransferWorkflow.class, options);
                WorkflowClient.start(workflow::execute, transferId, request.getMode());
                log.info("Transfer workflow started, transferId={}, taskQueue={}, mode={}",
                        transferId, taskQueue, request.getMode());
            } catch (Exception ex) {
                log.warn("Transfer workflow start failed, transferId={}, message={}",
                        transferId, ex.getMessage(), ex);
                stateService.markInitFailed(transferId, ex.getMessage());
                throw new RuntimeException("Workflow 启动失败: " + ex.getMessage(), ex);
            }
            log.info("Create transfer request completed, transferId={}", transferId);
            return order;
        });
    }

    /** 处理人工审核结果，向对应 Workflow 发送 review Signal。 */
    @Operation(summary = "人工审核", description = "向等待审核的 Workflow 发送 Signal，通知审核通过或驳回。")
    @PostMapping("/{transferId}/review")
    public ApiResponse<TransferOrder> review(
            @Parameter(description = "转账唯一标识", required = true) @PathVariable String transferId,
            @RequestBody ReviewTransferRequest request) {
        log.info("Received review transfer request, transferId={}, approved={}, message={}",
                transferId, request.isApproved(), request.getMessage());
        return execute(() -> {
            TransferWorkflow workflow = workflowClient.newWorkflowStub(TransferWorkflow.class, transferId);
            workflow.review(new ReviewDecision(request.isApproved(), request.getMessage()));
            log.info("Review signal sent, transferId={}, approved={}", transferId, request.isApproved());
            return stateService.loadOrder(transferId);
        });
    }

    /** 查询转账主单详情。 */
    @Operation(summary = "查询转账单", description = "查询转账当前状态、金额、模式和最近一次错误信息。")
    @GetMapping("/{transferId}")
    public ApiResponse<TransferOrder> get(
            @Parameter(description = "转账唯一标识", required = true) @PathVariable String transferId) {
        log.debug("Received get transfer request, transferId={}", transferId);
        return execute(() -> stateService.loadOrder(transferId));
    }

    private ApiResponse<TransferOrder> execute(Handler handler) {
        try {
            return ApiResponse.ok(handler.apply());
        } catch (RuntimeException ex) {
            log.warn("Transfer operation failed, message={}", ex.getMessage(), ex);
            return ApiResponse.fail("TRANSFER_OPERATION_FAILED", ex.getMessage());
        }
    }

    private interface Handler {
        TransferOrder apply();
    }
}
