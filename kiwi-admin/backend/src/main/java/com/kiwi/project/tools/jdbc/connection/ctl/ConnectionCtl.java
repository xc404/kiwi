package com.kiwi.project.tools.jdbc.connection.ctl;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.ai.tool.annotation.Tool;
import com.kiwi.common.query.QueryField;
import com.kiwi.common.query.QueryParams;
import com.kiwi.project.tools.jdbc.connection.dao.ConnectionSettingsDao;
import com.kiwi.project.tools.jdbc.connection.entity.ConnectionSettings;
import com.kiwi.project.tools.jdbc.connection.service.ConnectionService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.sql.Connection;

@RestController
@RequiredArgsConstructor
@RequestMapping("/tools/connection")
public class ConnectionCtl
{

    private final ConnectionSettingsDao connectionSettingsDao;
    private final ConnectionService connectionService;

    public static class SearchInput
    {
        @QueryField(condition = QueryField.Type.LIKE)
        public String name;
    }

    @Tool(name = "conn_get", description = "按 id 获取 JDBC 连接配置。")
    @GetMapping("{id}")
    @SaCheckPermission("tools:connectionSettings:view")
    @Operation(description = "Jdbc链接查看")
    @ResponseBody
    public ConnectionSettings connectionSettings(@PathVariable("id") String id) {
        return this.connectionSettingsDao.findById(id).orElseThrow();
    }

    @GetMapping()
    @SaCheckPermission("tools:connectionSettings:view")
    @Operation(description = "Jdbc链接查看")
    @ResponseBody
    public Page<ConnectionSettings> connectionSettingsList(SearchInput searchInput, Pageable pageable) {
        return this.connectionSettingsDao.findBy(QueryParams.of(searchInput), pageable);
    }

    @Tool(
            name = "conn_aiPage",
            description = "分页查询 JDBC 连接配置。name 模糊；page 从 0 开始，size 默认 20、最大 100。")
    @SaCheckPermission("tools:connectionSettings:view")
    public Page<ConnectionSettings> aiConnectionPage(String name, Integer page, Integer size) {
        SearchInput si = new SearchInput();
        si.name = name;
        int p = page != null && page >= 0 ? page : 0;
        int s = size != null && size > 0 ? Math.min(size, 100) : 20;
        return connectionSettingsList(si, PageRequest.of(p, s));
    }

    @Tool(name = "conn_add", description = "新增 JDBC 连接配置。")
    @PostMapping()
    @SaCheckPermission("tools:connectionSettings:add")
    @Operation(description = "Jdbc链接添加")
    @ResponseBody
    public ConnectionSettings addConnectionSettings(@RequestBody ConnectionSettings connectionSettings) {
        this.connectionService.encrypt(connectionSettings);
        this.connectionSettingsDao.insert(connectionSettings);
        return connectionSettings;
    }

    @Tool(name = "conn_edit", description = "按 id 修改 JDBC 连接配置。")
    @PutMapping("{id}")
    @SaCheckPermission("tools:connectionSettings:update")
    @Operation(description = "Jdbc链接修改")
    @ResponseBody
    public ConnectionSettings editConnectionSettings(@PathVariable("id") String id, @RequestBody ConnectionSettings connectionSettings) {
        this.connectionService.encrypt(connectionSettings);
        this.connectionSettingsDao.updateSelective(connectionSettings);
        return this.connectionSettingsDao.findById(connectionSettings.getId()).orElseThrow();
    }

    @Tool(name = "conn_delete", description = "按 id 删除 JDBC 连接配置。")
    @DeleteMapping("/{id}")
    @SaCheckPermission("tools:connectionSettings:delete")
    @Operation(description = "Jdbc链接删除")
    @ResponseBody
    public void deleteConnectionSettings(@PathVariable("id") String id) {
        this.connectionSettingsDao.deleteById(id);
    }

    /**
     * 测试Jdbc连接
     *
     * @return 连接是否成功
     */
    @Tool(name = "conn_test", description = "测试 JDBC 连接是否可用。")
    @PostMapping("{id}/test-connection")
    @SaCheckPermission("tools:connectionSettings:test")
    @Operation(description = "Jdbc链接测试")
    @ResponseBody
    public boolean testConnection(@PathVariable String id) throws Exception {
        try( Connection connection = this.connectionService.getConnection(id); ) {
            return connection != null;
        }

    }
}
