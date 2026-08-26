package com.aikiosk.backend.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * CORS covers the frontend's own origin across all of /api/** - the React app
 * (customer kiosk UI and, at /admin, the staff dashboard) runs on a different
 * port than the backend, so the browser needs this to read any response from
 * it. /api/admin/** is included because the dashboard is served from this
 * same origin, just a different path - the X-Admin-Key check
 * (AdminApiKeyFilter) is what actually gates access, not CORS.
 *
 * Registered as an explicit, highest-precedence Filter rather than via
 * WebMvcConfigurer.addCorsMappings, and deliberately not scoped per-path like
 * the old version was. addCorsMappings only adds headers to responses that
 * reach Spring MVC's own handler mapping - a request AdminApiKeyFilter
 * rejects with sendError() and a bare `return` never gets there, so that 401
 * came back with no CORS headers at all. The browser then treats it as an
 * opaque network error instead of a readable 401, which broke the admin
 * dashboard's "wrong key" flow. A plain Filter pinned to
 * Ordered.HIGHEST_PRECEDENCE runs before every other filter, including
 * AdminApiKeyFilter, so CORS headers land on every response regardless of
 * where in the chain it gets short-circuited.
 */
@Configuration
public class WebConfig {

    private final KioskProperties kioskProperties;

    public WebConfig(KioskProperties kioskProperties) {
        this.kioskProperties = kioskProperties;
    }

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(kioskProperties.getCors().getAllowedOrigin()));
        configuration.setAllowedMethods(List.of("GET", "POST", "DELETE"));
        configuration.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);

        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
