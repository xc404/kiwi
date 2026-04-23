package com.kiwi.project.bpm.ctl;

import cn.dev33.satoken.annotation.SaCheckLogin;
import org.springframework.ai.tool.annotation.Tool;
import com.kiwi.framework.ctl.BaseCtl;
import com.kiwi.common.query.QueryField;
import com.kiwi.common.query.QueryParams;
import com.kiwi.project.bpm.dao.BpmComponentDao;
import com.kiwi.project.bpm.dao.BpmProcessDefinitionDao;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.model.BpmProcess;
import com.kiwi.project.bpm.service.BpmComponentService;
import com.kiwi.project.bpm.service.BpmProcessDefinitionService;
import com.kiwi.project.bpm.service.BpmProcessIoAnalysisService;
import com.kiwi.project.bpm.service.BpmProcessStartService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.repository.DeploymentBuilder;
import org.camunda.bpm.engine.repository.DeploymentWithDefinitions;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.rest.dto.runtime.ProcessInstanceDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Date;
import java.util.Map;

import static com.kiwi.project.bpm.service.BpmProcessDefinitionService.getNewProcessId;
import static com.kiwi.project.bpm.service.BpmProcessDefinitionService.updateIdAndName;


@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping("/bpm/process")
public class BpmProcessDefinitionCtl extends BaseCtl
{


    private final BpmProcessDefinitionService bpmProcessDefinitionService;
    private final BpmProcessDefinitionDao bpmProcessDefinitionDao;
    private final BpmProcessIoAnalysisService bpmProcessIoAnalysisService;
    private final BpmComponentDao bpmComponentDao;
    private final BpmComponentService bpmComponentService;
    private final ProcessEngine processEngine;
    private final BpmProcessStartService bpmProcessStartService;


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
        /** 运行中实例数量上限；0 表示不限制；不传则不修改原值 */
        private Integer maxProcessInstances;
    }

    /** 启动流程实例时传入的流程变量（可选）。 */
    @Data
    public static class StartProcessInput {
        private Map<String, Object> variables;
    }

    /** 未保存的 BPMN 预览：包装为逻辑组件 */
    @Data
    public static class AnalyzeAsComponentInput
    {
        private String bpmnXml;
    }

    /** 将流程（及可选当前编辑中的 BPMN）另存为组件库条目 */
    @Data
    public static class SaveAsComponentInput
    {
//        private String key;
        private String name;
        private String description;
        private String version;
//        private String group;
    }



    @GetMapping()
    @ResponseBody
    public Page<BpmProcess> page(QueryInput queryInput, Pageable pageable) {
        return this.bpmProcessDefinitionDao.findBy(QueryParams.of(queryInput), pageable);
    }

    @Tool(
            name = "bpmPd_aiPage",
            description = "分页查询流程定义。projectId 可选；page 从 0 开始，size 默认 20、最大 100。")
    public Page<BpmProcess> aiPage(String projectId, Integer page, Integer size) {
        QueryInput q = new QueryInput();
        q.setProjectId(projectId);
        int p = page != null && page >= 0 ? page : 0;
        int s = size != null && size > 0 ? Math.min(size, 100) : 20;
        return page(q, PageRequest.of(p, s));
    }


    @Tool(name = "bpmPd_get", description = "按 id 获取流程定义。")
    @GetMapping("{id}")
    @ResponseBody
    public BpmProcess getProcessDefinition(@PathVariable String id) {
        return this.bpmProcessDefinitionDao.findById(id).orElseThrow();
    }

    /**
     * 将已保存流程的 BPMN 分析结果包装为 {@link BpmComponent}（输入/输出契约视图）。
     */
    @Tool(name = "bpmPd_getAsComponent", description = "将已保存流程的分析结果包装为组件契约视图。")
    @GetMapping("{id}/as-component")
    @ResponseBody
    public BpmComponent getProcessAsComponent(@PathVariable String id) {
        BpmProcess process = this.bpmProcessDefinitionDao.findById(id).orElseThrow();
        return this.bpmProcessIoAnalysisService.wrapProcessAsComponent(process);
    }

//    /**
//     * 根据请求体中的 BPMN 预览包装为 {@link BpmComponent}（无需已保存流程）。
//     */
//    @PostMapping("analyze-as-component")
//    @ResponseBody
//    public BpmComponent analyzeProcessAsComponent(@RequestBody AnalyzeAsComponentInput body) {
//        if (body == null || StringUtils.isBlank(body.getBpmnXml())) {
//            throw new IllegalArgumentException("bpmnXml 不能为空");
//        }
//        BpmProcess stub = new BpmProcess();
//        stub.setId("preview");
//        stub.setName("预览");
//        stub.setBpmnXml(body.getBpmnXml());
//        return this.bpmProcessIoAnalysisService.wrapProcessAsComponent(stub);
//    }

    /**
     * 另存为组件：分析 BPMN 推导输入/输出并写入组件库（一次请求完成）。
     * 请求体可带 {@code bpmnXml} 以使用画布当前未保存内容；否则使用库中已保存的 BPMN。
     */
    @Tool(name = "bpmPd_saveAsComponent", description = "将流程另存为组件库条目（可带名称、版本、描述）。")
    @PostMapping("{id}/save-as-component")
    @ResponseBody
    public BpmComponent saveAsComponent(@PathVariable String id, @RequestBody SaveAsComponentInput body) {
//        if (body == null || StringUtils.isBlank(body.getKey()) || StringUtils.isBlank(body.getName())) {
//            throw new IllegalArgumentException("key 与 name 不能为空");
//        }
        BpmProcess process = this.bpmProcessDefinitionDao.findById(id).orElseThrow();
        BpmComponent toSave = this.bpmProcessIoAnalysisService.wrapProcessAsComponent(process);
        toSave.setName(body.getName());
        toSave.setId(process.getId());
        toSave.setVersion(body.getVersion());
        toSave.setDescription(body.getDescription());
        BpmComponent saved = this.bpmComponentDao.save(toSave);
        this.bpmComponentService.refresh();
        return saved;
    }


    @Tool(name = "bpmPd_create", description = "新建流程定义（名称、所属 projectId）。")
    @PostMapping()
    @ResponseBody
    public BpmProcess createProcessDefinition(@RequestBody CreateInput createInput) {
        BpmProcess bpmProcess = this.bpmProcessDefinitionService.createProcessDefinition(createInput.name);
        bpmProcess.setProjectId(createInput.projectId);
        this.bpmProcessDefinitionDao.insert(bpmProcess);
        return bpmProcess;
    }


    @Tool(name = "bpmPd_save", description = "保存流程名称与 BPMN XML。")
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
        if( saveInput.maxProcessInstances != null ) {
            if( saveInput.maxProcessInstances < 0 ) {
                throw new IllegalArgumentException("maxProcessInstances 不能为负数");
            }
            bpmProcess.setMaxProcessInstances(saveInput.maxProcessInstances);
        }

        this.bpmProcessDefinitionDao.updateSelective(bpmProcess);
        return bpmProcess;
    }

    @Tool(name = "bpmPd_saveAs", description = "将流程另存为新 id 的流程。")
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


    @Tool(name = "bpmPd_clone", description = "克隆流程为新流程。")
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

    @Tool(name = "bpmPd_deploy", description = "部署流程到 Camunda 引擎。")
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

    @Tool(name = "bpmPd_start", description = "启动已部署流程的最新实例。")
    @PostMapping("{id}/start")
    @ResponseBody
    public ProcessInstanceDto startProcessDefinition(@PathVariable String id, @RequestBody(required = false) StartProcessInput body) {
        Map<String, Object> variables = body != null ? body.getVariables() : null;
        return this.bpmProcessStartService.start(id, variables);
    }


}
