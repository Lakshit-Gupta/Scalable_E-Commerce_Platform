package com.ecommerce.common.web;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

/**
 * Propagates the current correlation id onto outbound Feign requests so a call chain
 * across services shares one id. Only wired when Feign is on the classpath.
 */
public class CorrelationIdFeignInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String correlationId = MDC.get(CorrelationConstants.MDC_KEY);
        if (StringUtils.hasText(correlationId) && !template.headers().containsKey(CorrelationConstants.HEADER)) {
            template.header(CorrelationConstants.HEADER, correlationId);
        }
    }
}
