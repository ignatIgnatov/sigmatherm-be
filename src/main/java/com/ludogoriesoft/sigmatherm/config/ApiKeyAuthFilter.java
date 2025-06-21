package com.ludogoriesoft.sigmatherm.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final String apiKeyHeaderName;
    private final String validApiKey;
    private static final String MAGENTO_URL = "/api/magento";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String apiKey = request.getHeader(apiKeyHeaderName);

        if (request.getRequestURI().startsWith(MAGENTO_URL)) {
            if (apiKey == null || !apiKey.equals(validApiKey)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("Invalid API Key");
                return;
            }

            var auth = new UsernamePasswordAuthenticationToken(
                    "magentoClient",
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_MAGENTO"))
            );
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}

