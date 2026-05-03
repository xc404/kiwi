package com.kiwi.bpmn.external.utils;

import org.camunda.bpm.client.task.impl.ExternalTaskImpl;
import org.camunda.bpm.client.topic.impl.dto.FetchAndLockRequestDto;
import org.camunda.bpm.client.topic.impl.dto.TopicRequestDto;
import org.camunda.bpm.client.variable.impl.TypedValueField;
import org.camunda.bpm.engine.ExternalTaskService;
import org.camunda.bpm.engine.externaltask.ExternalTaskQueryBuilder;
import org.camunda.bpm.engine.externaltask.ExternalTaskQueryTopicBuilder;
import org.camunda.bpm.engine.externaltask.LockedExternalTask;

import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.type.ValueType;
import org.camunda.bpm.engine.variable.value.FileValue;
import org.camunda.bpm.engine.variable.value.SerializableValue;
import org.camunda.bpm.engine.variable.value.TypedValue;

import java.util.HashMap;
import java.util.Map;

import static java.lang.Boolean.TRUE;

public class DtoUtils
{

    public static <T> T copyProperties(Object source, Class<T> targetClass) {
        try {
            T target = targetClass.getDeclaredConstructor().newInstance();
            org.springframework.beans.BeanUtils.copyProperties(source, target);
            return target;
        } catch( Exception ex ) {
            throw new RuntimeException("Failed to copy properties from " + source.getClass() + " to " + targetClass, ex);
        }
    }

    public static ExternalTaskQueryBuilder buildQuery(ExternalTaskService externalTaskService, FetchAndLockRequestDto fetchExternalTasksDto) {
        ExternalTaskQueryBuilder fetchBuilder = externalTaskService
                .fetchAndLock(fetchExternalTasksDto.getMaxTasks(), fetchExternalTasksDto.getWorkerId(),
                        fetchExternalTasksDto.isUsePriority());

        if( fetchExternalTasksDto.getTopics() != null ) {
            for( TopicRequestDto topicDto : fetchExternalTasksDto.getTopics() ) {
                ExternalTaskQueryTopicBuilder topicFetchBuilder =
                        fetchBuilder.topic(topicDto.getTopicName(), topicDto.getLockDuration());

                if( topicDto.getBusinessKey() != null ) {
                    topicFetchBuilder = topicFetchBuilder.businessKey(topicDto.getBusinessKey());
                }

                if( topicDto.getProcessDefinitionId() != null ) {
                    topicFetchBuilder.processDefinitionId(topicDto.getProcessDefinitionId());
                }

                if( topicDto.getProcessDefinitionIdIn() != null ) {
                    topicFetchBuilder.processDefinitionIdIn(topicDto.getProcessDefinitionIdIn().toArray(new String[0]));
//                    topicFetchBuilder.processDefinitionIdIn(topicDto.getProcessDefinitionIdIn());
                }

                if( topicDto.getProcessDefinitionKey() != null ) {
                    topicFetchBuilder.processDefinitionKey(topicDto.getProcessDefinitionKey());
                }

                if( topicDto.getProcessDefinitionKeyIn() != null ) {
                    topicFetchBuilder.processDefinitionKeyIn(topicDto.getProcessDefinitionKeyIn().toArray(new String[0]));
                }

                if( topicDto.getVariables() != null ) {
                    topicFetchBuilder = topicFetchBuilder.variables(topicDto.getVariables());
                }

                if( topicDto.getProcessVariables() != null ) {
                    topicFetchBuilder = topicFetchBuilder.processInstanceVariableEquals(topicDto.getProcessVariables());
                }

//                if( topicDto.isDeserializeValues() ) {
//                    topicFetchBuilder = topicFetchBuilder.enableCustomObjectDeserialization();
//                }

                if( topicDto.isLocalVariables() ) {
                    topicFetchBuilder = topicFetchBuilder.localVariables();
                }

                if( TRUE.equals(topicDto.isWithoutTenantId()) ) {
                    topicFetchBuilder = topicFetchBuilder.withoutTenantId();
                }

                if( topicDto.getTenantIdIn() != null ) {
                    topicFetchBuilder = topicFetchBuilder.tenantIdIn(topicDto.getTenantIdIn().toArray(new String[0]));
                }

                if( topicDto.getProcessDefinitionVersionTag() != null ) {
                    topicFetchBuilder = topicFetchBuilder.processDefinitionVersionTag(topicDto.getProcessDefinitionVersionTag());
                }

                if( topicDto.isIncludeExtensionProperties() ) {
                    topicFetchBuilder = topicFetchBuilder.includeExtensionProperties();
                }

                fetchBuilder = topicFetchBuilder;
            }
        }

        return fetchBuilder;
    }


    public static ExternalTaskImpl fromLockedExternalTask(LockedExternalTask task) {
        ExternalTaskImpl dto = new ExternalTaskImpl();
        dto.setActivityId(task.getActivityId());
        dto.setActivityInstanceId(task.getActivityInstanceId());
        dto.setErrorMessage(task.getErrorMessage());
        dto.setErrorDetails(task.getErrorDetails());
        dto.setExecutionId(task.getExecutionId());
        dto.setId(task.getId());
        dto.setLockExpirationTime(task.getLockExpirationTime());
        dto.setProcessDefinitionId(task.getProcessDefinitionId());
        dto.setProcessDefinitionKey(task.getProcessDefinitionKey());
        dto.setProcessDefinitionVersionTag(task.getProcessDefinitionVersionTag());
        dto.setProcessInstanceId(task.getProcessInstanceId());
        dto.setRetries(task.getRetries());
        dto.setTopicName(task.getTopicName());
        dto.setWorkerId(task.getWorkerId());
        dto.setTenantId(task.getTenantId());

        dto.setVariables(fromMap(task.getVariables(),false));
        dto.setPriority(task.getPriority());
        dto.setBusinessKey(task.getBusinessKey());
        dto.setExtensionProperties(task.getExtensionProperties());

        return dto;
    }

    /**
     * 将引擎 {@link org.camunda.bpm.engine.externaltask.ExternalTask}（如 query 单条结果）转为 client {@link ExternalTaskImpl}，
     * 供 {@link com.kiwi.bpmn.external.retry.ExternalTaskRetryPlanner} 等仅依赖 client 模型的代码使用。
     */
    public static ExternalTaskImpl fromEngineExternalTask(org.camunda.bpm.engine.externaltask.ExternalTask task) {
        if (task == null) {
            return null;
        }
        ExternalTaskImpl dto = new ExternalTaskImpl();
        dto.setActivityId(task.getActivityId());
        dto.setActivityInstanceId(task.getActivityInstanceId());
        dto.setErrorMessage(task.getErrorMessage());
        dto.setErrorDetails(null);
        dto.setExecutionId(task.getExecutionId());
        dto.setId(task.getId());
        dto.setLockExpirationTime(task.getLockExpirationTime());
        dto.setProcessDefinitionId(task.getProcessDefinitionId());
        dto.setProcessDefinitionKey(task.getProcessDefinitionKey());
        dto.setProcessDefinitionVersionTag(task.getProcessDefinitionVersionTag());
        dto.setProcessInstanceId(task.getProcessInstanceId());
        dto.setRetries(task.getRetries());
        dto.setTopicName(task.getTopicName());
        dto.setWorkerId(task.getWorkerId());
        dto.setTenantId(task.getTenantId());
        dto.setVariables(new HashMap<>());
        dto.setPriority(task.getPriority());
        dto.setBusinessKey(task.getBusinessKey());
        dto.setExtensionProperties(task.getExtensionProperties());
        return dto;
    }

    public static Map<String, TypedValueField> fromMap(VariableMap variables, boolean preferSerializedValue)
    {
        Map<String, TypedValueField> result = new HashMap<>();
        for (String variableName : variables.keySet()) {
            TypedValueField valueDto = fromTypedValue(variables.getValueTyped(variableName), preferSerializedValue);
            result.put(variableName, valueDto);
        }

        return result;
    }

    public static TypedValueField fromTypedValue(TypedValue typedValue, boolean preferSerializedValue) {
        TypedValueField dto = new TypedValueField();
        fromTypedValue(dto, typedValue, preferSerializedValue);
        return dto;
    }

    public static void fromTypedValue(TypedValueField dto, TypedValue typedValue, boolean preferSerializedValue) {

        ValueType type = typedValue.getType();
        if (type != null) {
            String typeName = type.getName();
            dto.setType(toRestApiTypeName(typeName));
            dto.setValueInfo(type.getValueInfo(typedValue));
        }

        if(typedValue instanceof SerializableValue ) {
            SerializableValue serializableValue = (SerializableValue) typedValue;

            if(serializableValue.isDeserialized() && !preferSerializedValue) {
                dto.setValue(serializableValue.getValue());
            }
            else {
                dto.setValue(serializableValue.getValueSerialized());
            }

        }
        else if(typedValue instanceof FileValue ){
            //do not set the value for FileValues since we don't want to send megabytes over the network without explicit request
        }
        else {
            dto.setValue(typedValue.getValue());
        }

    }
    public static String toRestApiTypeName(String name) {
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }
}
