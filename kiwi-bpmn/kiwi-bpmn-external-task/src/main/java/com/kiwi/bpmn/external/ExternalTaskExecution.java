package com.kiwi.bpmn.external;

import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineServices;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.runtime.Incident;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.impl.VariableMapImpl;
import org.camunda.bpm.engine.variable.value.TypedValue;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowElement;

import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Set;

public class ExternalTaskExecution implements DelegateExecution
{
    private final ExternalTask externalTask;
    private final ExternalTaskService externalTaskService;
    private final VariableMap outputVariable = new VariableMapImpl();

    public ExternalTaskExecution(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        this.externalTask = externalTask;
        this.externalTaskService = externalTaskService;
    }


    public String getActivityId() {
        return externalTask.getActivityId();
    }

    public Map<String, String> getExtensionProperties() {
        return externalTask.getExtensionProperties();
    }

    public Map<String, Object> getAllVariables() {
        return externalTask.getAllVariables();
    }

    public String getTopicName() {
        return externalTask.getTopicName();
    }

    public Date getLockExpirationTime() {
        return externalTask.getLockExpirationTime();
    }

    public VariableMap getAllVariablesTyped() {
        return externalTask.getAllVariablesTyped();
    }

    public String getWorkerId() {
        return externalTask.getWorkerId();
    }

    public VariableMap getAllVariablesTyped(boolean deserializeObjectValues) {
        return externalTask.getAllVariablesTyped(deserializeObjectValues);
    }

    public Integer getRetries() {
        return externalTask.getRetries();
    }

    public String getExecutionId() {
        return externalTask.getExecutionId();
    }
//
//    public <T extends TypedValue> T getVariable(String variableName) {
//        return externalTask.getVariable(variableName);
//    }

    public String getErrorDetails() {
        return externalTask.getErrorDetails();
    }

    public String getProcessDefinitionVersionTag() {
        return externalTask.getProcessDefinitionVersionTag();
    }

    public String getExtensionProperty(String propertyKey) {
        return externalTask.getExtensionProperty(propertyKey);
    }

    public String getErrorMessage() {
        return externalTask.getErrorMessage();
    }

    public long getPriority() {
        return externalTask.getPriority();
    }

    public String getProcessDefinitionKey() {
        return externalTask.getProcessDefinitionKey();
    }

    @Override
    public String getProcessInstanceId() {
        return externalTask.getProcessInstanceId();
    }

    @Override
    public String getProcessBusinessKey() {
        return null;
    }

    @Override
    public void setProcessBusinessKey(String businessKey) {

    }

    @Override
    public String getProcessDefinitionId() {
        return externalTask.getProcessDefinitionId();
    }

    @Override
    public String getParentId() {
        return null;
    }

    @Override
    public String getCurrentActivityId() {
        return externalTask.getActivityId();
    }

    @Override
    public String getCurrentActivityName() {
        return externalTask.getTopicName();
    }

    @Override
    public String getActivityInstanceId() {
        return externalTask.getActivityInstanceId();
    }

    @Override
    public String getParentActivityInstanceId() {
        return null;
    }

    @Override
    public String getCurrentTransitionId() {
        return null;
    }

    @Override
    public DelegateExecution getProcessInstance() {
        return null;
    }

    @Override
    public DelegateExecution getSuperExecution() {
        return null;
    }

    @Override
    public boolean isCanceled() {
        return false;
    }

    @Override
    public String getTenantId() {
        return externalTask.getTenantId();
    }

    @Override
    public void setVariable(String variableName, Object value, String activityId) {
        externalTaskService.setVariables(this.externalTask, Map.of(variableName, value));
    }

    @Override
    public Incident createIncident(String incidentType, String configuration) {
        return null;
    }

    @Override
    public Incident createIncident(String incidentType, String configuration, String message) {
        return null;
    }

    @Override
    public void resolveIncident(String incidentId) {

    }

    @Override
    public String getId() {
        return externalTask.getExecutionId();
    }

    @Override
    public String getEventName() {
        return "";
    }

    @Override
    public String getBusinessKey() {
        return externalTask.getBusinessKey();
    }

    @Override
    public BpmnModelInstance getBpmnModelInstance() {
        return null;
    }

    @Override
    public FlowElement getBpmnModelElementInstance() {
        return null;
    }

    @Override
    public ProcessEngineServices getProcessEngineServices() {
        return null;
    }

    @Override
    public ProcessEngine getProcessEngine() {
        return null;
    }

    @Override
    public String getVariableScopeKey() {
        return "";
    }

    @Override
    public Map<String, Object> getVariables() {
        return externalTask.getAllVariables();
    }

    @Override
    public VariableMap getVariablesTyped() {
        return externalTask.getAllVariablesTyped();
    }

    @Override
    public VariableMap getVariablesTyped(boolean deserializeValues) {
       return externalTask.getAllVariablesTyped(deserializeValues);
    }

    @Override
    public Map<String, Object> getVariablesLocal() {
       return externalTask.getAllVariables();
    }

    @Override
    public VariableMap getVariablesLocalTyped() {
       return externalTask.getAllVariablesTyped();
    }

    @Override
    public VariableMap getVariablesLocalTyped(boolean deserializeValues) {
       return externalTask.getAllVariablesTyped(deserializeValues);
    }

    @Override
    public Object getVariable(String variableName) {
        return externalTask.getVariable(variableName);
    }

    @Override
    public Object getVariableLocal(String variableName) {
       return externalTask.getVariable(variableName);
    }

    @Override
    public <T extends TypedValue> T getVariableTyped(String variableName) {
       return externalTask.getVariableTyped(variableName);
    }

    @Override
    public <T extends TypedValue> T getVariableTyped(String variableName, boolean deserializeValue) {
        return externalTask.getVariableTyped(variableName, deserializeValue);
    }

    @Override
    public <T extends TypedValue> T getVariableLocalTyped(String variableName) {
       return externalTask.getVariableTyped(variableName);
    }

    @Override
    public <T extends TypedValue> T getVariableLocalTyped(String variableName, boolean deserializeValue) {
       return externalTask.getVariableTyped(variableName, deserializeValue);
    }

    @Override
    public Set<String> getVariableNames() {
        return externalTask.getAllVariables().keySet();
    }

    @Override
    public Set<String> getVariableNamesLocal() {
        return externalTask.getAllVariables().keySet();
    }

    @Override
    public void setVariable(String variableName, Object value) {
        this.outputVariable.put(variableName, value);
    }

    @Override
    public void setVariableLocal(String variableName, Object value) {
        this.outputVariable.put(variableName, value);
    }

    @Override
    public void setVariables(Map<String, ?> variables) {
        this.outputVariable.putAll(variables);
    }

    @Override
    public void setVariablesLocal(Map<String, ?> variables) {
        this.outputVariable.putAll(variables);
    }

    @Override
    public boolean hasVariables() {
        return true;
    }

    @Override
    public boolean hasVariablesLocal() {
        return true;
    }

    @Override
    public boolean hasVariable(String variableName) {
        return this.externalTask.getAllVariables().containsKey(variableName);
    }

    @Override
    public boolean hasVariableLocal(String variableName) {
        return this.externalTask.getAllVariables().containsKey(variableName);
    }

    @Override
    public void removeVariable(String variableName) {

    }

    @Override
    public void removeVariableLocal(String variableName) {

    }

    @Override
    public void removeVariables(Collection<String> variableNames) {

    }

    @Override
    public void removeVariablesLocal(Collection<String> variableNames) {

    }

    @Override
    public void removeVariables() {

    }

    @Override
    public void removeVariablesLocal() {

    }

    public VariableMap getOutputVariable() {
        return outputVariable;
    }

    public ExternalTask getExternalTask() {
        return externalTask;
    }
}
