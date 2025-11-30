package com.pnr.aggregator.config;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * -@Configuration: Marks this class as a Spring configuration class.
 * --Indicates this class contains [@Bean] definitions for the Spring container
 * --Processed by Spring to generate bean definitions and service requests
 * --Alternative to XML-based configuration
 * --WithoutIT: Spring won't recognize this as a configuration class;
 * -[@Bean] methods won't be processed, and Vert.x beans won't be available for
 * injection.
 */
@Configuration
public class VertxConfig {

    @Value("${vertx.worker-pool-size:40}")
    private int workerPoolSize;

    @Value("${vertx.event-loop-pool-size:4}")
    private int eventLoopPoolSize;

    /**
     * -@Autowired: Dependency injection for MongoDB properties.
     * --Injects MongoDbProperties configuration bean
     * --Contains MongoDB connection settings from application.properties
     * --WithoutIT: mongoDbProperties would be null;
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
     * --WithoutIT: Vert.x instance won't be available for dependency injection;
     * services trying to [@Autowire] Vertx will fail at startup.
     */
    @Bean
    public Vertx vertx() {
        VertxOptions options = new VertxOptions()
                .setWorkerPoolSize(workerPoolSize)
                .setEventLoopPoolSize(eventLoopPoolSize);
        // This creates a Vert.x instance using those custom configurations.
        // Starts Vert.x runtime (“Start the car engine.”)
        /*
         * This project doesn't use vertx.deployVerticle() because it's integrating
         * Vert.x into a Spring Boot application rather than creating a standalone
         * Vert.x application.
         * Key reasons:
         * Spring manages the lifecycle - Spring Boot's dependency injection container
         * handles component initialization and lifecycle, not Vert.x verticles
         * Direct bean injection - Components like BookingController and services are
         * Spring beans that get the Vert.x instance injected directly via [@Autowired],
         * so there's no need to deploy them as verticles
         * Hybrid architecture - This is a Spring-first application that uses Vert.x as
         * a library for its async capabilities (EventBus, non-blocking MongoDB client),
         * not as a framework
         * No verticle pattern needed - Verticles are self-contained deployment units
         * with their own lifecycle. Here, Spring controllers and services already
         * provide that structure.
         */
        return Vertx.vertx(options);
    }

    /**
     * -@Bean: Provides Vert.x EventBus as a Spring bean.
     * --EventBus enables publish-subscribe messaging between components
     * --Method parameter (Vertx vertx) is automatically injected by Spring
     * --Used for real-time event broadcasting (e.g., PNR fetch notifications)
     * --WithoutIT: EventBus won't be available for injection;
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
     * --WithoutIT: Services won't have access to MongoDB;
     * all database queries would fail, breaking the entire application.
     */
    @Bean
    public MongoClient mongoClient(Vertx vertx) {
        JsonObject config = new JsonObject()
                .put("host", mongoDbProperties.getHost())
                .put("port", mongoDbProperties.getPort())
                .put("db_name", mongoDbProperties.getDatabase())
                // Timeout settings from application.yml
                .put("connectTimeoutMS", mongoDbProperties.getConnectTimeoutMS())
                .put("socketTimeoutMS", mongoDbProperties.getSocketTimeoutMS())
                .put("serverSelectionTimeoutMS", mongoDbProperties.getServerSelectionTimeoutMS());

        return MongoClient.createShared(vertx, config);
    }
}
