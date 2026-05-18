package io.mindspice.magenta2.api.web;

import java.io.IOException;
import java.util.function.Supplier;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AlphaSecurityConfiguration.AlphaAccessProperties.class)
public class AlphaSecurityConfiguration {

    @Bean
    public SecurityFilterChain alphaSecurityFilterChain(HttpSecurity http) throws Exception {
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        csrfRequestHandler.setCsrfRequestAttributeName("_csrf");

        http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.GET, "/**").permitAll()
                .requestMatchers(HttpMethod.HEAD, "/**").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .anyRequest().authenticated())
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(csrfRequestHandler))
            .httpBasic(basic -> basic.authenticationEntryPoint(alphaAuthenticationEntryPoint()))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(alphaAuthenticationEntryPoint())
                .accessDeniedHandler(alphaAccessDeniedHandler()))
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable);

        http.addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class);

        return http.build();
    }

    @Bean
    public UserDetailsService alphaUserDetailsService(AlphaAccessProperties properties) {
        if (!hasText(properties.username()) || !hasText(properties.password())) {
            throw new IllegalStateException("magenta.alpha-access username and password must be configured");
        }
        UserDetails user = User.withUsername(properties.username().trim())
            .password("{noop}" + properties.password())
            .roles("ALPHA")
            .build();
        return new InMemoryUserDetailsManager(user);
    }

    private AuthenticationEntryPoint alphaAuthenticationEntryPoint() {
        return (request, response, authException) ->
            writeSecurityError(request, response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required.");
    }

    private AccessDeniedHandler alphaAccessDeniedHandler() {
        return (request, response, accessDeniedException) ->
            writeSecurityError(request, response, HttpServletResponse.SC_FORBIDDEN, forbiddenMessage(accessDeniedException));
    }

    private static String forbiddenMessage(AccessDeniedException exception) {
        String message = exception.getMessage();
        if (message != null && message.toLowerCase().contains("csrf")) {
            return "CSRF token missing or invalid.";
        }
        return "Access denied.";
    }

    private static void writeSecurityError(
        HttpServletRequest request,
        HttpServletResponse response,
        int status,
        String message
    ) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        if (status == HttpServletResponse.SC_UNAUTHORIZED) {
            response.setHeader("WWW-Authenticate", "Basic realm=\"Magenta Alpha\"");
        }
        if (isHtmx(request)) {
            response.setHeader("HX-Trigger", "magenta:security-error");
            response.setContentType(MediaType.TEXT_HTML_VALUE);
            response.getWriter().write("<div class=\"mag-auth-error\" role=\"alert\">" + message + "</div>");
            return;
        }
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }

    private static boolean isHtmx(HttpServletRequest request) {
        return "true".equalsIgnoreCase(request.getHeader("HX-Request"));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @ConfigurationProperties(prefix = "magenta.alpha-access")
    public record AlphaAccessProperties(
        @DefaultValue("alpha") String username,
        @DefaultValue("change-me-alpha") String password
    ) {
    }

    private static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
        ) throws ServletException, IOException {
            CsrfToken token = csrfToken(request);
            if (token != null) {
                token.getToken();
            }
            filterChain.doFilter(request, response);
        }

        private static CsrfToken csrfToken(HttpServletRequest request) {
            Object token = request.getAttribute(CsrfToken.class.getName());
            if (token instanceof CsrfToken csrfToken) {
                return csrfToken;
            }
            if (token instanceof Supplier<?> supplier) {
                Object supplied = supplier.get();
                if (supplied instanceof CsrfToken csrfToken) {
                    return csrfToken;
                }
            }
            token = request.getAttribute("_csrf");
            if (token instanceof CsrfToken csrfToken) {
                return csrfToken;
            }
            if (token instanceof Supplier<?> supplier) {
                Object supplied = supplier.get();
                if (supplied instanceof CsrfToken csrfToken) {
                    return csrfToken;
                }
            }
            return null;
        }
    }
}
