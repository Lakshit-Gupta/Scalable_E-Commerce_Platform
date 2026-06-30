package com.ecommerce.common.error;

import com.ecommerce.common.web.CorrelationConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void apiException_rendersDeclaredStatusAsProblemDetail() throws Exception {
        mockMvc.perform(get("/missing"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.detail").value("Product not found: 42"))
            .andExpect(jsonPath("$.instance").value("/missing"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void apiException_includesCorrelationIdFromMdc() throws Exception {
        MDC.put(CorrelationConstants.MDC_KEY, "test-correlation-id");
        mockMvc.perform(get("/missing"))
            .andExpect(jsonPath("$.correlationId").value("test-correlation-id"));
    }

    @Test
    void methodArgumentNotValid_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Validation failed"))
            .andExpect(jsonPath("$.errors[0].field").value("name"))
            .andExpect(jsonPath("$.errors[0].message").exists());
    }

    @Test
    void unexpectedException_returnsSafe500() throws Exception {
        mockMvc.perform(get("/boom"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
            .andExpect(jsonPath("$.title").value("Internal Server Error"));
    }

    @RestController
    static class TestController {

        @org.springframework.web.bind.annotation.GetMapping("/missing")
        String missing() {
            throw new ResourceNotFoundException("Product", 42);
        }

        @PostMapping("/create")
        String create(@Valid @RequestBody CreateRequest body) {
            return "ok";
        }

        @org.springframework.web.bind.annotation.GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("internal detail that must not leak");
        }
    }

    record CreateRequest(@NotBlank String name) {
    }
}
