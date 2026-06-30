package com.ecommerce.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Edge generator for the correlation id. If the client didn't supply {@code X-Correlation-Id},
 * a UUID is minted and injected into the forwarded request so all downstream services (and their
 * common-lib filter) share one id. The id is written onto the response at commit time with
 * {@code set} — replacing any copy the downstream service echoed, so the response carries exactly
 * one value (fixes the duplicate-header issue). Gateway is reactive and can't use the servlet
 * common library, so the header name is repeated here.
 */
@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

    static final String HEADER = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(HEADER);
        String correlationId = StringUtils.hasText(incoming) ? incoming : UUID.randomUUID().toString();

        ServerHttpRequest mutatedRequest =
            MutableRequestHeaders.with(exchange.getRequest(), java.util.Map.of(HEADER, correlationId));
        ServerWebExchange mutated = exchange.mutate().request(mutatedRequest).build();

        // Set at commit time (after downstream headers merged) and replace duplicates -> single value.
        mutated.getResponse().beforeCommit(() -> {
            mutated.getResponse().getHeaders().set(HEADER, correlationId);
            return Mono.empty();
        });

        return chain.filter(mutated);
    }

    @Override
    public int getOrder() {
        // Before auth/rate-limit filters so even rejected requests are traceable.
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
