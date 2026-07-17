package com.ecommerce.gateway.filter;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;

import java.util.Map;
import java.util.Set;

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
        return with(request, extra, Set.of());
    }

    /**
     * Decorate the request with {@code extra} headers set, and {@code remove} headers dropped first.
     * Removals are applied before sets so callers can strip client-supplied identity headers (spoof
     * defense) and re-set trusted values in one pass.
     */
    static ServerHttpRequest with(ServerHttpRequest request, Map<String, String> extra, Set<String> remove) {
        HttpHeaders writable = new HttpHeaders();
        writable.addAll(request.getHeaders());
        remove.forEach(writable::remove);
        extra.forEach(writable::set);
        return new ServerHttpRequestDecorator(request) {
            @Override
            public HttpHeaders getHeaders() {
                return writable;
            }
        };
    }
}
