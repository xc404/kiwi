package com.kiwi.project.bpm.ctl;

import com.kiwi.project.bpm.dao.BpmComponentDao;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.service.BpmComponentService;
import com.kiwi.project.bpm.utils.CliHelpExecutionException;
import com.kiwi.project.bpm.utils.CliHelpParser;
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
            return CliHelpParser.buildComponent(request.getHelpCommand().trim(), shellParentId);
        } catch (CliHelpExecutionException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @Data
    public static class CliHelpGenerateRequest {
        /** 用于获取 help 的完整命令行（由服务端通过 cmd/sh 执行） */
        private String helpCommand;
    }

}
