package com.hopoong.core.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestIdFilter extends OncePerRequestFilter {

    private static final int MAX_HEADER_LENGTH = 128;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = normalizeIncomingRequestId(request.getHeader(RequestCorrelationConstants.HEADER_REQUEST_ID));
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        try {
            MDC.put(RequestCorrelationConstants.MDC_REQUEST_ID, requestId);
            response.setHeader(RequestCorrelationConstants.HEADER_REQUEST_ID, requestId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(RequestCorrelationConstants.MDC_REQUEST_ID);
        }
    }

    private static String normalizeIncomingRequestId(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        String trimmed = headerValue.trim();
        if (trimmed.length() > MAX_HEADER_LENGTH) {
            return trimmed.substring(0, MAX_HEADER_LENGTH);
        }
        return trimmed;
    }
}
