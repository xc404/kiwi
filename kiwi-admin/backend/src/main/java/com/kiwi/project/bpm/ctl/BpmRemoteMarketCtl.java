package com.kiwi.project.bpm.ctl;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.kiwi.framework.ctl.BaseCtl;
import com.kiwi.project.bpm.dto.BpmRemoteMarketInstallResultDto;
import com.kiwi.project.bpm.dto.BpmRemoteMarketItemDetailDto;
import com.kiwi.project.bpm.dto.BpmRemoteMarketItemDto;
import com.kiwi.project.bpm.dto.BpmRemoteMarketSyncResultDto;
import com.kiwi.project.bpm.dto.InstallTemplatePackInput;
import com.kiwi.project.bpm.service.BpmRemoteMarketInstallService;
import com.kiwi.project.bpm.service.BpmRemoteMarketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@SaCheckLogin
@RestController
@RequestMapping("bpm/remote-market")
@RequiredArgsConstructor
@Tag(name = "BPM 远程市场", description = "Nexus-backed 远程模板包与插件市场")
public class BpmRemoteMarketCtl extends BaseCtl {

    private final BpmRemoteMarketService marketService;
    private final BpmRemoteMarketInstallService installService;

    @Operation(operationId = "bpmRemoteMarket_list", summary = "查询远程市场条目列表")
    @GetMapping("")
    @ResponseBody
    public List<BpmRemoteMarketItemDto> list(
            @Parameter(description = "类型：template 或 plugin") @RequestParam(required = false) String type,
            @Parameter(description = "关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "市场源 ID") @RequestParam(required = false) String sourceId) {
        return marketService.listItems(type, keyword, sourceId);
    }

    @Operation(operationId = "bpmRemoteMarket_get", summary = "查询远程市场条目详情")
    @GetMapping("{slug}/versions/{version}")
    @ResponseBody
    public BpmRemoteMarketItemDetailDto get(
            @PathVariable String slug,
            @PathVariable String version,
            @Parameter(description = "市场源 ID") @RequestParam(required = false) String sourceId) {
        return marketService.getItem(slug, version, sourceId);
    }

    @Operation(operationId = "bpmRemoteMarket_sync", summary = "刷新远程市场索引缓存")
    @PostMapping("sync")
    @ResponseBody
    public BpmRemoteMarketSyncResultDto sync() {
        return marketService.sync();
    }

    @Operation(operationId = "bpmRemoteMarket_installTemplate", summary = "下载并安装远程模板包为新项目")
    @PostMapping("templates/{slug}/versions/{version}/install")
    @ResponseBody
    public BpmRemoteMarketInstallResultDto installTemplate(
            @PathVariable String slug,
            @PathVariable String version,
            @Parameter(description = "市场源 ID") @RequestParam(required = false) String sourceId,
            @RequestBody(required = false) InstallTemplatePackInput input) {
        return installService.installTemplate(slug, version, sourceId, input, getCurrentUserId());
    }

    @Operation(operationId = "bpmRemoteMarket_installPlugin", summary = "下载并安装远程插件 JAR")
    @PostMapping("plugins/{slug}/versions/{version}/install")
    @ResponseBody
    public BpmRemoteMarketInstallResultDto installPlugin(
            @PathVariable String slug,
            @PathVariable String version,
            @Parameter(description = "市场源 ID") @RequestParam(required = false) String sourceId) {
        return installService.installPlugin(slug, version, sourceId, getCurrentUserId());
    }
}
