package com.kiwi.project.tools.jdbc.connection.ctl;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.kiwi.common.query.QueryField;
import com.kiwi.common.query.QueryParams;
import com.kiwi.project.tools.jdbc.connection.dao.ConnectionSettingsDao;
import com.kiwi.project.tools.jdbc.connection.entity.ConnectionSettings;
import com.kiwi.project.tools.jdbc.connection.service.ConnectionService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.sql.Connection;

@Controller
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

    @PostMapping()
    @SaCheckPermission("tools:connectionSettings:add")
    @Operation(description = "Jdbc链接添加")
    @ResponseBody
    public ConnectionSettings addConnectionSettings(@RequestBody ConnectionSettings connectionSettings) {
        this.connectionService.encrypt(connectionSettings);
        this.connectionSettingsDao.insert(connectionSettings);
        return connectionSettings;
    }

    @PutMapping("{id}")
    @SaCheckPermission("tools:connectionSettings:update")
    @Operation(description = "Jdbc链接修改")
    @ResponseBody
    public ConnectionSettings editConnectionSettings(@PathVariable("id") String id, @RequestBody ConnectionSettings connectionSettings) {
        this.connectionService.encrypt(connectionSettings);
        this.connectionSettingsDao.updateSelective(connectionSettings);
        return this.connectionSettingsDao.findById(connectionSettings.getId()).orElseThrow();
    }

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
