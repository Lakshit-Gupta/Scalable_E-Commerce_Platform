package com.ecommerce.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Adds baseline security response headers at the edge (api-security best practices) and strips the
 * downstream {@code Server} fingerprint. Applied at commit time so it also covers proxied responses.
 */
@Component
public class SecurityHeadersGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        exchange.getResponse().beforeCommit(() -> {
            HttpHeaders h = exchange.getResponse().getHeaders();
            // HSTS is only meaningful over TLS, and emitting it over plaintext poisons the whole
            // host: browsers treat localhost as a secure context, so an HTTP HSTS header force-
            // upgrades every localhost port (incl. the :3000 storefront) to HTTPS -> SSL errors in
            // dev. Only send it when the edge request actually arrived over HTTPS (prod behind a
            // TLS-terminating proxy/CDN sets X-Forwarded-Proto=https).
            if (isHttps(exchange)) {
                h.set("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
            }
            h.set("X-Content-Type-Options", "nosniff");
            h.set("X-Frame-Options", "DENY");
            h.set("Referrer-Policy", "no-referrer");
            h.remove(HttpHeaders.SERVER);          // strip Tomcat's Server passed through
            h.remove("X-Application-Context");
            return Mono.empty();
        });
        return chain.filter(exchange);
    }

    /** True when the original client request reached the edge over HTTPS (direct TLS or X-Forwarded-Proto). */
    private static boolean isHttps(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-Proto");
        if (forwarded != null && !forwarded.isBlank()) {
            // may be a comma-separated list (proxy chain); the left-most is the original client
            return "https".equalsIgnoreCase(forwarded.split(",")[0].trim());
        }
        return "https".equalsIgnoreCase(exchange.getRequest().getURI().getScheme());
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
