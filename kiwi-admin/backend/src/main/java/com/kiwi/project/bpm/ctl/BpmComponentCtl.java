package com.kiwi.project.bpm.ctl;

import com.kiwi.project.bpm.dao.BpmComponentDao;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.service.BpmComponentService;
import com.kiwi.project.bpm.utils.CliHelpExecutionException;
import com.kiwi.project.bpm.utils.CliHelpParser;
import com.kiwi.project.bpm.utils.OpenApiComponentGenerator;
import com.kiwi.project.bpm.utils.OpenApiSpecFetcher;
import org.apache.commons.lang3.StringUtils;
import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SaCheckLogin
@RestController
@RequestMapping("bpm/component")
@RequiredArgsConstructor
public class BpmComponentCtl
{

    private final BpmComponentDao bpmComponentDao;
    private final BpmComponentService bpmComponentService;

    // 查询所有组件分组（已实现）
    @ResponseBody
    @GetMapping
    public Page<BpmComponent> getComponents(Pageable pageable) {
        return bpmComponentDao.findAll(pageable);
    }


    // 新增组件
    @PostMapping("")
    @ResponseBody
    public BpmComponent addComponent(@RequestBody BpmComponent bpmComponent) {
        BpmComponent save = bpmComponentDao.save(bpmComponent);
        this.bpmComponentService.refresh();
        return save;
    }


    // 修改组件
    @PutMapping("{id}")
    @ResponseBody
    public BpmComponent updateComponent(@PathVariable String id, @RequestBody BpmComponent bpmComponent) {

        bpmComponentDao.updateSelective(bpmComponent);
        this.bpmComponentService.refresh();
        return this.bpmComponentDao.findById(id).orElseThrow();
    }

    // 删除组件
    @DeleteMapping("{id}")
    @ResponseBody
    public void deleteComponent(@PathVariable String id) {
        bpmComponentDao.deleteById(id);
    }



    @GetMapping("list")
    @ResponseBody
    public List<ComponentGroup> getComponents() {
        Map<String, List<BpmComponent>> map = bpmComponentDao.findAll().stream()
                .map(c -> {
                    return this.bpmComponentService.fillComponentProperties(c);
                })
                .collect(Collectors.groupingBy(BpmComponent::getGroup));
        return map.entrySet().stream().map(e -> {
            return new ComponentGroup(e.getKey(), e.getValue());
        }).toList();
    }

    @Data
    @AllArgsConstructor
    public static class ComponentGroup {
        private String group;
        private List<BpmComponent> components;
    }

    /**
     * 在后端执行 {@code helpCommand}（如 {@code docker --help}）获取 help 输出，生成继承 shell 的 {@link BpmComponent} 草稿；
     * 名称、key、分组、描述等均由后端默认推导。
     */
    @PostMapping("from-cli-help")
    @ResponseBody
    public BpmComponent generateFromCliHelp(@RequestBody CliHelpGenerateRequest request) {
        if (request == null || StringUtils.isBlank(request.getHelpCommand())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "helpCommand 不能为空");
        }
        String shellParentId = bpmComponentService.resolveShellParentComponentId();
        try {
            return CliHelpParser.buildComponent(
                    request.getHelpCommand().trim(),
                    shellParentId,
                    request.getHelpText());
        } catch (CliHelpExecutionException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @Data
    public static class CliHelpGenerateRequest {
        /** 用于获取 help 的完整命令行（由服务端通过 cmd/sh 执行；非空 {@link #helpText} 时不执行，仅用于推导命令前缀等） */
        private String helpCommand;
        /** 可选；非空时直接使用该文本作为 help 输出解析，不再执行 {@link #helpCommand} */
        private String helpText;
    }

    /**
     * 根据 OpenAPI 3.x 或 Swagger 2.0 文档（JSON/YAML）为每个 HTTP 操作生成一个 {@link BpmComponent} 草稿；
     * 组件继承 {@code httpRequest}（{@link com.kiwi.bpmn.component.activity.HttpRequestActivity}）父定义，仅覆盖
     * url、method、headers、body 等默认值。
     */
    @PostMapping("from-openapi")
    @ResponseBody
    public List<BpmComponent> generateFromOpenApi(@RequestBody OpenApiGenerateRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体不能为空");
        }
        String specContent = resolveOpenApiSpecContent(request);
        String parentId = bpmComponentService.resolveHttpRequestParentComponentId();
        try {
            return OpenApiComponentGenerator.buildComponents(specContent, request.getBaseUrl(), parentId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /**
     * 优先使用 {@code specUrl} 在服务端 GET 拉取正文；否则使用请求体中的 {@code spec} 全文。
     */
    private static String resolveOpenApiSpecContent(OpenApiGenerateRequest request) {
        if (StringUtils.isNotBlank(request.getSpecUrl())) {
            try {
                return OpenApiSpecFetcher.fetch(request.getSpecUrl().trim());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
            } catch (IllegalStateException e) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
            }
        }
        if (StringUtils.isNotBlank(request.getSpec())) {
            return request.getSpec();
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "spec 与 specUrl 至少填写一项");
    }

    @Data
    public static class OpenApiGenerateRequest {
        /**
         * OpenAPI / Swagger 文档全文（JSON 或 YAML）；与 {@link #specUrl} 二选一即可（若同时提供则优先拉取
         * URL）。
         */
        private String spec;
        /**
         * 文档的 http(s) 地址，由服务端 GET 拉取后再解析；与 {@link #spec} 二选一即可。
         */
        private String specUrl;
        /**
         * 可选；非空时作为根 URL（当文档中 servers 为空或为相对路径时尤其有用）。
         * 与 path 拼接为默认 {@code url} 参数。
         */
        private String baseUrl;
    }

}
