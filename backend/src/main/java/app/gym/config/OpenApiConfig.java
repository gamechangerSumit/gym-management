package app.gym.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI gymOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Gym Management API")
                .version("1.0")
                .description("Owner / Staff / Member portal with QR attendance. Session cookie auth; "
                        + "send the XSRF-TOKEN cookie value in the X-XSRF-TOKEN header on state-changing calls."));
    }
}
