package com.meesho.library.config;

import com.meesho.library.entity.Book;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Configuration for talking to Redis.
 *
 * <p>{@code @Configuration} marks this as a source of bean definitions. Every
 * method annotated {@code @Bean} is invoked once by Spring at startup, and its
 * return value is registered as a singleton bean that other components can inject.
 *
 * <p>We define a customized {@link RedisTemplate}. RedisTemplate is the high-level
 * client we use to read/write the cache (GET/SET against Redis). The reason we
 * configure it by hand instead of using the auto-configured default: the default
 * serializes values with Java binary serialization, which is unreadable. We want:
 * <ul>
 *   <li><b>keys</b> as plain strings  -> e.g. the key literally reads {@code book:1}</li>
 *   <li><b>values</b> as JSON         -> e.g. {@code {"@class":"...Book","id":1,"title":"..."}}</li>
 * </ul>
 * so you can run {@code redis-cli KEYS '*'} and {@code GET book:1} and actually
 * read what is cached while you study the cache hit/miss flow.
 */
@Configuration
public class RedisConfig {

    /**
     * @param connectionFactory the RedisConnectionFactory Spring Boot auto-configured
     *                          from spring.data.redis.* (host/port). Spring injects it
     *                          into this @Bean method as a parameter.
     */
    @Bean
    public RedisTemplate<String, Book> bookRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Book> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // How to turn the KEY (a String like "book:1") into bytes Redis stores.
        template.setKeySerializer(new StringRedisSerializer());

        // How to turn the VALUE (a Book object) into bytes: JSON. The serializer
        // embeds the Java type (@class) so it can rebuild the exact Book on read.
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());

        return template;
    }
}
