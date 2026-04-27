package com.kiwi.project.bpm.ctl;

import cn.dev33.satoken.annotation.SaCheckLogin;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Date;
import java.util.Map;

import static com.kiwi.project.bpm.service.BpmProcessDefinitionService.getNewProcessId;
import static com.kiwi.project.bpm.service.BpmProcessDefinitionService.updateIdAndName;


@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping("/bpm/process")
@Tag(name = "BPM 流程定义", description = "流程 CRUD、部署与启动")
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

    @Operation(
            operationId = "bpmPd_aiPage",
            summary = "分页查询流程定义",
            description = "projectId 可选；page 从 0 开始，size 默认 20、最大 100。")
    @GetMapping("/search/ai-page")
    @ResponseBody
    public Page<BpmProcess> aiPage(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        QueryInput q = new QueryInput();
        q.setProjectId(projectId);
        int p = page != null && page >= 0 ? page : 0;
        int s = size != null && size > 0 ? Math.min(size, 100) : 20;
        return page(q, PageRequest.of(p, s));
    }

    @Operation(operationId = "bpmPd_get", summary = "按 id 获取流程定义")
    @GetMapping("{id}")
    @ResponseBody
    public BpmProcess getProcessDefinition(@PathVariable String id) {
        return this.bpmProcessDefinitionDao.findById(id).orElseThrow();
    }

    /**
     * 将已保存流程的 BPMN 分析结果包装为 {@link BpmComponent}（输入/输出契约视图）。
     */
    @Operation(operationId = "bpmPd_getAsComponent", summary = "将已保存流程的分析结果包装为组件契约视图")
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
    @Operation(operationId = "bpmPd_saveAsComponent", summary = "将流程另存为组件库条目（可带名称、版本、描述）")
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


    @Operation(operationId = "bpmPd_create", summary = "新建流程定义（名称、所属 projectId）")
    @PostMapping()
    @ResponseBody
    public BpmProcess createProcessDefinition(@RequestBody CreateInput createInput) {
        BpmProcess bpmProcess = this.bpmProcessDefinitionService.createProcessDefinition(createInput.name);
        bpmProcess.setProjectId(createInput.projectId);
        this.bpmProcessDefinitionDao.insert(bpmProcess);
        return bpmProcess;
    }


    @Operation(operationId = "bpmPd_save", summary = "保存流程名称与 BPMN XML")
    @PutMapping("{id}")
    @ResponseBody
    public BpmProcess saveProcessDefinition(@PathVariable String id, @RequestBody SaveInput saveInput) {
        BpmProcess bpmProcess = this.bpmProcessDefinitionDao.findById(id).orElseThrow();

        if( saveInput.name != null ) {
            bpmProcess.setName(saveInput.name);
            updateIdAndName(bpmProcess);
        }
        var xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<bpmn:definitions xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\" xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\" xmlns:camunda=\"http://camunda.org/schema/1.0/bpmn\" xmlns:kiwi=\"http://kiwi.com/bpmn\" xmlns:di=\"http://www.omg.org/spec/DD/20100524/DI\" id=\"Definitions_1\" targetNamespace=\"http://bpmn.io/schema/bpmn\">\n" +
                "    <bpmn:process id=\"p-3dbc94f6-a6c5-479e-8fd0-a697f9c3a7fd\" name=\"cyroems\" isExecutable=\"true\">\n" +
                "        <bpmn:startEvent id=\"StartEvent_1\" name=\"\">\n" +
                "            <bpmn:outgoing>Flow_1whiiel</bpmn:outgoing>\n" +
                "        </bpmn:startEvent>\n" +
                "        <bpmn:serviceTask id=\"Activity_0nd8l0g\" name=\"定义全局变量\" camunda:delegateExpression=\"${assignmentActivity}\" kiwi:componentId=\"classpath_assignmentActivity\">\n" +
                "            <bpmn:extensionElements>\n" +
                "                <camunda:properties>\n" +
                "                    <camunda:property name=\"componentId\" value=\"classpath_assignmentActivity\" />\n" +
                "                </camunda:properties>\n" +
                "                <camunda:inputOutput>\n" +
                "                    <camunda:inputParameter name=\"assignments\">[{\"key\":\"work_dir\",\"value\":\"/home/chaox/cryoems/tasks/${task.work_dir}\"},{\"key\":\"p_size\",\"value\":\"${task.p_size}\"},{\"key\":\"total_dose_per_movie\",\"value\":\"${task.total_dose_per_movie}\"},{\"key\":\"movie_id\",\"value\":\"${movie.id}\"},{\"key\":\"movie_file_name\",\"value\":\"${movie.file_name}\"},{\"key\":\"movie_file_path\",\"value\":\"${movie.file_path}\"},{\"key\":\"microscope\",\"value\":\"${task.microscope}\"},{\"key\":\"gain_file\",\"value\":\"${task.gain_file}\"},{\"key\":\"partition\",\"value\":\"DF\"},{\"key\":\"motion_result_file\",\"value\":\"${work_dir}/${movie_file_name}.mrc\"},{\"key\":\"patch\",\"value\":5},{\"key\":\"eer_sampling\",\"value\":2},{\"key\":\"eer_fraction\",\"value\":40},{\"key\":\"binning_factor\",\"value\":2},{\"key\":\"acceleration_kv\",\"value\":300},{\"key\":\"spherical_aberration\",\"value\":2.7},{\"key\":\"amplitude_contrast\",\"value\":0.07},{\"key\":\"spectrum_size\",\"value\":1024},{\"key\":\"min_res\",\"value\":30},{\"key\":\"max_res\",\"value\":5},{\"key\":\"min_defocus\",\"value\":5000},{\"key\":\"max_defocus\",\"value\":50000},{\"key\":\"defocus_step\",\"value\":100}]</camunda:inputParameter>\n" +
                "                </camunda:inputOutput>\n" +
                "            </bpmn:extensionElements>\n" +
                "            <bpmn:incoming>Flow_1whiiel</bpmn:incoming>\n" +
                "            <bpmn:outgoing>Flow_1s47zxm</bpmn:outgoing>\n" +
                "        </bpmn:serviceTask>\n" +
                "        <bpmn:sequenceFlow id=\"Flow_1whiiel\" name=\"\" sourceRef=\"StartEvent_1\" targetRef=\"Activity_0nd8l0g\" />\n" +
                "        <bpmn:serviceTask id=\"Activity_1i9jbk1\" name=\"CryoEMS 预处理\" camunda:delegateExpression=\"${cyroemsPrepareActivity}\" kiwi:componentId=\"classpath_cyroemsPrepareActivity\">\n" +
                "            <bpmn:extensionElements>\n" +
                "                <camunda:properties>\n" +
                "                    <camunda:property name=\"componentId\" value=\"classpath_cyroemsPrepareActivity\" />\n" +
                "                </camunda:properties>\n" +
                "                <camunda:inputOutput>\n" +
                "                    <camunda:inputParameter name=\"movieFile\">${movie_file_path}</camunda:inputParameter>\n" +
                "                    <camunda:inputParameter name=\"microscope\">${microscope}</camunda:inputParameter>\n" +
                "                    <camunda:inputParameter name=\"p_size\">${p_size}</camunda:inputParameter>\n" +
                "                    <camunda:outputParameter name=\"mrcMetadata\">mrcMetadata</camunda:outputParameter>\n" +
                "                    <camunda:outputParameter name=\"closetScale\">closetScale</camunda:outputParameter>\n" +
                "                </camunda:inputOutput>\n" +
                "            </bpmn:extensionElements>\n" +
                "            <bpmn:incoming>Flow_1s47zxm</bpmn:incoming>\n" +
                "            <bpmn:outgoing>Flow_1vk90tn</bpmn:outgoing>\n" +
                "        </bpmn:serviceTask>\n" +
                "        <bpmn:sequenceFlow id=\"Flow_1s47zxm\" name=\"\" sourceRef=\"Activity_0nd8l0g\" targetRef=\"Activity_1i9jbk1\" />\n" +
                "        <bpmn:serviceTask id=\"Activity_0edto47\" name=\"MotionCor3 CLI\" camunda:delegateExpression=\"${shell}\" camunda:type=\"external\" camunda:topic=\"slurm\" kiwi:componentId=\"69e6d3a3d734c66ace4fce63\">\n" +
                "            <bpmn:extensionElements>\n" +
                "                <camunda:properties>\n" +
                "                    <camunda:property name=\"componentId\" value=\"69e6d3a3d734c66ace4fce63\" />\n" +
                "                </camunda:properties>\n" +
                "                <camunda:inputOutput>\n" +
                "                    <camunda:inputParameter name=\"directory\" />\n" +
                "                    <camunda:inputParameter name=\"waitFlag\" />\n" +
                "                    <camunda:inputParameter name=\"redirectError\" />\n" +
                "                    <camunda:inputParameter name=\"cleanEnv\" />\n" +
                "                    <camunda:inputParameter name=\"InMrc\" />\n" +
                "                    <camunda:inputParameter name=\"InTiff\">${movie_path}</camunda:inputParameter>\n" +
                "                    <camunda:inputParameter name=\"InEer\" />\n" +
                "                    <camunda:inputParameter name=\"OutMrc\">${motion_result_file}</camunda:inputParameter>\n" +
                "                    <camunda:inputParameter name=\"ArcDir\" />\n" +
                "                    <camunda:inputParameter name=\"FullSum\" />\n" +
                "                    <camunda:inputParameter name=\"DefectFile\" />\n" +
                "                    <camunda:inputParameter name=\"InAln\" />\n" +
                "                    <camunda:inputParameter name=\"OutAln\" />\n" +
                "                    <camunda:inputParameter name=\"DefectMap\" />\n" +
                "                    <camunda:inputParameter name=\"Serial\" />\n" +
                "                    <camunda:inputParameter name=\"Gain\">${gain_file}</camunda:inputParameter>\n" +
                "                    <camunda:inputParameter name=\"Dark\" />\n" +
                "                    <camunda:inputParameter name=\"TmpFile\" />\n" +
                "                    <camunda:inputParameter name=\"LogFile\" />\n" +
                "                    <camunda:inputParameter name=\"Patch\">${patch}</camunda:inputParameter>\n" +
                "                    <camunda:inputParameter name=\"Iter\" />\n" +
                "                    <camunda:inputParameter name=\"Tol\" />\n" +
                "                    <camunda:inputParameter name=\"Bft\" />\n" +
                "                    <camunda:inputParameter name=\"PhaseOnly\" />\n" +
                "                    <camunda:inputParameter name=\"StackZ\" />\n" +
                "                    <camunda:inputParameter name=\"FtBin\" />\n" +
                "                    <camunda:inputParameter name=\"InitDose\" />\n" +
                "                    <camunda:inputParameter name=\"FmDose\" />\n" +
                "                    <camunda:inputParameter name=\"PixSize\" />\n" +
                "                    <camunda:inputParameter name=\"kV\" />\n" +
                "                    <camunda:inputParameter name=\"Align\" />\n" +
                "                    <camunda:inputParameter name=\"Throw\" />\n" +
                "                    <camunda:inputParameter name=\"Trunc\" />\n" +
                "                    <camunda:inputParameter name=\"SumRange\" />\n" +
                "                    <camunda:inputParameter name=\"Group\" />\n" +
                "                    <camunda:inputParameter name=\"Crop\" />\n" +
                "                    <camunda:inputParameter name=\"FmRef\" />\n" +
                "                    <camunda:inputParameter name=\"Tilt\" />\n" +
                "                    <camunda:inputParameter name=\"RotGain\" />\n" +
                "                    <camunda:inputParameter name=\"FlipGain\" />\n" +
                "                    <camunda:inputParameter name=\"Mag\" />\n" +
                "                    <camunda:inputParameter name=\"InFmMotion\" />\n" +
                "                    <camunda:inputParameter name=\"Gpu\" />\n" +
                "                    <camunda:inputParameter name=\"GpuMemUsage\" />\n" +
                "                    <camunda:inputParameter name=\"UseGpus\" />\n" +
                "                    <camunda:inputParameter name=\"SplitSum\" />\n" +
                "                    <camunda:inputParameter name=\"command\">MotionCor3 ${not empty InMrc ? ' -InMrc '.concat(InMrc) : ''} ${not empty InTiff ? ' -InTiff '.concat(InTiff) : ''} ${not empty InEer ? ' -InEer '.concat(InEer) : ''} ${not empty OutMrc ? ' -OutMrc '.concat(OutMrc) : ''} ${not empty ArcDir ? ' -ArcDir '.concat(ArcDir) : ''} ${not empty FullSum ? ' -FullSum '.concat(FullSum) : ''} ${not empty DefectFile ? ' -DefectFile '.concat(DefectFile) : ''} ${not empty InAln ? ' -InAln '.concat(InAln) : ''} ${not empty OutAln ? ' -OutAln '.concat(OutAln) : ''} ${not empty DefectMap ? ' -DefectMap '.concat(DefectMap) : ''} ${not empty Serial ? ' -Serial '.concat(Serial) : ''} ${not empty Gain ? ' -Gain '.concat(Gain) : ''} ${not empty Dark ? ' -Dark '.concat(Dark) : ''} ${not empty TmpFile ? ' -TmpFile '.concat(TmpFile) : ''} ${not empty LogFile ? ' -LogFile '.concat(LogFile) : ''} ${not empty Patch ? ' -Patch '.concat(Patch) : ''} ${not empty Iter ? ' -Iter '.concat(Iter) : ''} ${not empty Tol ? ' -Tol '.concat(Tol) : ''} ${not empty Bft ? ' -Bft '.concat(Bft) : ''} ${not empty PhaseOnly ? ' -PhaseOnly '.concat(PhaseOnly) : ''} ${not empty StackZ ? ' -StackZ '.concat(StackZ) : ''} ${not empty FtBin ? ' -FtBin '.concat(FtBin) : ''} ${not empty InitDose ? ' -InitDose '.concat(InitDose) : ''} ${not empty FmDose ? ' -FmDose '.concat(FmDose) : ''} ${not empty PixSize ? ' -PixSize '.concat(PixSize) : ''} ${not empty kV ? ' -kV '.concat(kV) : ''} ${not empty Align ? ' -Align '.concat(Align) : ''} ${not empty Throw ? ' -Throw '.concat(Throw) : ''} ${not empty Trunc ? ' -Trunc '.concat(Trunc) : ''} ${not empty SumRange ? ' -SumRange '.concat(SumRange) : ''} ${not empty Group ? ' -Group '.concat(Group) : ''} ${not empty Crop ? ' -Crop '.concat(Crop) : ''} ${not empty FmRef ? ' -FmRef '.concat(FmRef) : ''} ${not empty Tilt ? ' -Tilt '.concat(Tilt) : ''} ${not empty RotGain ? ' -RotGain '.concat(RotGain) : ''} ${not empty FlipGain ? ' -FlipGain '.concat(FlipGain) : ''} ${not empty Mag ? ' -Mag '.concat(Mag) : ''} ${not empty InFmMotion ? ' -InFmMotion '.concat(InFmMotion) : ''} ${not empty Gpu ? ' -Gpu '.concat(Gpu) : ''} ${not empty GpuMemUsage ? ' -GpuMemUsage '.concat(GpuMemUsage) : ''} ${not empty UseGpus ? ' -UseGpus '.concat(UseGpus) : ''} ${not empty SplitSum ? ' -SplitSum '.concat(SplitSum) : ''}</camunda:inputParameter>\n" +
                "                    <camunda:inputParameter name=\"slurm_job_name\" />\n" +
                "                    <camunda:inputParameter name=\"slurm_output_file\" />\n" +
                "                    <camunda:inputParameter name=\"slurm_partition\" />\n" +
                "                    <camunda:inputParameter name=\"slurm_time\" />\n" +
                "                    <camunda:inputParameter name=\"slurm_begin\" />\n" +
                "                    <camunda:inputParameter name=\"slurm_constraints\" />\n" +
                "                    <camunda:inputParameter name=\"slurm_cpu_per_task\" />\n" +
                "                    <camunda:inputParameter name=\"slurm_error_file\" />\n" +
                "                    <camunda:inputParameter name=\"slurm_exclude\" />\n" +
                "                    <camunda:inputParameter name=\"slurm_dependency\" />\n" +
                "                    <camunda:inputParameter name=\"slurm_exclusive\" />\n" +
                "                    <camunda:inputParameter name=\"slurm_gres\" />\n" +
                "                    <camunda:inputParameter name=\"slurm_label\" />\n" +
                "                    <camunda:inputParameter name=\"slurm_mem\" />\n" +
                "                    <camunda:inputParameter name=\"slurm_mem_per_cpu\" />\n" +
                "                    <camunda:inputParameter name=\"slurm_min_nodes\" />\n" +
                "                    <camunda:inputParameter name=\"slurm_max_nodes\" />\n" +
                "                    <camunda:inputParameter name=\"slurm_task_num\" />\n" +
                "                    <camunda:inputParameter name=\"slurm_nodelist\" />\n" +
                "                    <camunda:inputParameter name=\"slurm_qos\" />\n" +
                "                    <camunda:inputParameter name=\"slurm_signal\" />\n" +
                "                    <camunda:inputParameter name=\"slurm_account\" />\n" +
                "                    <camunda:inputParameter name=\"slurm_comment\" />\n" +
                "                    <camunda:inputParameter name=\"slurm_cpus_per_gpu\" />\n" +
                "                    <camunda:inputParameter name=\"slurm_deadline\" />\n" +
                "                    <camunda:inputParameter name=\"slurm_chdir\" />\n" +
                "                    <camunda:inputParameter name=\"slurm_gpus\" />\n" +
                "                    <camunda:inputParameter name=\"slurm_gpus_per_node\" />\n" +
                "                    <camunda:inputParameter name=\"slurm_gpus_per_task\" />\n" +
                "                    <camunda:outputParameter name=\"result\" />\n" +
                "                    <camunda:outputParameter name=\"errorCode\" />\n" +
                "                </camunda:inputOutput>\n" +
                "            </bpmn:extensionElements>\n" +
                "            <bpmn:incoming>Flow_1tsi8bo</bpmn:incoming>\n" +
                "        </bpmn:serviceTask>\n" +
                "        <bpmn:serviceTask id=\"Activity_1wxl2ny\" name=\"赋值组件\" camunda:delegateExpression=\"${assignmentActivity}\" kiwi:componentId=\"classpath_assignmentActivity\">\n" +
                "            <bpmn:extensionElements>\n" +
                "                <camunda:properties>\n" +
                "                    <camunda:property name=\"componentId\" value=\"classpath_assignmentActivity\" />\n" +
                "                    <camunda:property name=\"componentId\" value=\"classpath_assignmentActivity\" />\n" +
                "                    <camunda:property name=\"componentId\" value=\"classpath_assignmentActivity\" />\n" +
                "                    <camunda:property name=\"componentId\" value=\"classpath_assignmentActivity\" />\n" +
                "                </camunda:properties>\n" +
                "                <camunda:inputOutput>\n" +
                "                    <camunda:inputParameter name=\"assignments\">${'[{\"key\":\"movie_sections\",\"value\":\"\"},{\"key\":\"fmdose\",\"value\":\"\"}]'}</camunda:inputParameter>\n" +
                "                </camunda:inputOutput>\n" +
                "            </bpmn:extensionElements>\n" +
                "            <bpmn:incoming>Flow_1vk90tn</bpmn:incoming>\n" +
                "            <bpmn:outgoing>Flow_1tsi8bo</bpmn:outgoing>\n" +
                "        </bpmn:serviceTask>\n" +
                "        <bpmn:sequenceFlow id=\"Flow_1vk90tn\" name=\"\" sourceRef=\"Activity_1i9jbk1\" targetRef=\"Activity_1wxl2ny\" />\n" +
                "        <bpmn:sequenceFlow id=\"Flow_1tsi8bo\" name=\"\" sourceRef=\"Activity_1wxl2ny\" targetRef=\"Activity_0edto47\" />\n" +
                "    </bpmn:process>\n" +
                "    <bpmndi:BPMNDiagram id=\"BPMNDiagram_1\">\n" +
                "        <bpmndi:BPMNPlane id=\"BPMNPlane_1\" bpmnElement=\"p-3dbc94f6-a6c5-479e-8fd0-a697f9c3a7fd\">\n" +
                "            <bpmndi:BPMNShape id=\"_BPMNShape_StartEvent_2\" bpmnElement=\"StartEvent_1\">\n" +
                "                <dc:Bounds x=\"173\" y=\"102\" width=\"36\" height=\"36\" />\n" +
                "            </bpmndi:BPMNShape>\n" +
                "            <bpmndi:BPMNShape id=\"Activity_0nd8l0g_di\" bpmnElement=\"Activity_0nd8l0g\">\n" +
                "                <dc:Bounds x=\"280\" y=\"80\" width=\"100\" height=\"80\" />\n" +
                "                <bpmndi:BPMNLabel />\n" +
                "            </bpmndi:BPMNShape>\n" +
                "            <bpmndi:BPMNShape id=\"Activity_1i9jbk1_di\" bpmnElement=\"Activity_1i9jbk1\">\n" +
                "                <dc:Bounds x=\"300\" y=\"280\" width=\"100\" height=\"80\" />\n" +
                "                <bpmndi:BPMNLabel />\n" +
                "            </bpmndi:BPMNShape>\n" +
                "            <bpmndi:BPMNShape id=\"Activity_0edto47_di\" bpmnElement=\"Activity_0edto47\">\n" +
                "                <dc:Bounds x=\"710\" y=\"460\" width=\"100\" height=\"80\" />\n" +
                "                <bpmndi:BPMNLabel />\n" +
                "            </bpmndi:BPMNShape>\n" +
                "            <bpmndi:BPMNShape id=\"Activity_1wxl2ny_di\" bpmnElement=\"Activity_1wxl2ny\">\n" +
                "                <dc:Bounds x=\"480\" y=\"280\" width=\"100\" height=\"80\" />\n" +
                "                <bpmndi:BPMNLabel />\n" +
                "            </bpmndi:BPMNShape>\n" +
                "            <bpmndi:BPMNEdge id=\"Flow_1whiiel_di\" bpmnElement=\"Flow_1whiiel\">\n" +
                "                <di:waypoint x=\"209\" y=\"120\" />\n" +
                "                <di:waypoint x=\"280\" y=\"120\" />\n" +
                "            </bpmndi:BPMNEdge>\n" +
                "            <bpmndi:BPMNEdge id=\"Flow_1s47zxm_di\" bpmnElement=\"Flow_1s47zxm\">\n" +
                "                <di:waypoint x=\"330\" y=\"160\" />\n" +
                "                <di:waypoint x=\"330\" y=\"220\" />\n" +
                "                <di:waypoint x=\"350\" y=\"220\" />\n" +
                "                <di:waypoint x=\"350\" y=\"280\" />\n" +
                "            </bpmndi:BPMNEdge>\n" +
                "            <bpmndi:BPMNEdge id=\"Flow_1vk90tn_di\" bpmnElement=\"Flow_1vk90tn\">\n" +
                "                <di:waypoint x=\"400\" y=\"320\" />\n" +
                "                <di:waypoint x=\"480\" y=\"320\" />\n" +
                "            </bpmndi:BPMNEdge>\n" +
                "            <bpmndi:BPMNEdge id=\"Flow_1tsi8bo_di\" bpmnElement=\"Flow_1tsi8bo\">\n" +
                "                <di:waypoint x=\"580\" y=\"320\" />\n" +
                "                <di:waypoint x=\"615\" y=\"320\" />\n" +
                "                <di:waypoint x=\"615\" y=\"500\" />\n" +
                "                <di:waypoint x=\"710\" y=\"500\" />\n" +
                "            </bpmndi:BPMNEdge>\n" +
                "        </bpmndi:BPMNPlane>\n" +
                "    </bpmndi:BPMNDiagram>\n" +
                "</bpmn:definitions>\n";
        if( saveInput.bpmnXml != null ) {
            bpmProcess.setBpmnXml(saveInput.bpmnXml);
            if( bpmProcess.getDeployedVersion() >= 0 ) {
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

    @Operation(operationId = "bpmPd_saveAs", summary = "将流程另存为新 id 的流程")
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


    @Operation(operationId = "bpmPd_clone", summary = "克隆流程为新流程")
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

    @Operation(operationId = "bpmPd_deploy", summary = "部署流程到 Camunda 引擎")
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

    @Operation(operationId = "bpmPd_start", summary = "启动已部署流程的最新实例")
    @PostMapping("{id}/start")
    @ResponseBody
    public ProcessInstanceDto startProcessDefinition(@PathVariable String id, @RequestBody(required = false) StartProcessInput body) {
        Map<String, Object> variables = body != null ? body.getVariables() : null;
        return this.bpmProcessStartService.start(id, variables);
    }


}
