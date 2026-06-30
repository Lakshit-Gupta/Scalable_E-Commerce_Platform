package com.ecommerce.gateway.filter;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;

import java.util.Map;

/**
 * Returns a request whose headers include {@code extra}, safe even when the source request exposes
 * {@code ReadOnlyHttpHeaders} (as it does once Spring Security wraps the exchange). The usual
 * {@code request.mutate().header(...)} path throws {@code UnsupportedOperationException} in that
 * case, so we decorate the request with a pre-built writable copy instead.
 */
final class MutableRequestHeaders {

    private MutableRequestHeaders() {
    }

    static ServerHttpRequest with(ServerHttpRequest request, Map<String, String> extra) {
        HttpHeaders writable = new HttpHeaders();
        writable.addAll(request.getHeaders());
        extra.forEach(writable::set);
        return new ServerHttpRequestDecorator(request) {
            @Override
            public HttpHeaders getHeaders() {
                return writable;
            }
        };
    }
}
