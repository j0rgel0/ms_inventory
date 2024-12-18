package com.lox.inventoryservice.common.webclient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${product.catalog.service.url}")
    private String productCatalogServiceUrl;

    @Bean
    public WebClient productCatalogWebClient() {
        return WebClient.builder()
                .baseUrl(productCatalogServiceUrl)
                .build();
    }
}
