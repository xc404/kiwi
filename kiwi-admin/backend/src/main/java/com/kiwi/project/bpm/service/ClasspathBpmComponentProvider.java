package com.kiwi.project.bpm.service;

import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.utils.ComponentUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.impl.pvm.delegate.ActivityBehavior;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
@Service
public class ClasspathBpmComponentProvider implements BpmComponentProvider, InitializingBean, ApplicationContextAware
{

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
            return this.parseComponent(entry.getKey(),entry.getValue());
        }).filter(Objects::nonNull).toList());
        bpmComponents.addAll(applicationContext.getBeansOfType(JavaDelegate.class).entrySet().stream().map(entry -> {
            if( entry.getValue() instanceof ExternalTaskHandler ){
                return null;
            }
            return this.parseComponent(entry.getKey(),entry.getValue());
        }).filter(Objects::nonNull).toList());

        bpmComponents.addAll(applicationContext.getBeansOfType(ExternalTaskHandler.class).entrySet().stream().map(entry -> {
            return this.parseComponent(entry.getKey(),entry.getValue());
        }).filter(Objects::nonNull).toList());
        this.bpmComponents = bpmComponents;

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
