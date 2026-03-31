package com.kiwi.project.bpm.utils;

import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.model.BpmComponentParameter;
import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.springframework.core.annotation.AnnotationUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class ComponentUtils
{

    public static  BpmComponent fromClass(Class activityBehaviorClass)
    {
        ComponentDescription componentDescription = AnnotationUtils.getAnnotation(activityBehaviorClass, ComponentDescription.class);


        if( componentDescription != null ) {
            BpmComponent bpmComponent = new BpmComponent();
            String name = componentDescription.name();
            String description = componentDescription.description();
            String version = componentDescription.version();
            bpmComponent.setName(name);
            bpmComponent.setDescription(description);
            bpmComponent.setVersion(version);
            bpmComponent.setType(BpmComponent.Type.SpringBean);
            bpmComponent.setGroup(componentDescription.group());

            ComponentParameter[] inputs = componentDescription.inputs();
            bpmComponent.setInputParameters(Optional.ofNullable(inputs)
                    .map(parameters -> {
                        return Arrays.stream(parameters).map(ComponentUtils::toComponentProperty).toList();
                    }).orElse(List.of()));

            ComponentParameter[] outputs = componentDescription.outputs();
            bpmComponent.setOutputParameters(Optional.ofNullable(outputs)
                    .map(parameters -> {
                        return Arrays.stream(parameters).map(p -> {
                            BpmComponentParameter bpmComponentParameter = toComponentProperty(p);
                            if( bpmComponentParameter.getDefaultValue() == null ) {
                                bpmComponentParameter.setDefaultValue(bpmComponentParameter.getKey());
                            }
                            return bpmComponentParameter;
                        }).toList();
                    }).orElse(List.of()));
            ExternalTaskSubscription taskSubscription = AnnotationUtils.getAnnotation(activityBehaviorClass, ExternalTaskSubscription.class);
            if( taskSubscription != null ) {
                String topicName = taskSubscription.topicName();
                bpmComponent.setKey(topicName);
                bpmComponent.setType(BpmComponent.Type.SpringExternalTask);
            }
            return bpmComponent;
        }
        return null;
    }

    public static BpmComponentParameter toComponentProperty(ComponentParameter parameter) {
        BpmComponentParameter bpmComponentParameter = new BpmComponentParameter();
        bpmComponentParameter.setExample(parameter.example());
        bpmComponentParameter.setKey(parameter.key());
        bpmComponentParameter.setName(parameter.name());
        bpmComponentParameter.setDescription(parameter.description());
        bpmComponentParameter.setHtmlType(parameter.htmlType());
        bpmComponentParameter.setRequired(parameter.required());
        bpmComponentParameter.setImportant(parameter.required() || parameter.important());
        bpmComponentParameter.setDefaultValue(parameter.schema().defaultValue());
        bpmComponentParameter.setType(parameter.type());
        return bpmComponentParameter;

    }
}
