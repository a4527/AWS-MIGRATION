package com.example.fileshare.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 요청마다 요청 ID를 정하고 한 줄 접근 로그를 남긴다(OBS-01).
 *
 * <p>요청 ID는 유효한 {@code X-Request-Id} 헤더를 그대로 쓰고, 없거나 형식이 맞지 않으면 새로 만든다. MDC의
 * {@code requestId}로 같은 요청의 모든 로그에 붙고 응답 헤더로 돌려준다. 접근 로그에는 메서드, 경로 패턴(ID 등 값 제외),
 * 상태 코드, 처리 시간만 남긴다. 요청 본문, 쿼리 문자열, Authorization 헤더, 파일 내용은 남기지 않는다.
 *
 * <p>Spring Security 필터보다 먼저 실행해 인증 실패(401·403)도 기록한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final Logger log = LoggerFactory.getLogger("http.access");
    private static final Pattern VALID_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{8,64}");
    private static final Pattern NUMERIC_SEGMENT = Pattern.compile("/\\d+(?=/|$)");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));
        MDC.put(MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        long start = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            log.atInfo()
                    .addKeyValue("event", "http_request")
                    .addKeyValue("method", request.getMethod())
                    .addKeyValue("route", route(request))
                    .addKeyValue("status", response.getStatus())
                    .addKeyValue("durationMs", durationMs)
                    .log("http_request {} {} {} {}ms", request.getMethod(), route(request), response.getStatus(), durationMs);
            MDC.remove(MDC_KEY);
        }
    }

    static String resolveRequestId(String header) {
        if (header != null && VALID_REQUEST_ID.matcher(header).matches()) {
            return header;
        }
        return UUID.randomUUID().toString();
    }

    /** 경로 패턴(예: /api/files/{fileId})을 쓴다. 패턴이 없으면(인증 실패 등) 숫자 경로 조각을 {id}로 바꾼다. */
    static String route(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern != null) {
            return pattern.toString();
        }
        return NUMERIC_SEGMENT.matcher(request.getRequestURI()).replaceAll("/{id}");
    }
}
