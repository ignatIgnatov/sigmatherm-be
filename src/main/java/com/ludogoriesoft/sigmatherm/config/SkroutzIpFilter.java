package com.ludogoriesoft.sigmatherm.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import org.springframework.web.filter.OncePerRequestFilter;

public class SkroutzIpFilter extends OncePerRequestFilter {

  private static final Logger logger = Logger.getLogger(SkroutzIpFilter.class.getName());

  private static final String REQUEST_URI = "/api/skroutz-orders";
  private static final List<String> ALLOWED_IP_PREFIXES =
      Arrays.asList("185.6.76.", "185.6.77.", "185.6.78.", "185.6.79.", "2a03:e40:");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    if (request.getRequestURI().equals(REQUEST_URI)) {
      String remoteIp = getClientIp(request);

      // Check if IP is from Skroutz
      boolean isFromSkroutz = ALLOWED_IP_PREFIXES.stream().anyMatch(remoteIp::startsWith);

      if (!isFromSkroutz) {
        logger.warning("Blocked request from unauthorized IP: " + remoteIp);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.getWriter().write("Access denied: Request not from Skroutz IP range");
        return;
      }

      logger.info("Allowed request from Skroutz IP: " + remoteIp);
    }

    filterChain.doFilter(request, response);
  }

  /** Gets the client IP address from the request, handling proxies */
  private String getClientIp(HttpServletRequest request) {
    String ip = request.getHeader("X-Forwarded-For");

    if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
      ip = request.getHeader("Proxy-Client-IP");
    }
    if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
      ip = request.getHeader("WL-Proxy-Client-IP");
    }
    if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
      ip = request.getHeader("HTTP_CLIENT_IP");
    }
    if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
      ip = request.getHeader("HTTP_X_FORWARDED_FOR");
    }
    if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
      ip = request.getRemoteAddr();
    }

    // If multiple IPs are provided, take the first one (client's IP)
    if (ip != null && ip.contains(",")) {
      ip = ip.split(",")[0].trim();
    }

    return ip;
  }
}
