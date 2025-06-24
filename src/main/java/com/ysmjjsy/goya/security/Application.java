package com.ysmjjsy.goya.security;

import com.ysmjjsy.goya.security.bus.context.EventListenerBeanRegister;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * <p></p>
 *
 * @author goya
 * @since 2025/6/24 09:05
 */
@SpringBootApplication
@Import(EventListenerBeanRegister.class)
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
