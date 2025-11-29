package com.pnr.aggregator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * -@Configuration: Indicates this class contains Spring bean definitions.
 * --WithoutIT: Spring won't process [@Bean] methods;
 * ---cache manager won't be configured, breaking caching functionality.
 * =========
 * -@EnableCaching: Enables Spring's annotation-driven cache management.
 * --capability.
 * --WithoutIT: All cache operations [@Cacheable, @CachePut, or @CacheEvict]
 * would be no-ops;
 * ---fallback caching for circuit breakers wouldn't work.
 * =========
 * [@EnableCaching] is NOT used in this configuration.
 * 
 * Reason: All caching is done programmatically using cacheManager.getCache()
 * and cache.put() directly in service classes. No Spring cache annotations
 * ([@Cacheable], [@CachePut], [@CacheEvict]) are used.
 * 
 * [@EnableCaching] is only needed when Spring intercepts methods decorated with
 * cache annotations. Since we're manually managing the cache through the
 * CacheManager API for explicit control, this annotation is unnecessary.
 */
@Configuration
public class CacheConfig {

        @Value("${spring.cache.redis.time-to-live:600000}")
        private long cacheTtlMinutes;

        /**
         * -@Bean: Marks this method as a bean producer - Spring will manage the.
         * --returned object.
         * --Configures Redis as the cache manager with the following settings:
         * ---Cache entries expire after 10 minutes
         * ---Keys are serialized as strings
         * ---Values are serialized as JSON using Jackson
         * ---Null values are not cached
         * ---Cache operations participate in ongoing transactions
         * --WithoutIT: No cache manager available;
         * ---fallback data storage for circuit breakers would fail.
         */
        @Bean
        public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
                // Configure Redis cache with TTL and serialization settings
                RedisCacheConfiguration cacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofMillis(cacheTtlMinutes)) // Cache TTL from application.yml
                                /*
                                 * When using Redis as a cache in Spring Boot, everything stored in
                                 * Redis must
                                 * be serialized (converted) into a format Redis can save.
                                 * Spring Boot lets you choose how keys and values are serialized.
                                 */
                                .serializeKeysWith(
                                                RedisSerializationContext.SerializationPair
                                                                .fromSerializer(new StringRedisSerializer()))
                                /*
                                 * Keys will be stored as plain human-readable strings in Redis.
                                 * Easy to debug using Redis CLI.
                                 * Keys look clean and readable.
                                 * Works with normal string keys in Spring Cache.
                                 */
                                .serializeValuesWith(RedisSerializationContext.SerializationPair
                                                .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                                /*
                                 * Values are stored as JSON in Redis.
                                 * It uses Jackson under the hood to serialize/deserialize objects.
                                 * Human-readable JSON values
                                 * Supports ANY Java object without extra config
                                 * Avoids Java’s default binary serialization (slow + unsafe)
                                 */
                                .disableCachingNullValues(); // Prevent caching of null values

                return RedisCacheManager.builder(connectionFactory)
                                .cacheDefaults(cacheConfiguration)
                                .transactionAware() // Make cache operations transaction-aware
                                .build();
        }
}
