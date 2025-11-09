package ru.aikr.inet.parser.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.lang.NonNull;

@Configuration
public class WebConfig implements WebFluxConfigurer {
    
    @Value("${app.cors.allowed-origins:*}")
    private String allowedOriginsConfig;
    
    @Value("${app.cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS}")
    private String allowedMethodsConfig;
    
    @Value("${app.cors.allowed-headers:*}")
    private String allowedHeadersConfig;
    
    @Value("${app.cors.allow-credentials:true}")
    private boolean allowCredentials;
    
    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        var corsRegistry = registry.addMapping("/api/**");
        
        // Настройка origins
        if ("*".equals(allowedOriginsConfig)) {
            corsRegistry.allowedOriginPatterns("*");
        } else {
            corsRegistry.allowedOrigins(StringUtils.commaDelimitedListToStringArray(allowedOriginsConfig));
        }
        
        // Настройка methods
        if ("*".equals(allowedMethodsConfig)) {
            corsRegistry.allowedMethods("*");
        } else {
            corsRegistry.allowedMethods(StringUtils.commaDelimitedListToStringArray(allowedMethodsConfig));
        }
        
        // Настройка headers
        if ("*".equals(allowedHeadersConfig)) {
            corsRegistry.allowedHeaders("*");
        } else {
            corsRegistry.allowedHeaders(StringUtils.commaDelimitedListToStringArray(allowedHeadersConfig));
        }
        
        corsRegistry.allowCredentials(allowCredentials);
    }
}
