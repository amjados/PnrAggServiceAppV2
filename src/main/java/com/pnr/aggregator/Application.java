package com.pnr.aggregator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * -@SpringBootApplication is a convenience annotation that combines:
 * =========
 * --@Configuration: Tags the class as a source of bean definitions
 * --WithoutIT: Spring won't recognize this class as a configuration source;
 * no [@Bean] methods would work.
 * =========
 * --@EnableAutoConfiguration: Enables Spring Boot's auto-configuration
 * mechanism
 * --WithoutIT: No automatic setup of Tomcat server, database connections, JPA,
 * security, etc. You'd need to manually configure every Spring feature.
 * =========
 * --@ComponentScan: Enables component scanning in this package and sub-packages
 * --WithoutIT: [@Controller], [@Service], [@Repository], [@Component] classes
 * won't be discovered. No dependency injection for your custom beans;
 * application context would be nearly empty.
 */
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
