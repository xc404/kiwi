package com.kiwi.project.bpm.ctl;

import com.kiwi.bpmn.component.activity.ShellActivityBehavior;
import com.kiwi.project.bpm.dao.BpmComponentDao;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.service.BpmComponentService;
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
    private final ShellActivityBehavior shellActivityBehavior;

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

}
