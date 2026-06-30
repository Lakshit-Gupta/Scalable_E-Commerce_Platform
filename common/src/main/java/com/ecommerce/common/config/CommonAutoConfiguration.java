package com.ecommerce.common.config;

import com.ecommerce.common.error.GlobalExceptionHandler;
import com.ecommerce.common.web.CorrelationIdFeignInterceptor;
import com.ecommerce.common.web.CorrelationIdFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the shared cross-cutting beans into any service that depends on the common library.
 * Registered through {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports},
 * so no service needs explicit @Import or component scanning.
 */
@AutoConfiguration
public class CommonAutoConfiguration {

    /** RFC-7807 error rendering. @ConditionalOnMissingBean lets a service override if it must. */
    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    /** Correlation-id / MDC filter — only in servlet web apps. */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }

    /** Outbound correlation-id propagation, active only when Feign is present. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "feign.RequestInterceptor")
    static class FeignCorrelationConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public CorrelationIdFeignInterceptor correlationIdFeignInterceptor() {
            return new CorrelationIdFeignInterceptor();
        }
    }
}
