package com.synclite.qreader.web;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

@WebFilter("/*")
public class CSRFTokenFilter implements Filter {
    private static final String CSRF_TOKEN_SESSION_ATTR = "csrfToken";
    private static final String CSRF_TOKEN_PARAM = "csrfToken";

	@Override
	public void init(FilterConfig filterConfig) {
	}

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
            HttpServletRequest req = (HttpServletRequest) request;
            HttpServletResponse res = (HttpServletResponse) response;
            HttpSession session = req.getSession(true);

            // Set security headers
            res.setHeader("X-Content-Type-Options", "nosniff");
            res.setHeader("X-Frame-Options", "DENY");
            res.setHeader("X-XSS-Protection", "1; mode=block");
            res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

            // Generate CSRF token if not present
            if (session.getAttribute(CSRF_TOKEN_SESSION_ATTR) == null) {
                session.setAttribute(CSRF_TOKEN_SESSION_ATTR, generateToken());
            }

            // Validate CSRF token for POST requests
            if ("POST".equalsIgnoreCase(req.getMethod())) {
                String sessionToken = (String) session.getAttribute(CSRF_TOKEN_SESSION_ATTR);
                String requestToken = req.getParameter(CSRF_TOKEN_PARAM);
                if (sessionToken == null || requestToken == null || !sessionToken.equals(requestToken)) {
                    res.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid or missing CSRF token.");
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }

    private String generateToken() {
        byte[] token = new byte[32];
        new SecureRandom().nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

	@Override
	public void destroy() {
	}
}
