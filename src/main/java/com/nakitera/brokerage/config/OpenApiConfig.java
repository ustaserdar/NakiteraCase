package com.nakitera.brokerage.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI nakiteraOpenApi() {
        SecurityScheme basicAuth = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("basic")
                .description("Evaluation users: admin/admin123, customer-1/customer123, customer-2/customer123");

        return new OpenAPI()
                .info(new Info()
                        .title("Nakitera Brokerage API")
                        .version("1.0.0")
                        .description("""
                                REST API for creating, listing, canceling, and matching stock orders.
                                Use Authorize with HTTP Basic before Try it out.
                                Seeded customers: customer-1 and customer-2.
                                """))
                .servers(List.of(new Server().url("/").description("Local")))
                .components(new Components().addSecuritySchemes("basicAuth", basicAuth))
                .addSecurityItem(new SecurityRequirement().addList("basicAuth"));
    }
}
