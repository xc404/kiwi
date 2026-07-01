package com.kiwi.project.bpm.service;

import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.utils.ComponentUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.operaton.bpm.client.task.ExternalTaskHandler;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.operaton.bpm.engine.impl.pvm.delegate.ActivityBehavior;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
@Service
@DependsOn("bpmComponentPluginLoader")
public class ClasspathBpmComponentProvider implements BpmComponentProvider, InitializingBean, ApplicationContextAware
{

    private final BpmComponentPluginLoader pluginLoader;

    private List<BpmComponent> bpmComponents;
    private ApplicationContext applicationContext;

    public List<BpmComponent> getComponents() {
        return bpmComponents;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        loadComponents();
    }

    private void loadComponents() {
        List<BpmComponent> bpmComponents = new ArrayList<>();

        bpmComponents.addAll(applicationContext.getBeansOfType(ActivityBehavior.class).entrySet().stream().map(entry -> {
            if( entry.getValue() instanceof ExternalTaskHandler ){
                return null;
            }
            if (!isClasspathBean(entry.getKey(), entry.getValue())) {
                return null;
            }
            return this.parseComponent(entry.getKey(),entry.getValue());
        }).filter(Objects::nonNull).toList());
        bpmComponents.addAll(applicationContext.getBeansOfType(JavaDelegate.class).entrySet().stream().map(entry -> {
            if( entry.getValue() instanceof ExternalTaskHandler ){
                return null;
            }
            if (!isClasspathBean(entry.getKey(), entry.getValue())) {
                return null;
            }
            return this.parseComponent(entry.getKey(),entry.getValue());
        }).filter(Objects::nonNull).toList());

        bpmComponents.addAll(applicationContext.getBeansOfType(ExternalTaskHandler.class).entrySet().stream().map(entry -> {
            if (!isClasspathBean(entry.getKey(), entry.getValue())) {
                return null;
            }
            return this.parseComponent(entry.getKey(),entry.getValue());
        }).filter(Objects::nonNull).toList());
        this.bpmComponents = bpmComponents;

    }

    /**
     * 仅收录主 ClassLoader（如 Slurm）上的 Bean；插件 JAR 经 {@link BpmComponentPluginLoader} 注册的实例由
     * {@link PluginBpmComponentProvider} 产出 {@code plugin_*} 元数据。
     */
    private boolean isClasspathBean(String beanName, Object bean) {
        if (pluginLoader.isPluginRegisteredBean(beanName)) {
            return false;
        }
        ClassLoader appCl = applicationContext.getClassLoader();
        ClassLoader beanCl = bean.getClass().getClassLoader();
        return beanCl == null || beanCl == appCl;
    }

    private BpmComponent parseComponent(String beanName, Object bean) {
        Class<?> activityBehaviorClass = bean.getClass();
        BpmComponent bpmComponent = ComponentUtils.fromClass(activityBehaviorClass);
        if(bpmComponent != null){
            if( StringUtils.isBlank(bpmComponent.getKey())){
                bpmComponent.setKey(beanName);
            }
            bpmComponent.setSource(getSource());
        }
        return bpmComponent;
    }

    public String getSource() {
        return "classpath";
    }


    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
