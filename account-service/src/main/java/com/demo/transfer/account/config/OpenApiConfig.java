package com.demo.transfer.account.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI accountOpenApi(@Value("${spring.application.name}") String applicationName) {
        return new OpenAPI().info(new Info()
                .title("账户资产服务 API - " + applicationName)
                .description("用于冻结、确认扣减、取消冻结和目标账户入账的内部资产接口。")
                .version("v1")
                .contact(new Contact().name("demo-transfer"))
                .license(new License().name("Internal Demo Use")));
    }
}
