package com.example.fileshare.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingFilterTests {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @Test
    void keepsValidRequestIdAndExposesItDuringTheRequest(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/files/42");
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "client-req-12345");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/files/{fileId}");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> seenInChain.set(MDC.get(RequestLoggingFilter.MDC_KEY)));

        assertThat(seenInChain.get()).isEqualTo("client-req-12345");
        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isEqualTo("client-req-12345");
        assertThat(MDC.get(RequestLoggingFilter.MDC_KEY)).isNull();
        assertThat(output).contains("http_request GET /api/files/{fileId} 200");
        assertThat(output).doesNotContain("/api/files/42");
    }

    @Test
    void replacesMissingOrInvalidRequestId() throws Exception {
        for (String header : new String[] {null, "bad id", "x", "a".repeat(65), "inject\nnewline1"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
            if (header != null) {
                request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, header);
            }
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, (req, res) -> { });

            String id = response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
            assertThat(id).isNotEqualTo(header).matches("[0-9a-f-]{36}");
        }
    }

    @Test
    void routeWithoutHandlerPatternMasksNumericSegments() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/files/123/download");
        assertThat(RequestLoggingFilter.route(request)).isEqualTo("/api/files/{id}/download");
    }
}
