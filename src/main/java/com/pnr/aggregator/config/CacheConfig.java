package com.pnr.aggregator.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
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
 * @Configuration: Indicates this class contains Spring bean definitions
 * @EnableCaching: Enables Spring's annotation-driven cache management capability
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * @Bean: Marks this method as a bean producer - Spring will manage the returned object
     * Configures Redis as the cache manager with the following settings:
     * - Cache entries expire after 10 minutes
     * - Keys are serialized as strings
     * - Values are serialized as JSON using Jackson
     * - Null values are not cached
     * - Cache operations participate in ongoing transactions
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Configure Redis cache with TTL and serialization settings
        RedisCacheConfiguration cacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10)) // Cache entries expire after 10 minutes
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues(); // Prevent caching of null values

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(cacheConfiguration)
                .transactionAware() // Make cache operations transaction-aware
                .build();
    }
}
