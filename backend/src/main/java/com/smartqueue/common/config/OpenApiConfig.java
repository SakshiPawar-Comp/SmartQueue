package com.smartqueue.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI smartQueueOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SmartQueue API")
                        .version("0.1.0")
                        .description("REST API for the SmartQueue queue orchestration platform."));
    }
}
