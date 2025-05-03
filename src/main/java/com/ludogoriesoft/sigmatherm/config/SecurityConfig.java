package com.ludogoriesoft.sigmatherm.config;

import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

  private static final String SKROUTZ_WEBHOOK_URI = "/skroutz-orders";

  @Value("${spring.cors.allowedOrigins}")
  private String[] allowedOrigins;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .addFilterBefore(skroutzIpFilter(), UsernamePasswordAuthenticationFilter.class)
        .authorizeHttpRequests(
            auth -> auth.requestMatchers(SKROUTZ_WEBHOOK_URI).permitAll().anyRequest().permitAll())
        .cors(cors -> cors.configurationSource(configurationSource()));

    return http.build();
  }

  @Bean
  public SkroutzIpFilter skroutzIpFilter() {
    return new SkroutzIpFilter();
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
