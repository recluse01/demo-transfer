package com.demo.transfer.transfer.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

import com.demo.transfer.common.TransferDirection;
import com.demo.transfer.common.TransferMode;
import com.demo.transfer.common.TransferStatus;
import com.demo.transfer.transfer.domain.TransferOrder;
import com.demo.transfer.transfer.repository.TransferOrderRepository;
import com.demo.transfer.transfer.repository.TransferStepLogRepository;
import com.demo.transfer.transfer.service.TransferRetryService;
import com.demo.transfer.transfer.service.TransferSagaService;
import com.demo.transfer.transfer.support.AbstractMySqlIntegrationTest;
import com.demo.transfer.transfer.web.CreateTransferRequest;
import com.demo.transfer.transfer.web.ReviewTransferRequest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 转账 Saga 全流程保真轨集成测试。
 *
 * <p>结合 Testcontainers MySQL（真实 schema）+ WireMock（桩代 A/B 账户服务），
 * 通过真实 HTTP + 真实数据库，验证以下场景：
 * <ol>
 *   <li>AUTO_WITHDRAW 自动完成（正常路径）；</li>
 *   <li>MANUAL_REVIEW 人工审核通过 → SUCCESS；</li>
 *   <li>MANUAL_REVIEW 人工审核拒绝 → REJECTED；</li>
 *   <li>
 *     <strong>核心原则验证</strong>：入账失败 (CREDIT_FAILED) 后重试收敛到 SUCCESS，
 *     源账户 cancel-freeze 端点全程 <strong>零次调用</strong>（无反向补偿）。
 *   </li>
 * </ol>
 *
 * <p>继承 {@link AbstractMySqlIntegrationTest} 以获取单例 MySQL 容器与 datasource 注册；
 * 额外通过 {@link #registerWireMockUrls} 注入 Feign 指向 WireMock 的端口，
 * 两个 {@code @DynamicPropertySource} 方法在同一 Spring 上下文注册，互不冲突。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TransferScenarioIT extends AbstractMySqlIntegrationTest {

    /** 桩代 account-a-service 的 WireMock 实例（源账户，A_TO_B 方向）。 */
    private static WireMockServer wireMockA;
    /** 桩代 account-b-service 的 WireMock 实例（目标账户，A_TO_B 方向）。 */
    private static WireMockServer wireMockB;

    @BeforeAll
    static void startWireMock() {
        wireMockA = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockB = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockA.start();
        wireMockB.start();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockA != null) {
            wireMockA.stop();
        }
        if (wireMockB != null) {
            wireMockB.stop();
        }
    }

    /**
     * 将 Feign 客户端 URL 指向对应的 WireMock 端口。
     *
     * <p>父类的 {@link AbstractMySqlIntegrationTest#registerDatasource} 注册 datasource；
     * 本方法注册 account.a.url / account.b.url，二者均为 {@code @DynamicPropertySource}，
     * Spring 会合并处理，没有冲突。
     */
    @DynamicPropertySource
    static void registerWireMockUrls(DynamicPropertyRegistry registry) {
        registry.add("account.a.url", () -> "http://localhost:" + wireMockA.port());
        registry.add("account.b.url", () -> "http://localhost:" + wireMockB.port());
    }

    @Autowired
    private TransferSagaService sagaService;

    @Autowired
    private TransferRetryService retryService;

    @Autowired
    private TransferOrderRepository orderRepository;

    @Autowired
    private TransferStepLogRepository stepLogRepository;

    @BeforeEach
    void resetWireMock() {
        // 每个测试前清除所有 stub 和请求记录，避免测试间相互干扰
        wireMockA.resetAll();
        wireMockB.resetAll();
    }

    @AfterEach
    void cleanDatabase() {
        // 清理本次测试写入的数据，避免污染共享 MySQL 容器（单例模式），
        // 防止 MySqlContainerSmokeIT 等其他 IT 因非空表断言失败。
        stepLogRepository.deleteAll();
        orderRepository.deleteAll();
    }

    // ----------------------------------------------------------------
    // 场景一：AUTO_WITHDRAW 正常完成
    // ----------------------------------------------------------------

    @Test
    void autoWithdrawAToBCompletesNormally() {
        // A 账户（源）
        stubFreezeSuccess(wireMockA);
        stubConfirmDebitSuccess(wireMockA);
        // B 账户（目标）
        stubCreditSuccess(wireMockB);

        TransferOrder result = sagaService.createTransfer(
                createRequest(TransferDirection.A_TO_B, TransferMode.AUTO_WITHDRAW));

        // 验证最终状态持久化到真实 MySQL
        assertThat(result.getStatus()).isEqualTo(TransferStatus.SUCCESS);
        TransferOrder fromDb = orderRepository.findByTransferId(result.getTransferId())
                .orElseThrow(() -> new AssertionError("转账单未找到"));
        assertThat(fromDb.getStatus()).isEqualTo(TransferStatus.SUCCESS);
        assertThat(fromDb.getAmount()).isEqualByComparingTo(new BigDecimal("10.00"));

        // 验证端点调用顺序正确
        wireMockA.verify(1, postRequestedFor(urlEqualTo("/internal/accounts/assets/freeze")));
        wireMockA.verify(1, postRequestedFor(urlEqualTo("/internal/accounts/assets/confirm-debit")));
        wireMockB.verify(1, postRequestedFor(urlEqualTo("/internal/accounts/assets/credit")));
        // 无需取消冻结
        wireMockA.verify(0, postRequestedFor(urlEqualTo("/internal/accounts/assets/cancel-freeze")));
    }

    // ----------------------------------------------------------------
    // 场景二：MANUAL_REVIEW 审核通过 → SUCCESS
    // ----------------------------------------------------------------

    @Test
    void manualReviewApproveCompletesTransfer() {
        stubFreezeSuccess(wireMockA);
        stubConfirmDebitSuccess(wireMockA);
        stubCreditSuccess(wireMockB);

        // 创建后状态为 WAIT_REVIEW
        TransferOrder created = sagaService.createTransfer(
                createRequest(TransferDirection.A_TO_B, TransferMode.MANUAL_REVIEW));
        assertThat(created.getStatus()).isEqualTo(TransferStatus.WAIT_REVIEW);

        // 审核通过
        TransferOrder result = sagaService.review(
                new ReviewTransferRequest(created.getTransferId(), true, "审核通过"));

        assertThat(result.getStatus()).isEqualTo(TransferStatus.SUCCESS);
        // 验证数据库最终状态
        TransferOrder fromDb = orderRepository.findByTransferId(created.getTransferId()).orElseThrow(() -> new AssertionError("转账单未找到"));
        assertThat(fromDb.getStatus()).isEqualTo(TransferStatus.SUCCESS);

        wireMockA.verify(1, postRequestedFor(urlEqualTo("/internal/accounts/assets/freeze")));
        wireMockA.verify(1, postRequestedFor(urlEqualTo("/internal/accounts/assets/confirm-debit")));
        wireMockB.verify(1, postRequestedFor(urlEqualTo("/internal/accounts/assets/credit")));
        wireMockA.verify(0, postRequestedFor(urlEqualTo("/internal/accounts/assets/cancel-freeze")));
    }

    // ----------------------------------------------------------------
    // 场景三：MANUAL_REVIEW 审核拒绝 → REJECTED
    // ----------------------------------------------------------------

    @Test
    void manualReviewRejectCancelsAndRejects() {
        stubFreezeSuccess(wireMockA);
        stubCancelFreezeSuccess(wireMockA);

        TransferOrder created = sagaService.createTransfer(
                createRequest(TransferDirection.A_TO_B, TransferMode.MANUAL_REVIEW));
        assertThat(created.getStatus()).isEqualTo(TransferStatus.WAIT_REVIEW);

        TransferOrder result = sagaService.review(
                new ReviewTransferRequest(created.getTransferId(), false, "拒绝原因"));

        assertThat(result.getStatus()).isEqualTo(TransferStatus.REJECTED);
        TransferOrder fromDb = orderRepository.findByTransferId(created.getTransferId()).orElseThrow(() -> new AssertionError("转账单未找到"));
        assertThat(fromDb.getStatus()).isEqualTo(TransferStatus.REJECTED);

        // 取消冻结已调用
        wireMockA.verify(1, postRequestedFor(urlEqualTo("/internal/accounts/assets/cancel-freeze")));
        // 目标账户 B 未被调用（审核拒绝时不入账）
        wireMockB.verify(0, postRequestedFor(urlEqualTo("/internal/accounts/assets/credit")));
        wireMockA.verify(0, postRequestedFor(urlEqualTo("/internal/accounts/assets/confirm-debit")));
    }

    // ----------------------------------------------------------------
    // 场景四（核心原则）：入账失败 CREDIT_FAILED → 重试 → SUCCESS，且绝不触发反向补偿
    // ----------------------------------------------------------------

    /**
     * 验证核心设计原则：
     * <p><strong>源账户扣减一旦确认，目标账户入账失败不做反向补偿，只停留在 CREDIT_FAILED 持续重试入账。</strong>
     *
     * <p>测试步骤：
     * <ol>
     *   <li>创建转账 (MANUAL_REVIEW)，冻结成功 → WAIT_REVIEW；</li>
     *   <li>审核通过，confirm-debit 成功 → 入账第一次失败 → CREDIT_FAILED；</li>
     *   <li>断言数据库持久化状态为 CREDIT_FAILED；</li>
     *   <li>断言源账户 cancel-freeze <strong>零次调用</strong>（无补偿）；</li>
     *   <li>调用 retryService.retryOne，入账第二次成功 → SUCCESS；</li>
     *   <li>再次断言 cancel-freeze 仍为零次。</li>
     * </ol>
     */
    @Test
    void creditFailedRetryConvergesToSuccessWithoutAnyCompensation() {
        // A 账户（源）：冻结 + 扣减均成功
        stubFreezeSuccess(wireMockA);
        stubConfirmDebitSuccess(wireMockA);
        // 保持 cancel-freeze 桩（但预期永远不被调用）
        stubCancelFreezeSuccess(wireMockA);

        // B 账户（目标）：第一次入账失败，第二次成功（使用 WireMock 状态机 Scenario）
        // Scenario 名称：credit-retry，状态：STARTED（第一次）→ CREDIT_RETRY（第二次）
        wireMockB.stubFor(post(urlEqualTo("/internal/accounts/assets/credit"))
                .inScenario("credit-retry")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{"
                                + "\"success\":false,"
                                + "\"code\":\"ACCOUNT_OPERATION_FAILED\","
                                + "\"message\":\"入账失败，目标账户异常\","
                                + "\"data\":null}"))
                .willSetStateTo("CREDIT_RETRY"));

        wireMockB.stubFor(post(urlEqualTo("/internal/accounts/assets/credit"))
                .inScenario("credit-retry")
                .whenScenarioStateIs("CREDIT_RETRY")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{"
                                + "\"success\":true,"
                                + "\"code\":\"OK\","
                                + "\"message\":\"success\","
                                + "\"data\":{"
                                + "  \"transferId\":\"retry-t\","
                                + "  \"operationType\":\"CREDIT\","
                                + "  \"applied\":true,"
                                + "  \"message\":\"重试入账成功\""
                                + "}}")));

        // 步骤 1：创建转账并等待审核
        TransferOrder created = sagaService.createTransfer(
                createRequest(TransferDirection.A_TO_B, TransferMode.MANUAL_REVIEW));
        assertThat(created.getStatus()).isEqualTo(TransferStatus.WAIT_REVIEW);

        // 步骤 2：审核通过 → 入账第一次失败 → CREDIT_FAILED
        TransferOrder afterApprove = sagaService.review(
                new ReviewTransferRequest(created.getTransferId(), true, "审核通过"));
        assertThat(afterApprove.getStatus()).isEqualTo(TransferStatus.CREDIT_FAILED);

        // 步骤 3：断言数据库持久化 CREDIT_FAILED（真实 MySQL 验证）
        TransferOrder fromDbAfterFail = orderRepository.findByTransferId(created.getTransferId()).orElseThrow(() -> new AssertionError("转账单未找到"));
        assertThat(fromDbAfterFail.getStatus()).isEqualTo(TransferStatus.CREDIT_FAILED);
        assertThat(fromDbAfterFail.getLastErrorCode()).isEqualTo("ACCOUNT_OPERATION_FAILED");

        // 步骤 4【核心断言】：入账失败后，源账户 cancel-freeze 端点一次都没被调用
        // 这证明 Saga 没有执行反向补偿
        wireMockA.verify(0, postRequestedFor(urlEqualTo("/internal/accounts/assets/cancel-freeze")));

        // 步骤 5：重试 → 第二次入账成功 → SUCCESS
        TransferOrder afterRetry = retryService.retryOne(created.getTransferId());
        assertThat(afterRetry.getStatus()).isEqualTo(TransferStatus.SUCCESS);

        // 步骤 6：数据库最终状态为 SUCCESS
        TransferOrder fromDbFinal = orderRepository.findByTransferId(created.getTransferId()).orElseThrow(() -> new AssertionError("转账单未找到"));
        assertThat(fromDbFinal.getStatus()).isEqualTo(TransferStatus.SUCCESS);

        // 步骤 7【核心断言】：即使重试后，cancel-freeze 仍然是零次
        // 证明整个生命周期内没有触发任何反向补偿
        wireMockA.verify(0, postRequestedFor(urlEqualTo("/internal/accounts/assets/cancel-freeze")));

        // 辅助验证：confirm-debit 只调用一次（重试只重试 credit，不重试 debit）
        wireMockA.verify(1, postRequestedFor(urlEqualTo("/internal/accounts/assets/confirm-debit")));
        // credit 调用了两次（第一次失败 + 第二次重试成功）
        wireMockB.verify(2, postRequestedFor(urlEqualTo("/internal/accounts/assets/credit")));
    }

    // ----------------------------------------------------------------
    // 工具方法
    // ----------------------------------------------------------------

    private CreateTransferRequest createRequest(TransferDirection direction, TransferMode mode) {
        return new CreateTransferRequest("user-it", "USDT", new BigDecimal("10.00"), direction, mode);
    }

    /** 桩代指定 WireMock 服务器上的 /freeze 端点返回成功。 */
    private void stubFreezeSuccess(WireMockServer server) {
        server.stubFor(post(urlEqualTo("/internal/accounts/assets/freeze"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(successBody("FREEZE"))));
    }

    /** 桩代指定 WireMock 服务器上的 /confirm-debit 端点返回成功。 */
    private void stubConfirmDebitSuccess(WireMockServer server) {
        server.stubFor(post(urlEqualTo("/internal/accounts/assets/confirm-debit"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(successBody("CONFIRM_DEBIT"))));
    }

    /** 桩代指定 WireMock 服务器上的 /cancel-freeze 端点返回成功。 */
    private void stubCancelFreezeSuccess(WireMockServer server) {
        server.stubFor(post(urlEqualTo("/internal/accounts/assets/cancel-freeze"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(successBody("CANCEL_FREEZE"))));
    }

    /** 桩代指定 WireMock 服务器上的 /credit 端点返回成功。 */
    private void stubCreditSuccess(WireMockServer server) {
        server.stubFor(post(urlEqualTo("/internal/accounts/assets/credit"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(successBody("CREDIT"))));
    }

    /** 构造业务成功的 JSON 响应体，operationType 为 String 形式的枚举名称。 */
    private String successBody(String operationType) {
        return "{"
                + "\"success\":true,"
                + "\"code\":\"OK\","
                + "\"message\":\"success\","
                + "\"data\":{"
                + "  \"transferId\":\"stub-id\","
                + "  \"operationType\":\"" + operationType + "\","
                + "  \"applied\":true,"
                + "  \"message\":\"操作成功\""
                + "}}";
    }
}
