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
 * @Configuration: Marks this class as a Spring configuration class
 * - Indicates this class contains @Bean definitions for the Spring container
 * - Processed by Spring to generate bean definitions and service requests
 * - Alternative to XML-based configuration
 */
@Configuration
public class VertxConfig {

    /**
     * @Autowired: Dependency injection for MongoDB properties
     * - Injects MongoDbProperties configuration bean
     * - Contains MongoDB connection settings from application.properties
     */
    @Autowired
    private MongoDbProperties mongoDbProperties;

    /**
     * @Bean: Declares a Spring bean to be managed by the container
     * - Method return value is registered as a bean in the application context
     * - Bean name defaults to method name ("vertx")
     * - This Vert.x instance can be @Autowired into other components
     * - Configured with custom worker pool and event loop sizes
     */
    @Bean
    public Vertx vertx() {
        VertxOptions options = new VertxOptions()
                .setWorkerPoolSize(40)
                .setEventLoopPoolSize(4);

        return Vertx.vertx(options);
    }

    /**
     * @Bean: Provides Vert.x EventBus as a Spring bean
     * - EventBus enables publish-subscribe messaging between components
     * - Method parameter (Vertx vertx) is automatically injected by Spring
     * - Used for real-time event broadcasting (e.g., PNR fetch notifications)
     */
    @Bean
    public EventBus eventBus(Vertx vertx) {
        return vertx.eventBus();
    }

    /**
     * @Bean: Configures Vert.x MongoClient as a Spring bean
     * - Provides non-blocking, asynchronous MongoDB client
     * - Configured with connection details from MongoDbProperties
     * - Shared client instance improves connection pooling and performance
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
