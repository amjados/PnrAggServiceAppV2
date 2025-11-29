package com.pnr.aggregator.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web Configuration
 * WHY: Enables CORS (Cross-Origin Resource Sharing) for web clients
 * USE CASE: Allows Swagger UI, test HTML files, and browser clients to access API
 */
/**
 * -@Configuration: Marks this class as a Spring configuration class.
 * --Indicates this class contains configuration methods for the application
 * --Enables Spring to process this class and apply web configurations
 * --WithoutIT: Spring won't recognize this as a configuration class; CORS
 * ---settings won't be applied, and browsers will block cross-origin requests.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                // Allow various origins
                .allowedOrigins(
                        "http://localhost:8081",
                        "http://127.0.0.1:8081",
                        "http://pnr-swagger-ui:8080" // Docker internal network
                )
                // Allow file:// protocol and null origin for local HTML files
                .allowedOriginPatterns("file://*", "null")
                // Allow all HTTP methods
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD")
                // Allow all headers
                .allowedHeaders("*")
                // Allow credentials (cookies, authorization headers)
                .allowCredentials(true)
                // Cache preflight response for 1 hour
                .maxAge(3600);
    }
}
