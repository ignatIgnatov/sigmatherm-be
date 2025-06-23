package com.ludogoriesoft.sigmatherm.config;

import com.ludogoriesoft.sigmatherm.exception.JwtEntryPoint;
import com.ludogoriesoft.sigmatherm.filter.ApiKeyAuthFilter;
import com.ludogoriesoft.sigmatherm.filter.JwtAuthenticationFilter;
import com.ludogoriesoft.sigmatherm.filter.SkroutzIpFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Collections;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String SKROUTZ_WEBHOOK_URI = "/api/skroutz-orders";
    private static final String SKROUTZ_FEED_URL = "/api/skroutz/**";
    private static final String MAGENTO_URL = "/api/magento/**";

    @Value("${spring.cors.allowedOrigins}")
    private String[] allowedOrigins;

    @Value("${magento.api-key}")
    private String magentoApiKey;

    private final JwtEntryPoint jwtEntryPoint;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(skroutzIpFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(apiKeyAuthFilter(), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(
                        auth -> auth
                                .requestMatchers("/api/auth/**").permitAll()
                                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                                .requestMatchers(MAGENTO_URL).authenticated()
                                .requestMatchers(SKROUTZ_WEBHOOK_URI).permitAll()
                                .requestMatchers(SKROUTZ_FEED_URL).permitAll()
                                .anyRequest().authenticated())
                .exceptionHandling(
                        httpSecurityExceptionHandlingConfigurer ->
                                httpSecurityExceptionHandlingConfigurer.authenticationEntryPoint(
                                        jwtEntryPoint))
                .cors(cors -> cors.configurationSource(configurationSource()));

        return http.build();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter();
    }

    @Bean
    public SkroutzIpFilter skroutzIpFilter() {
        return new SkroutzIpFilter();
    }

    @Bean
    public ApiKeyAuthFilter apiKeyAuthFilter() {
        return new ApiKeyAuthFilter("X-API-KEY", magentoApiKey);
    }

    private CorsConfigurationSource configurationSource() {
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        corsConfiguration.setAllowedOrigins(List.of(allowedOrigins));
        corsConfiguration.setAllowedMethods(Collections.singletonList("*"));
        corsConfiguration.setAllowCredentials(true);
        corsConfiguration.setAllowedHeaders(Collections.singletonList("*"));
        corsConfiguration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", corsConfiguration);

        CorsConfiguration webhookConfig = new CorsConfiguration();
        webhookConfig.setAllowedOrigins(Collections.singletonList("*"));
        webhookConfig.setAllowedMethods(List.of("POST"));
        webhookConfig.setAllowedHeaders(List.of("*"));

        source.registerCorsConfiguration(SKROUTZ_WEBHOOK_URI, webhookConfig);

        return source;
    }
}
