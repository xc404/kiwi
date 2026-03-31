package com.kiwi.project.bpm.ctl;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.kiwi.framework.ctl.BaseCtl;
import com.kiwi.common.query.QueryField;
import com.kiwi.common.query.QueryParams;
import com.kiwi.project.bpm.dao.BpmProcessDefinitionDao;
import com.kiwi.project.bpm.model.BpmProcess;
import com.kiwi.project.bpm.service.BpmProcessDefinitionService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.repository.DeploymentBuilder;
import org.camunda.bpm.engine.repository.DeploymentWithDefinitions;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.rest.dto.runtime.ProcessInstanceDto;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Date;

import static com.kiwi.project.bpm.service.BpmProcessDefinitionService.getNewProcessId;
import static com.kiwi.project.bpm.service.BpmProcessDefinitionService.updateIdAndName;


@SaCheckLogin
@Controller
@RequiredArgsConstructor
@RequestMapping("/bpm/process")
public class BpmProcessDefinitionCtl extends BaseCtl
{


    private final BpmProcessDefinitionService bpmProcessDefinitionService;
    private final BpmProcessDefinitionDao bpmProcessDefinitionDao;
    private final ProcessEngine processEngine;


    @Data
    public static class CreateInput
    {
        private String name;
        private String projectId;
    }

    @Data
    public static class QueryInput
    {
        @QueryField(condition = QueryField.Type.EQ, value = "projectId")
        private String projectId;
    }

    @Data
    public static class CloneInput
    {
        private String name;
    }

    @Data
    public static class SaveInput
    {
        private String name;
        private String bpmnXml;
    }



    @GetMapping()
    @ResponseBody
    public Page<BpmProcess> page(QueryInput queryInput, Pageable pageable) {
        return this.bpmProcessDefinitionDao.findBy(QueryParams.of(queryInput), pageable);
    }


    @GetMapping("{id}")
    @ResponseBody
    public BpmProcess getProcessDefinition(@PathVariable String id) {
        return this.bpmProcessDefinitionDao.findById(id).orElseThrow();
    }


    @PostMapping()
    @ResponseBody
    public BpmProcess createProcessDefinition(@RequestBody CreateInput createInput) {
        BpmProcess bpmProcess = this.bpmProcessDefinitionService.createProcessDefinition(createInput.name);
        bpmProcess.setProjectId(createInput.projectId);
        this.bpmProcessDefinitionDao.insert(bpmProcess);
        return bpmProcess;
    }


    @PutMapping("{id}")
    @ResponseBody
    public BpmProcess saveProcessDefinition(@PathVariable String id, @RequestBody SaveInput saveInput) {
        BpmProcess bpmProcess = this.bpmProcessDefinitionDao.findById(id).orElseThrow();

        if( saveInput.name != null ) {
            bpmProcess.setName(saveInput.name);
            updateIdAndName(bpmProcess);
        }
        if( saveInput.bpmnXml != null ) {
            bpmProcess.setBpmnXml(saveInput.bpmnXml);
            if( bpmProcess.getDeployedVersion() > 0 ) {
                bpmProcess.setVersion(bpmProcess.getDeployedVersion() + 1);
            }
        }

        this.bpmProcessDefinitionDao.updateSelective(bpmProcess);
        return bpmProcess;
    }

    @PostMapping("{id}/saveAs")
    @ResponseBody
    public BpmProcess saveAsProcessDefinition(@PathVariable String id, @RequestBody SaveInput saveInput) {
        BpmProcess src = this.bpmProcessDefinitionDao.findById(id).orElseThrow();
        BpmProcess bpmProcess = new BpmProcess();
        bpmProcess.setId(getNewProcessId());
        bpmProcess.setName(saveInput.name);
        bpmProcess.setBpmnXml(saveInput.bpmnXml);
        updateIdAndName(bpmProcess);
        bpmProcess.setCreatedBy(getCurrentUserId());
        bpmProcess.setCreatedTime(new Date());
        return this.bpmProcessDefinitionDao.save(bpmProcess);
    }


    @PostMapping("{id}/clone")
    @ResponseBody
    public BpmProcess cloneProcessDefinition(@PathVariable String id, @RequestBody CloneInput cloneInput) {
        BpmProcess src = this.bpmProcessDefinitionDao.findById(id).orElseThrow();
        BpmProcess bpmProcess = new BpmProcess();
        bpmProcess.setId(getNewProcessId());
        bpmProcess.setName(cloneInput.name);
//        String xml = updateId(id, processDefinition.getId(), src.getBpmnXml());
//        xml = updateName(src.getName(), processDefinition.getName(), xml);
        bpmProcess.setBpmnXml(src.getBpmnXml());
        updateIdAndName(bpmProcess);
        bpmProcess.setCreatedBy(getCurrentUserId());
        bpmProcess.setCreatedTime(new Date());
        return this.bpmProcessDefinitionDao.save(bpmProcess);
    }

    @PostMapping("{id}/deploy")
    @ResponseBody
    public BpmProcess deployProcessDefinition(@PathVariable String id) {
        BpmProcess bpmProcess = this.bpmProcessDefinitionDao.findById(id).orElseThrow();
        DeploymentBuilder deploymentBuilder = processEngine.getRepositoryService().createDeployment();
        deploymentBuilder.name(bpmProcess.getName());
        deploymentBuilder.addString(bpmProcess.getName() + ".bpmn", bpmProcess.getBpmnXml());
//        deploymentBuilder.tenantId(bpmProcess.getCreatedBy());
        deploymentBuilder.source(BpmProcessDefinitionService.XBPM);
        DeploymentWithDefinitions deploymentWithDefinitions = deploymentBuilder.deployWithResult();
        ProcessDefinition processDefinition = deploymentWithDefinitions.getDeployedProcessDefinitions().get(0);
        bpmProcess.setDeployedVersion(processDefinition.getVersion());
        bpmProcess.setDeployedAt(new Date());
        bpmProcess.setDeployedProcessDefinitionId(processDefinition.getId());
        this.bpmProcessDefinitionDao.save(bpmProcess);
        return bpmProcess;

    }

    @PostMapping("{id}/start")
    @ResponseBody
    public ProcessInstanceDto startProcessDefinition(@PathVariable String id) {
        BpmProcess bpmProcess = this.bpmProcessDefinitionDao.findById(id).orElseThrow();

        String deployedProcessDefinitionId = bpmProcess.getDeployedProcessDefinitionId();

        if( deployedProcessDefinitionId == null ) {
            throw new RuntimeException("流程未部署");
        }

        ProcessInstance processInstance = processEngine.getRuntimeService().startProcessInstanceById(deployedProcessDefinitionId);
        return new ProcessInstanceDto(processInstance);


    }


}
