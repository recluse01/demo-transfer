package com.demo.transfer.account;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

/** 验证测试失败时覆盖率评论步骤不会二次染红 CI。 */
class CoverageCommentFailureProbeTest {
    @Test
    void shouldFailFastForCoverageWorkflowValidation() {
        fail("intentional validation failure");
    }
}
