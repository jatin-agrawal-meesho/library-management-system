package com.meesho.library;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The single entry point of the entire application.
 *
 * <p>{@code @SpringBootApplication} is a convenience annotation that combines three:
 * <ol>
 *   <li>{@code @SpringBootConfiguration} - marks this class as a source of bean definitions.</li>
 *   <li>{@code @EnableAutoConfiguration} - turns on Spring Boot's auto-configuration. It inspects
 *       the classpath and configures sensible defaults; because spring-boot-starter-web is present,
 *       it auto-configures and starts an embedded Tomcat web server.</li>
 *   <li>{@code @ComponentScan} - scans THIS package ({@code com.meesho.library}) and every
 *       sub-package for Spring components ({@code @RestController}, {@code @Service},
 *       {@code @Repository}, ...) and registers them as beans. This is why classes we add later
 *       only need to live under this package to be discovered automatically.</li>
 * </ol>
 */
@SpringBootApplication
public class LibraryManagementApplication {

    public static void main(String[] args) {
        // Boots the Spring "application context": runs auto-configuration, performs component
        // scanning, wires up beans (dependency injection), and starts the embedded web server.
        SpringApplication.run(LibraryManagementApplication.class, args);
    }
}
