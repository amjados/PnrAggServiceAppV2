package com.pnr.aggregator.config;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * -@Configuration: Marks this class as a Spring configuration class.
 * --Indicates this class contains [@Bean] definitions for the Spring container
 * --Processed by Spring to generate bean definitions and service requests
 * --Alternative to XML-based configuration
 * ----WithoutIT:: Spring won't recognize this as a configuration class;
 * -@Bean methods won't be processed, and Vert.x beans won't be available for
 * injection.
 * =========
 * Hardcoded in class: Worker pool size (40), event loop size (4), MongoDB
 * client config
 * Why: Vert.x-specific programmatic configuration, no Spring Boot auto-config
 * for Vert.x
 */
@Configuration
public class VertxConfig {

    /**
     * -@Autowired: Dependency injection for MongoDB properties.
     * --Injects MongoDbProperties configuration bean
     * --Contains MongoDB connection settings from application.properties
     * ----WithoutIT:: mongoDbProperties would be null;
     * MongoDB connection would fail with NullPointerException.
     */
    @Autowired
    private MongoDbProperties mongoDbProperties;

    /**
     * -@Bean: Declares a Spring bean to be managed by the container.
     * --Method return value is registered as a bean in the application context
     * --Bean name defaults to method name ("vertx")
     * --This Vert.x instance can be [@Autowired] into other components
     * --Configured with custom worker pool and event loop sizes
     * ----WithoutIT:: Vert.x instance won't be available for dependency injection;
     * services trying to [@Autowire] Vertx will fail at startup.
     */
    @Bean
    public Vertx vertx() {
        VertxOptions options = new VertxOptions()
                .setWorkerPoolSize(40)
                .setEventLoopPoolSize(4);

        return Vertx.vertx(options);
    }

    /**
     * -@Bean: Provides Vert.x EventBus as a Spring bean.
     * --EventBus enables publish-subscribe messaging between components
     * --Method parameter (Vertx vertx) is automatically injected by Spring
     * --Used for real-time event broadcasting (e.g., PNR fetch notifications)
     * ----WithoutIT:: EventBus won't be available for injection;
     * real-time event broadcasting and WebSocket notifications would fail.
     */
    @Bean
    public EventBus eventBus(Vertx vertx) {
        return vertx.eventBus();
    }

    /**
     * -@Bean: Configures Vert.x MongoClient as a Spring bean.
     * --Provides non-blocking, asynchronous MongoDB client
     * --Configured with connection details from MongoDbProperties
     * --Shared client instance improves connection pooling and performance
     * ----WithoutIT:: Services won't have access to MongoDB;
     * all database queries would fail, breaking the entire application.
     */
    @Bean
    public MongoClient mongoClient(Vertx vertx) {
        JsonObject config = new JsonObject()
                .put("host", mongoDbProperties.getHost())
                .put("port", mongoDbProperties.getPort())
                .put("db_name", mongoDbProperties.getDatabase());

        return MongoClient.createShared(vertx, config);
    }
}
