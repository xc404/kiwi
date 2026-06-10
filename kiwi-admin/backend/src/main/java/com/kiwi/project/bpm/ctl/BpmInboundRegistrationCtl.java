package com.kiwi.project.bpm.ctl;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.kiwi.framework.ctl.BaseCtl;
import com.kiwi.project.bpm.model.BpmInboundRegistration;
import com.kiwi.project.bpm.service.BpmInboundRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@SaCheckLogin
@RestController
@RequestMapping("bpm/inbound/registration")
@RequiredArgsConstructor
@Tag(name = "BPM 入站组件注册", description = "Webhook 与 BPMN Message 映射管理")
public class BpmInboundRegistrationCtl extends BaseCtl {

    private final BpmInboundRegistrationService inboundRegistrationService;

    @Operation(operationId = "bpmInboundReg_page", summary = "分页查询入站组件注册")
    @GetMapping
    @ResponseBody
    public Page<BpmInboundRegistration> page(Pageable pageable) {
        return inboundRegistrationService.page(pageable);
    }

    @Operation(operationId = "bpmInboundReg_get", summary = "按 id 获取入站注册")
    @GetMapping("{id}")
    @ResponseBody
    public BpmInboundRegistration get(@PathVariable String id) {
        return inboundRegistrationService.get(id);
    }

    @Operation(operationId = "bpmInboundReg_add", summary = "新增入站组件注册")
    @PostMapping
    @ResponseBody
    public BpmInboundRegistration add(@RequestBody BpmInboundRegistration body) {
        return inboundRegistrationService.create(body);
    }

    @Operation(operationId = "bpmInboundReg_update", summary = "更新入站组件注册")
    @PutMapping("{id}")
    @ResponseBody
    public BpmInboundRegistration update(@PathVariable String id, @RequestBody BpmInboundRegistration body) {
        return inboundRegistrationService.update(id, body);
    }

    @Operation(operationId = "bpmInboundReg_delete", summary = "删除入站组件注册")
    @DeleteMapping("{id}")
    @ResponseBody
    public void delete(@PathVariable String id) {
        inboundRegistrationService.delete(id);
    }
}
