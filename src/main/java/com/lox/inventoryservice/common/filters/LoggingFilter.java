package com.lox.inventoryservice.common.filters;

import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class LoggingFilter implements WebFilter {

    @Override
    public @NonNull Mono<Void> filter(@NonNull ServerWebExchange exchange,
            @NonNull WebFilterChain chain) {
        log.info("Incoming request: {} {}", exchange.getRequest().getMethod(),
                exchange.getRequest().getURI());
        return chain.filter(exchange)
                .doOnSuccess(
                        aVoid -> log.info("Response: {}", exchange.getResponse().getStatusCode()));
    }
}
