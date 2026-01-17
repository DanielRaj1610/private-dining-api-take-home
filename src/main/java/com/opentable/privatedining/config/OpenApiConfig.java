package com.opentable.privatedining.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger configuration for API documentation.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Private Dining Reservation API")
                        .version("1.0.0")
                        .description("""
                                A comprehensive REST API for managing private dining room reservations.

                                ## Features
                                - **Reservation Management**: Create, view, and cancel reservations with intelligent availability checking
                                - **Capacity Management**: Supports concurrent reservations with flexible capacity constraints
                                - **Operating Hours**: Enforces restaurant operating hours per day of week
                                - **Occupancy Reporting**: Analytics and insights on reservation patterns

                                ## Concurrency Handling
                                Uses optimistic locking with retry mechanism to prevent overbooking under high load.

                                ## Business Rules
                                - Reservations must be within operating hours
                                - Maximum 90 days advance booking
                                - Party size must fit within available capacity
                                """)
                        .contact(new Contact()
                                .name("Private Dining API Support")
                                .email("support@privatedining.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local Development Server"),
                        new Server()
                                .url("http://localhost:8080")
                                .description("Docker Development Server")
                ));
    }
}
