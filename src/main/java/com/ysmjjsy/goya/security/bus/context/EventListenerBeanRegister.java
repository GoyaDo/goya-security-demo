package com.ysmjjsy.goya.security.bus.context;

import com.ysmjjsy.goya.security.bus.annotation.IListener;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

import java.util.List;

/**
 * <p></p>
 *
 * @author goya
 * @since 2025/6/24 19:14
 */
@Slf4j
@Configuration
public class EventListenerBeanRegister implements ImportBeanDefinitionRegistrar {

    @SneakyThrows
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        RegisterBeanScanner registerBeanScanner = new RegisterBeanScanner(importingClassMetadata, IListener.class);
        List<BeanDefinition> beanDefinitions = registerBeanScanner.findBeanDefinitions();

        //register Bean
        for (BeanDefinition candidateComponent : beanDefinitions) {
            String beanName = candidateComponent.getBeanClassName();
            registry.registerBeanDefinition(beanName, candidateComponent);
        }
    }
}
