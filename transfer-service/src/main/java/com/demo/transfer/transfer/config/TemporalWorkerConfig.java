package com.demo.transfer.transfer.config;

import com.demo.transfer.transfer.workflow.TransferActivitiesImpl;
import com.demo.transfer.transfer.workflow.TransferWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Temporal Worker 配置。
 *
 * <p>在 Spring 上下文启动时自动注册 Workflow 实现和 Activity Bean，并启动 Worker 监听 Task Queue。
 */
@Slf4j
@Configuration
public class TemporalWorkerConfig {
    @Value("${temporal.host-port:localhost:7233}")
    private String hostPort;

    @Value("${temporal.namespace:default}")
    private String namespace;

    @Value("${temporal.task-queue:transfer-queue}")
    private String taskQueue;

    @Bean
    public WorkflowServiceStubs workflowServiceStubs() {
        log.info("Creating Temporal service stubs, hostPort={}", hostPort);
        return WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder()
                        .setTarget(hostPort)
                        .build());
    }

    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs stubs) {
        log.info("Creating Temporal workflow client, namespace={}", namespace);
        return WorkflowClient.newInstance(stubs,
                WorkflowClientOptions.newBuilder()
                        .setNamespace(namespace)
                        .build());
    }

    @Bean
    public WorkerFactory workerFactory(WorkflowClient client) {
        return WorkerFactory.newInstance(client);
    }

    @Bean
    public Worker transferWorker(WorkerFactory factory, TransferActivitiesImpl activities) {
        Worker worker = factory.newWorker(taskQueue);
        worker.registerWorkflowImplementationTypes(TransferWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        factory.start();
        log.info("Temporal worker started, taskQueue={}", taskQueue);
        return worker;
    }

    public String getTaskQueue() {
        return taskQueue;
    }
}
