package com.aikiosk.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Guards /api/admin/** with a shared secret. Full staff auth (accounts, roles)
 * is out of scope until the admin dashboard (build-sequence step 7); this is
 * the minimum needed so voucher issuance isn't a public endpoint.
 */
@Component
public class AdminApiKeyFilter extends GenericFilterBean {

    private final KioskProperties kioskProperties;

    public AdminApiKeyFilter(KioskProperties kioskProperties) {
        this.kioskProperties = kioskProperties;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (httpRequest.getRequestURI().startsWith("/api/admin/") && !isAuthorized(httpRequest)) {
            httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isAuthorized(HttpServletRequest request) {
        String provided = request.getHeader("X-Admin-Key");
        String expected = kioskProperties.getAdmin().getApiKey();
        if (provided == null || expected == null) {
            return false;
        }
        return MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }
}
