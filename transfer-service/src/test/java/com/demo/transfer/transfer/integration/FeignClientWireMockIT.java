package com.demo.transfer.transfer.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.demo.transfer.common.ApiResponse;
import com.demo.transfer.common.AssetOperationRequest;
import com.demo.transfer.common.AssetOperationResponse;
import com.demo.transfer.common.OperationType;
import com.demo.transfer.common.TransferDirection;
import com.demo.transfer.transfer.client.AccountAClient;
import com.demo.transfer.transfer.client.AccountBClient;
import com.demo.transfer.transfer.support.AbstractMySqlIntegrationTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import feign.FeignException;
import feign.RetryableException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Feign 客户端保真轨集成测试。
 *
 * <p>启动两个 WireMock 服务器分别桩代 A/B 账户服务的 HTTP 端点，验证：
 * <ul>
 *   <li>请求序列化 + 响应反序列化（正常 200+success=true 路径）；</li>
 *   <li>业务失败响应解码（HTTP 200 + success=false）；</li>
 *   <li>下游 5xx 错误时 Feign 抛出 {@link FeignException}；</li>
 *   <li>响应延迟超过 Feign readTimeout 时抛出 {@link RetryableException}（读超时）。</li>
 * </ul>
 *
 * <p>读超时阈值（300ms）由 {@link #registerWireMockUrls} 通过 @DynamicPropertySource 注册的
 * {@code feign.client.config.default.readTimeout} 提供（详见该方法的说明）。</p>
 *
 * <p>继承 {@link AbstractMySqlIntegrationTest} 以满足完整 Spring 上下文的数据库依赖（JPA/scheduler）；
 * Feign 指向 WireMock 而非真实账户服务。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class FeignClientWireMockIT extends AbstractMySqlIntegrationTest {

    /** 桩代 account-a-service 的 WireMock 服务器。 */
    private static WireMockServer wireMockA;
    /** 桩代 account-b-service 的 WireMock 服务器。 */
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
     * 将 Feign URL 指向 WireMock 动态端口，并配置较短的读超时用于超时验证。
     *
     * <p>父类 {@link AbstractMySqlIntegrationTest#registerDatasource} 已注册 datasource 属性；
     * 本方法额外注册 account.a.url / account.b.url，两个 @DynamicPropertySource 方法合并生效。
     *
     * <p>读超时 readTimeout 设为 300ms。本版本 spring-cloud-openfeign-core 3.1.9 的
     * {@code FeignClientProperties} 仍以 {@code @ConfigurationProperties("feign.client")} 绑定，
     * 故前缀用 {@code feign.client.config.default}（而非新版文档的 spring.cloud.openfeign.client）。
     * 通过 @DynamicPropertySource 注册可确保该属性以最高优先级进入 Environment，
     * 被 FeignClientProperties 的 Map 绑定可靠读取（写在 application-test.yml 中会因
     * FeignClientProperties 早期绑定时机错过 test profile，导致 config map 为空、超时不生效）。
     * 其余 IT 的 WireMock 响应均为即时返回，不会被 300ms 误伤。
     */
    @DynamicPropertySource
    static void registerWireMockUrls(DynamicPropertyRegistry registry) {
        registry.add("account.a.url", () -> "http://localhost:" + wireMockA.port());
        registry.add("account.b.url", () -> "http://localhost:" + wireMockB.port());
        registry.add("feign.client.config.default.connectTimeout", () -> "1000");
        registry.add("feign.client.config.default.readTimeout", () -> "300");
    }

    @Autowired
    private AccountAClient accountAClient;

    @Autowired
    private AccountBClient accountBClient;

    @BeforeEach
    void resetWireMock() {
        wireMockA.resetAll();
        wireMockB.resetAll();
    }

    // ----------------------------------------------------------------
    // 1. 请求序列化 + 响应反序列化（HTTP 200, success=true）
    // ----------------------------------------------------------------

    @Test
    void freezeRequestSerializedAndSuccessResponseDeserialized() {
        // 桩代 account-a-service 的 /freeze 端点返回成功
        wireMockA.stubFor(post(urlEqualTo("/internal/accounts/assets/freeze"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{"
                                + "\"success\":true,"
                                + "\"code\":\"OK\","
                                + "\"message\":\"success\","
                                + "\"data\":{"
                                + "  \"transferId\":\"t-001\","
                                + "  \"operationType\":\"FREEZE\","
                                + "  \"applied\":true,"
                                + "  \"message\":\"冻结成功\""
                                + "}}")));

        AssetOperationRequest req = new AssetOperationRequest(
                "t-001", "user-1", "USDT", new BigDecimal("10.00"), TransferDirection.A_TO_B);

        ApiResponse<AssetOperationResponse> resp = accountAClient.freeze(req);

        // 验证响应正确反序列化
        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getCode()).isEqualTo("OK");
        assertThat(resp.getData()).isNotNull();
        assertThat(resp.getData().getTransferId()).isEqualTo("t-001");
        assertThat(resp.getData().getOperationType()).isEqualTo(OperationType.FREEZE);
        assertThat(resp.getData().isApplied()).isTrue();

        // 验证请求确实发出（序列化正确，WireMock 收到了正确路径的 POST）
        wireMockA.verify(1, postRequestedFor(urlEqualTo("/internal/accounts/assets/freeze")));
    }

    @Test
    void creditRequestToAccountBSerializedAndSuccessResponseDeserialized() {
        wireMockB.stubFor(post(urlEqualTo("/internal/accounts/assets/credit"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{"
                                + "\"success\":true,"
                                + "\"code\":\"OK\","
                                + "\"message\":\"success\","
                                + "\"data\":{"
                                + "  \"transferId\":\"t-002\","
                                + "  \"operationType\":\"CREDIT\","
                                + "  \"applied\":true,"
                                + "  \"message\":\"入账成功\""
                                + "}}")));

        AssetOperationRequest req = new AssetOperationRequest(
                "t-002", "user-1", "USDT", new BigDecimal("5.50"), TransferDirection.A_TO_B);

        ApiResponse<AssetOperationResponse> resp = accountBClient.credit(req);

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getData().getOperationType()).isEqualTo(OperationType.CREDIT);
        assertThat(resp.getData().isApplied()).isTrue();
        wireMockB.verify(1, postRequestedFor(urlEqualTo("/internal/accounts/assets/credit")));
    }

    // ----------------------------------------------------------------
    // 2. 业务失败响应解码（HTTP 200, success=false）
    // ----------------------------------------------------------------

    @Test
    void businessFailureResponseDeserializedWithSuccessFalse() {
        // 真实场景：账户服务 HTTP 200 + success=false 表示业务错误（如余额不足）
        wireMockA.stubFor(post(urlEqualTo("/internal/accounts/assets/freeze"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{"
                                + "\"success\":false,"
                                + "\"code\":\"ACCOUNT_OPERATION_FAILED\","
                                + "\"message\":\"insufficient balance\","
                                + "\"data\":null}")));

        AssetOperationRequest req = new AssetOperationRequest(
                "t-003", "user-1", "USDT", new BigDecimal("99999.00"), TransferDirection.A_TO_B);

        ApiResponse<AssetOperationResponse> resp = accountAClient.freeze(req);

        // success=false 时 Saga 会判断失败，Feign 本身不应抛异常（HTTP 200）
        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.getCode()).isEqualTo("ACCOUNT_OPERATION_FAILED");
        assertThat(resp.getMessage()).contains("insufficient");
        assertThat(resp.getData()).isNull();
    }

    @Test
    void idempotentAppliedFalseResponseDeserializedCorrectly() {
        // 幂等命中：success=true，但 applied=false（余额未重复变动）
        wireMockA.stubFor(post(urlEqualTo("/internal/accounts/assets/confirm-debit"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{"
                                + "\"success\":true,"
                                + "\"code\":\"OK\","
                                + "\"message\":\"success\","
                                + "\"data\":{"
                                + "  \"transferId\":\"t-004\","
                                + "  \"operationType\":\"CONFIRM_DEBIT\","
                                + "  \"applied\":false,"
                                + "  \"message\":\"幂等命中，未重复执行\""
                                + "}}")));

        AssetOperationRequest req = new AssetOperationRequest(
                "t-004", "user-1", "USDT", new BigDecimal("10.00"), TransferDirection.A_TO_B);

        ApiResponse<AssetOperationResponse> resp = accountAClient.confirmDebit(req);

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getData().isApplied()).isFalse();
    }

    // ----------------------------------------------------------------
    // 3. 下游 5xx 错误解码：FeignException 抛出
    // ----------------------------------------------------------------

    @Test
    void downstreamServerErrorThrowsFeignException() {
        // HTTP 500 应触发 FeignException（Feign 默认错误解码器对非 2xx 抛异常）
        wireMockA.stubFor(post(urlEqualTo("/internal/accounts/assets/cancel-freeze"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Internal Server Error\"}")));

        AssetOperationRequest req = new AssetOperationRequest(
                "t-005", "user-1", "USDT", new BigDecimal("10.00"), TransferDirection.A_TO_B);

        assertThatThrownBy(() -> accountAClient.cancelFreeze(req))
                .isInstanceOf(FeignException.class);
    }

    @Test
    void downstreamUnavailableOnCreditThrowsFeignException() {
        wireMockB.stubFor(post(urlEqualTo("/internal/accounts/assets/credit"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withBody("Service Unavailable")));

        AssetOperationRequest req = new AssetOperationRequest(
                "t-006", "user-1", "USDT", new BigDecimal("10.00"), TransferDirection.A_TO_B);

        assertThatThrownBy(() -> accountBClient.credit(req))
                .isInstanceOf(FeignException.class);
    }

    // ----------------------------------------------------------------
    // 4. 读超时：响应延迟超过 readTimeout(300ms) → 抛出超时异常
    // ----------------------------------------------------------------

    @Test
    void responseDelayExceedingReadTimeoutThrowsTimeout() {
        // 注入 1000ms 固定延迟，远大于测试 profile 中配置的 readTimeout=300ms，
        // 故 Feign 在读取响应时应触发 SocketTimeoutException，并被包装成 RetryableException。
        wireMockA.stubFor(post(urlEqualTo("/internal/accounts/assets/freeze"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(1000)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{"
                                + "\"success\":true,"
                                + "\"code\":\"OK\","
                                + "\"message\":\"success\","
                                + "\"data\":{"
                                + "  \"transferId\":\"t-timeout\","
                                + "  \"operationType\":\"FREEZE\","
                                + "  \"applied\":true,"
                                + "  \"message\":\"延迟响应\""
                                + "}}")));

        AssetOperationRequest req = new AssetOperationRequest(
                "t-timeout", "user-1", "USDT", new BigDecimal("10.00"), TransferDirection.A_TO_B);

        // 超时应抛出 feign.RetryableException，其 root cause 为 SocketTimeoutException。
        // 已验证：50ms 延迟（< 300ms readTimeout）不抛异常、正常返回；
        // 1000ms 延迟（> 300ms readTimeout）则在 ~340ms 处中断并抛出本异常，证明断言由超时阈值驱动。
        assertThatThrownBy(() -> accountAClient.freeze(req))
                .isInstanceOf(RetryableException.class)
                .hasRootCauseInstanceOf(SocketTimeoutException.class);
    }
}
