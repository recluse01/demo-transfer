package com.demo.transfer.transfer.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.demo.transfer.common.ApiResponse;
import com.demo.transfer.common.TransferDirection;
import com.demo.transfer.common.TransferMode;
import com.demo.transfer.common.TransferStatus;
import com.demo.transfer.transfer.client.AccountAClient;
import com.demo.transfer.transfer.client.AccountBClient;
import com.demo.transfer.transfer.domain.TransferOrder;
import com.demo.transfer.transfer.repository.TransferOrderRepository;
import com.demo.transfer.transfer.service.AccountClientRouter;
import com.demo.transfer.transfer.service.TransferOrderStateService;
import io.temporal.client.WorkflowClient;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@ContextConfiguration(classes = TransferControllerTest.ControllerConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TransferControllerTest {
    @Autowired
    private TransferController controller;

    @Autowired
    private TransferOrderRepository orderRepository;

    @MockBean
    private AccountAClient accountAClient;

    @MockBean
    private AccountBClient accountBClient;

    @MockBean
    private WorkflowClient workflowClient;

    @Test
    void marksOrderInitFailedWhenWorkflowStartFailsAfterOrderCreated() {
        CreateTransferRequest request = new CreateTransferRequest("user-1", "USDT", new BigDecimal("90.00"),
                TransferDirection.A_TO_B, TransferMode.MANUAL_REVIEW);

        ApiResponse<TransferOrder> response = controller.create(request);

        assertThat(response.isSuccess()).isFalse();
        TransferOrder order = orderRepository.findAll().get(0);
        assertThat(order.getStatus()).isEqualTo(TransferStatus.INIT_FAILED);
        assertThat(order.getLastErrorCode()).isEqualTo("WORKFLOW_START_FAILED");
    }

    @EnableJpaRepositories(basePackages = "com.demo.transfer.transfer.repository")
    @EntityScan(basePackages = "com.demo.transfer.transfer.domain")
    @Import({TransferController.class, TransferOrderStateService.class, AccountClientRouter.class})
    static class ControllerConfig {
    }
}
