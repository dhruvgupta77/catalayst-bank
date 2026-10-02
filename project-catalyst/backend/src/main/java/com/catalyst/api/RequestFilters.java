package com.catalyst.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Correlation-id propagation and a hard guard: automated/AI callers can never approve or execute.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestFilters extends OncePerRequestFilter {
    private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9._-]{1,40}$");
    private static final Pattern FINANCIAL = Pattern.compile("^/api/(actions/.+|recommendations/[^/]+/(approve|decline))$");

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String actor = req.getHeader("X-Actor-Type");
        if (actor != null && actor.equalsIgnoreCase("AI_AGENT") && "POST".equals(req.getMethod())
                && FINANCIAL.matcher(req.getRequestURI()).matches()) {
            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
            res.setContentType("application/json");
            res.getWriter().write("{\"status\":403,\"error\":\"Forbidden\",\"message\":\"AI agents cannot approve or execute financial actions\"}");
            return;
        }
        String corr = req.getHeader("X-Correlation-Id");
        if (corr != null && SAFE.matcher(corr).matches()) {
            MDC.put("correlationId", corr);
            res.setHeader("X-Correlation-Id", corr);
        }
        try {
            chain.doFilter(req, res);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
