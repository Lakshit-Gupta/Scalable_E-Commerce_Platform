package com.ecommerce.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * After Spring Security validates the Keycloak access token, copy identity onto trusted headers so
 * downstream services keep their existing "trust the gateway" contract (v0.0.9):
 *   X-User-Id   = token subject (Keycloak user id)
 *   X-User-Role = ADMIN if present, else CUSTOMER (from realm_access.roles)
 * Anonymous requests on public routes pass through unchanged. Runs early so the per-user
 * RequestRateLimiter key resolver sees X-User-Id.
 */
@Component
public class UserHeaderGlobalFilter implements GlobalFilter, Ordered {

    // Client-supplied copies of these are never trusted — always stripped, then set from the JWT.
    private static final Set<String> IDENTITY_HEADERS = Set.of("X-User-Id", "X-User-Role");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
            .filter(JwtAuthenticationToken.class::isInstance)
            .cast(JwtAuthenticationToken.class)
            .map(auth -> withIdentityHeaders(exchange, auth.getToken()))
            // Anonymous/public route: strip any spoofed identity headers, inject nothing.
            .defaultIfEmpty(stripIdentityHeaders(exchange))
            .flatMap(chain::filter);
    }

    private ServerWebExchange withIdentityHeaders(ServerWebExchange exchange, Jwt jwt) {
        var mutatedRequest = MutableRequestHeaders.with(exchange.getRequest(),
            Map.of("X-User-Id", jwt.getSubject(), "X-User-Role", resolveRole(jwt)),
            IDENTITY_HEADERS);
        return exchange.mutate().request(mutatedRequest).build();
    }

    private ServerWebExchange stripIdentityHeaders(ServerWebExchange exchange) {
        var mutatedRequest = MutableRequestHeaders.with(exchange.getRequest(), Map.of(), IDENTITY_HEADERS);
        return exchange.mutate().request(mutatedRequest).build();
    }

    @SuppressWarnings("unchecked")
    private String resolveRole(Jwt jwt) {
        Object realmAccess = jwt.getClaim("realm_access");
        if (realmAccess instanceof Map<?, ?> map && map.get("roles") instanceof List<?> roles) {
            if (roles.contains("ADMIN")) {
                return "ADMIN";
            }
            if (roles.contains("CUSTOMER")) {
                return "CUSTOMER";
            }
        }
        return "CUSTOMER";
    }

    @Override
    public int getOrder() {
        // Before RequestRateLimiter (so its key resolver sees X-User-Id), after Security web filters.
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
