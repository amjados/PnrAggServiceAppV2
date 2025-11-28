package com.pnr.aggregator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MongoDB Configuration Properties
 * 
 * WHY: Binds 'spring.data.mongodb.*' properties from application.yml to a
 * type-safe
 * Java class
 * USE CASE: Provides MongoDB connection details for Vert.x MongoClient
 * 
 * PROPERTIES:
 * - spring.data.mongodb.host: MongoDB server hostname
 * - spring.data.mongodb.port: MongoDB server port
 * - spring.data.mongodb.database: Database name
 * 
 * USAGE: Inject this bean anywhere you need MongoDB connection details
 */
/**
 * @Data: Lombok annotation that generates boilerplate code
 * - Automatically creates getters for all fields (getHost(), getPort(), getDatabase())
 * - Automatically creates setters for all fields (setHost(), setPort(), setDatabase())
 * - Generates equals(), hashCode(), and toString() methods
 * - Reduces code verbosity by eliminating getter/setter boilerplate
 */
@Data
/**
 * @Component: Registers this class as a Spring component
 * - Makes this class a Spring-managed bean
 * - Enables @Autowired injection in other classes
 * - Discovered during component scanning
 */
@Component
/**
 * @ConfigurationProperties: Binds external configuration to this class
 * - Maps properties with prefix "spring.data.mongodb" to class fields
 * - Example: spring.data.mongodb.host -> setHost() is called
 * - Provides type-safe configuration instead of using @Value for each property
 * - Values can come from application.yml, application.properties, or environment variables
 */
@ConfigurationProperties(prefix = "spring.data.mongodb")
public class MongoDbProperties {

    private String host = "localhost";
    private int port = 27017;
    private String database = "pnr_db";
}
