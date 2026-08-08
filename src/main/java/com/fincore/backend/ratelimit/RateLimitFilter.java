package com.fincore.backend.ratelimit;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.*;

// @Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
    private final int LIMIT = 100;

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                   HttpServletResponse res,
                                   FilterChain chain)
            throws ServletException, IOException {

        String ip = req.getRemoteAddr();
        map.merge(ip, 1, Integer::sum);

        if (map.get(ip) > LIMIT) {
            res.setStatus(429);
            res.getWriter().write("Too many requests");
            return;
        }

        chain.doFilter(req, res);
    }
}
