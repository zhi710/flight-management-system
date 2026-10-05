package com.itemll.flight_management_system_sp.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc / OpenAPI 配置
 * <p>访问地址：http://localhost:8080/api/swagger-ui.html</p>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("航班管理系统 API")
                        .version("1.0.0")
                        .description("SkyTrip 航班管理系统接口文档，包含旅客端和管理端全部接口。")
                        .contact(new Contact().name("SkyTrip Dev Team").email("dev@skytrip.com")))
                // 全局 Bearer Token 认证
                .addSecurityItem(new SecurityRequirement().addList("Bearer"))
                .components(new Components()
                        .addSecuritySchemes("Bearer",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT Token，无需 Bearer 前缀")));
    }
}
