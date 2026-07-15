package com.hengshucredit.rule.server.auth;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ProjectAuthBodyFilter extends OncePerRequestFilter {

    private static final String[] PATH_PREFIXES = {
            "/api/sync",
            "/api/rule/sync",
            "/api/rule/log/report",
            "/api/rule/auth/token"
    };

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        for (String prefix : PATH_PREFIXES) {
            if (uri.equals(prefix) || uri.startsWith(prefix + "/")) return false;
        }
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (request.getContentLengthLong() > CachedBodyHttpServletRequest.MAX_BODY_SIZE) {
            rejectOversizedBody(response);
            return;
        }
        try {
            filterChain.doFilter(new CachedBodyHttpServletRequest(request), response);
        } catch (RequestBodyTooLargeException e) {
            rejectOversizedBody(response);
        }
    }

    private void rejectOversizedBody(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":413,\"message\":\"Request body too large\"}");
    }
}
