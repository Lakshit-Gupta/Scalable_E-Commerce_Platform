package com.ecommerce.common.web;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void generatesIdWhenHeaderAbsent_andEchoesOnResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String header = response.getHeader(CorrelationConstants.HEADER);
        assertThat(header).isNotBlank();
    }

    @Test
    void preservesIncomingCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationConstants.HEADER, "incoming-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationConstants.HEADER)).isEqualTo("incoming-id");
    }

    @Test
    void populatesMdcDuringChain_andClearsAfter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationConstants.HEADER, "mdc-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInChain = new AtomicReference<>();

        FilterChain capturing = new FilterChain() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse res) throws IOException, ServletException {
                seenInChain.set(MDC.get(CorrelationConstants.MDC_KEY));
            }
        };

        filter.doFilter(request, response, capturing);

        assertThat(seenInChain.get()).isEqualTo("mdc-id");
        assertThat(MDC.get(CorrelationConstants.MDC_KEY)).isNull(); // cleared in finally
    }
}
