package com.kiwi.project.tools.jdbc.connection.ctl;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.kiwi.common.query.QueryField;
import com.kiwi.common.query.QueryParams;
import com.kiwi.project.tools.jdbc.connection.dao.ConnectionSettingsDao;
import com.kiwi.project.tools.jdbc.connection.entity.ConnectionSettings;
import com.kiwi.project.tools.jdbc.connection.service.ConnectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Connection;

@RestController
@RequiredArgsConstructor
@RequestMapping("/tools/connection")
@Tag(name = "JDBC 连接配置", description = "数据源连接 CRUD 与测试")
public class ConnectionCtl {

    private final ConnectionSettingsDao connectionSettingsDao;
    private final ConnectionService connectionService;

    public static class SearchInput {
        @QueryField(condition = QueryField.Type.LIKE)
        public String name;
    }

    @Operation(operationId = "conn_get", summary = "按 id 获取 JDBC 连接配置")
    @GetMapping("{id}")
    @SaCheckPermission("tools:connectionSettings:view")
    @ResponseBody
    public ConnectionSettings connectionSettings(@PathVariable("id") String id) {
        return this.connectionSettingsDao.findById(id).orElseThrow();
    }

    @GetMapping()
    @SaCheckPermission("tools:connectionSettings:view")
    @ResponseBody
    public Page<ConnectionSettings> connectionSettingsList(SearchInput searchInput, Pageable pageable) {
        return this.connectionSettingsDao.findBy(QueryParams.of(searchInput), pageable);
    }

    @Operation(
            operationId = "conn_aiPage",
            summary = "分页查询 JDBC 连接配置",
            description = "name 模糊；page 从 0 开始，size 默认 20、最大 100。")
    @GetMapping("/search/ai-page")
    @SaCheckPermission("tools:connectionSettings:view")
    @ResponseBody
    public Page<ConnectionSettings> aiConnectionPage(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        SearchInput si = new SearchInput();
        si.name = name;
        int p = page != null && page >= 0 ? page : 0;
        int s = size != null && size > 0 ? Math.min(size, 100) : 20;
        return connectionSettingsList(si, PageRequest.of(p, s));
    }

    @Operation(operationId = "conn_add", summary = "新增 JDBC 连接配置")
    @PostMapping()
    @SaCheckPermission("tools:connectionSettings:add")
    @ResponseBody
    public ConnectionSettings addConnectionSettings(@RequestBody ConnectionSettings connectionSettings) {
        this.connectionService.encrypt(connectionSettings);
        this.connectionSettingsDao.insert(connectionSettings);
        return connectionSettings;
    }

    @Operation(operationId = "conn_edit", summary = "按 id 修改 JDBC 连接配置")
    @PutMapping("{id}")
    @SaCheckPermission("tools:connectionSettings:update")
    @ResponseBody
    public ConnectionSettings editConnectionSettings(
            @PathVariable("id") String id, @RequestBody ConnectionSettings connectionSettings) {
        this.connectionService.encrypt(connectionSettings);
        this.connectionSettingsDao.updateSelective(connectionSettings);
        return this.connectionSettingsDao.findById(connectionSettings.getId()).orElseThrow();
    }

    @Operation(operationId = "conn_delete", summary = "按 id 删除 JDBC 连接配置")
    @DeleteMapping("/{id}")
    @SaCheckPermission("tools:connectionSettings:delete")
    @ResponseBody
    public void deleteConnectionSettings(@PathVariable("id") String id) {
        this.connectionSettingsDao.deleteById(id);
    }

    @Operation(operationId = "conn_test", summary = "测试 JDBC 连接是否可用")
    @PostMapping("{id}/test-connection")
    @SaCheckPermission("tools:connectionSettings:test")
    @ResponseBody
    public boolean testConnection(@PathVariable String id) throws Exception {
        try (Connection connection = this.connectionService.getConnection(id)) {
            return connection != null;
        }
    }
}
