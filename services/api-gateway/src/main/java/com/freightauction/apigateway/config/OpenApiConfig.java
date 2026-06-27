package com.freightauction.apigateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI gatewayOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Freight Auction Platform - API Gateway")
                        .version("1.0.0")
                        .description("""
                    🎯 Gateway centralizado para a Plataforma de Leilão de Frete
                    
                    Este gateway roteia requisições para os serviços especializados:
                    
                    **📌 Serviços disponíveis:**
                    - Auth Service: Autenticação e gerenciamento de usuários (porta 8084)
                    - Auction Service: Leilões e cargas de frete (porta 8081)
                    - Bid Service: Sistema de lances (porta 8082)
                    
                    **🔐 Autenticação:**
                    Todos os endpoints (exceto /v1/auth/*) requerem Bearer Token JWT
                    """)
                        .contact(new Contact()
                                .name("API Support")
                                .email("api@freightauction.com")
                                .url("https://freightauction.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")));
    }
}