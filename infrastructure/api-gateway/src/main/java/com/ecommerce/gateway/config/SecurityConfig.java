package com.ecommerce.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;

import java.nio.charset.StandardCharsets;

import reactor.core.publisher.Mono;

/**
 * Edge authentication (v0.0.9): the gateway is an OAuth2 Resource Server that validates Keycloak
 * access tokens against the realm JWKS (see {@code spring.security.oauth2.resourceserver.jwt}).
 * Identity is propagated downstream as trusted headers by {@code UserHeaderGlobalFilter}.
 *
 * Public (no token): auth (legacy), product reads, API docs, OTLP/actuator, the aggregated docs.
 * Everything else requires a valid bearer token. 401s are rendered as RFC-7807 problem+json to stay
 * consistent with the rest of the platform.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
        "/actuator/**",
        "/api/auth/**",          // legacy user-service auth endpoints
        "/api/payments/webhooks/**",  // Stripe webhooks — verified by signature, not JWT (v0.0.14)
        "/api/support/chatwoot/**",   // Chatwoot inbound webhooks — verified by shared token (v0.1.2)
        "/aggregate/**",         // aggregated Swagger api-docs
        "/swagger-ui.html",
        "/swagger-ui/**",
        "/v3/api-docs/**",
        "/webjars/**"
    };

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
                                                  ServerAuthenticationEntryPoint problemAuthEntryPoint) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .logout(ServerHttpSecurity.LogoutSpec::disable)
            .authorizeExchange(exchange -> exchange
                .pathMatchers(PUBLIC_PATHS).permitAll()
                .pathMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                .pathMatchers(HttpMethod.GET, "/api/recommendations/**").permitAll()
                .anyExchange().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .authenticationEntryPoint(problemAuthEntryPoint)
                .jwt(jwt -> { }));
        return http.build();
    }

    /** RFC-7807 application/problem+json for unauthenticated requests. */
    @Bean
    ServerAuthenticationEntryPoint problemAuthEntryPoint() {
        return (exchange, ex) -> {
            var response = exchange.getResponse();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
            String cid = exchange.getRequest().getHeaders().getFirst("X-Correlation-Id");
            String path = exchange.getRequest().getPath().value();
            String json = "{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,"
                + "\"detail\":\"Authentication required\",\"instance\":\"" + path + "\""
                + (cid != null ? ",\"correlationId\":\"" + cid + "\"" : "") + "}";
            DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        };
    }
}
