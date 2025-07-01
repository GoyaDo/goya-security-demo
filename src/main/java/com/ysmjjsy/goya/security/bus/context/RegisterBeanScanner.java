package com.ysmjjsy.goya.security.bus.context;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <p></p>
 *
 * @author goya
 * @since 2025/6/24 19:14
 */
@Slf4j
public class RegisterBeanScanner {


    private final AnnotationMetadata importingClassMetadata;
    private final Class<? extends Annotation> handlerClass;
    private final List<String> scannerPackageNames;

    public RegisterBeanScanner(AnnotationMetadata importingClassMetadata, Class<? extends Annotation> handlerClass) throws ClassNotFoundException {
        this.importingClassMetadata = importingClassMetadata;
        this.handlerClass = handlerClass;
        this.scannerPackageNames = new ArrayList<>();
        this.loadScannerPackages();
    }

    private void loadScannerPackages() throws ClassNotFoundException {
        final Map<String, Object> attributes = importingClassMetadata.getAnnotationAttributes(SpringBootApplication.class.getName());
        if (attributes == null) {
            return;
        }

        String defaultPackage = (Class.forName(importingClassMetadata.getClassName())).getPackage().getName();
        addPackage(defaultPackage);

        String[] scanBasePackages = (String[]) attributes.get("scanBasePackages");

        if (scanBasePackages != null) {
            for (String basePackages : scanBasePackages) {
                addPackage(basePackages);
            }
        }

        log.debug("scannerPackageNames:{}", scannerPackageNames);
    }


    public List<BeanDefinition> findBeanDefinitions() {
        ClassPathScanningCandidateComponentProvider pathScanningCandidateComponentProvider = new ClassPathScanningCandidateComponentProvider(false);
        
        // 添加包含过滤器：包含所有实现IEventListener的类
//        pathScanningCandidateComponentProvider.addIncludeFilter(new AssignableTypeFilter(handlerClass));
        pathScanningCandidateComponentProvider.addIncludeFilter(new AnnotationTypeFilter(handlerClass,true));

        // 添加排除过滤器：排除框架内部的包装器类
//        pathScanningCandidateComponentProvider.addExcludeFilter(new AssignableTypeFilter(MethodIEventListenerWrapper.class));

        List<BeanDefinition> candidateComponents = new ArrayList<>();
        for (String packageName : scannerPackageNames) {
            candidateComponents.addAll(pathScanningCandidateComponentProvider.findCandidateComponents(packageName));
        }
        
        log.debug("找到 {} 个候选的IEventListener实现", candidateComponents.size());
        for (BeanDefinition bd : candidateComponents) {
            log.debug("扫描到事件监听器: {}", bd.getBeanClassName());
        }
        
        return candidateComponents;
    }

    private String appendPackageChildren(String packageName) {
        if (!packageName.endsWith(".*") || !packageName.endsWith(".**")) {
            packageName = String.format("%s.**", packageName);
        }
        return packageName;
    }


    private void addPackage(String packageName) {
        if (packageName != null) {
            scannerPackageNames.add(appendPackageChildren(packageName));
        }
    }
}
