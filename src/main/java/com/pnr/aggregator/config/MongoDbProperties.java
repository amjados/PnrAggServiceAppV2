package com.pnr.aggregator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MongoDB Configuration Properties
 * 
 * WHY: Binds 'spring.data.mongodb.*' properties from application.yml to a type-safe
 * USE CASE: Provides MongoDB connection details for Vert.x MongoClient
 * 
 * PROPERTIES:
 * - spring.data.mongodb.host: MongoDB server hostname
 * - spring.data.mongodb.port: MongoDB server port
 * - spring.data.mongodb.database: Database name
 */
/**
 * -@Data: Lombok annotation that generates boilerplate code.
 * --Automatically creates getters for all fields (getHost(), getPort(),
 * getDatabase())
 * --Automatically creates setters for all fields (setHost(), setPort(),
 * setDatabase())
 * --Generates equals(), hashCode(), and toString() methods
 * --Reduces code verbosity by eliminating getter/setter boilerplate
 * =========
 * -@Component: Registers this class as a Spring component.
 * --WithoutIT: This class won't be a Spring bean.
 * =========
 * -@Autowired injection of MongoDbProperties would fail.
 * --Makes this class a Spring-managed bean
 * --Enables [@Autowired] injection in other classes
 * --Discovered during component scanning
 * =========
 * -@ConfigurationProperties: Binds external configuration to this.
 * class.
 * --Maps properties with prefix "spring.data.mongodb" to class
 * fields.
 * --It ONLY reads property values from application.yml / application.properties
 * at startup.
 * It is always 100% synchronous.
 * It is not blocking I/O.
 * It is not reactive.
 * It is just configuration binding.
 * --Provides type-safe configuration instead of using [@Value] for
 * each property.
 * --Values can come from application.yml, application.properties, or
 * environment variables.
 * --WithoutIT: Properties from application.yml won't be loaded; MongoDB
 * connection would use only hardcoded defaults.
 */
@Data
@Component
@ConfigurationProperties(prefix = "spring.data.mongodb")
public class MongoDbProperties {

    private String host = "localhost";
    private int port = 27017;
    private String database = "pnr_db";
}
