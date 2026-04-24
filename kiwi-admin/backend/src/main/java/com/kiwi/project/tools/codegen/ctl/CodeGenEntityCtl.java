package com.kiwi.project.tools.codegen.ctl;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.kiwi.common.query.QueryField;
import com.kiwi.common.query.QueryParams;
import com.kiwi.project.tools.codegen.dao.GenEntityDao;
import com.kiwi.project.tools.codegen.dao.GenFieldDao;
import com.kiwi.project.tools.codegen.entity.GenEntity;
import com.kiwi.project.tools.codegen.entity.vo.CodeGenVo;
import com.kiwi.project.tools.codegen.service.GenService;
import com.kiwi.project.tools.codegen.utils.JavaFileParser;
import com.kiwi.project.tools.codegen.utils.JdbcTableReader;
import com.kiwi.project.tools.jdbc.connection.service.ConnectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@Tag(name = "代码生成实体", description = "GenTable CRUD 与导入预览")
@RestController
@RequestMapping("/tools/codegen/entity")
@RequiredArgsConstructor
public class CodeGenEntityCtl {
    private final GenEntityDao genTabledao;
    private final GenFieldDao genFieldDao;
    private final GenService genService;
    private final ConnectionService connectionService;

    public static class GenTableQuery {
        @QueryField(value = "tableName", condition = QueryField.Type.LIKE)
        public String tableName;
        @QueryField(value = "tableComment", condition = QueryField.Type.LIKE)
        public String tableComment;
    }

    @SaCheckPermission("gen:entity:list")
    @GetMapping("")
    public Page<GenEntity> tables(GenTableQuery query, Pageable pageable) {
        return this.genTabledao.findBy(QueryParams.of(query), pageable);
    }

    @Operation(operationId = "codeEnt_get", summary = "按 id 获取代码生成实体配置")
    @SaCheckPermission("gen:entity:view")
    @GetMapping("/{id}")
    public GenEntity getById(@Parameter(description = "entity ID") @PathVariable String id) {
        return genTabledao.findById(id).orElseThrow();
    }

    @Operation(operationId = "codeEnt_create", summary = "新增代码生成实体配置")
    @SaCheckPermission("gen:entity:add")
    @PostMapping("")
    public GenEntity create(
            @RequestBody(description = "GenTable to create") @org.springframework.web.bind.annotation.RequestBody GenEntity genEntity) {
        genTabledao.save(genEntity);
        return genEntity;
    }

    @Operation(operationId = "codeEnt_update", summary = "按 id 更新代码生成实体配置")
    @SaCheckPermission("gen:entity:edit")
    @PutMapping("/{id}")
    public GenEntity update(
            @Parameter(description = "entity ID") @PathVariable String id,
            @RequestBody(description = "GenTable to update") @org.springframework.web.bind.annotation.RequestBody GenEntity genEntity) {
        genEntity.setId(id);
        genTabledao.updateSelective(genEntity);
        return genEntity;
    }

    @Operation(operationId = "codeEnt_delete", summary = "按 id 删除代码生成实体配置")
    @SaCheckPermission("gen:entity:delete")
    @DeleteMapping("/{id}")
    public void delete(@Parameter(description = "entity ID") @PathVariable String id) {
        this.genFieldDao.deleteByTableId(id);
        genTabledao.deleteById(id);
    }

    @SaCheckPermission("gen:entity:import")
    @PostMapping("import/javaFile")
    public void importTable(@RequestParam("file") MultipartFile javaFile) {
        try {
            CodeGenVo codeGenVo = JavaFileParser.fromJavaFile(javaFile.getInputStream());
            assert codeGenVo != null;
            this.genService.importGenTable(codeGenVo);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Operation(
            operationId = "codeEnt_importDatabase",
            summary = "从数据库连接导入表结构到代码生成",
            description = "connectionId + 表名列表。")
    @SaCheckPermission("gen:entity:import")
    @PostMapping("/import/database")
    public void importTable(@org.springframework.web.bind.annotation.RequestBody ImportTableInput input) {
        try (var con = this.connectionService.getConnection(input.connectionId)) {
            List<CodeGenVo> codeGenVos = JdbcTableReader.readTables(con, input.tables);
            for (CodeGenVo codeGenVo : codeGenVos) {
                this.genService.importGenTable(codeGenVo);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Operation(operationId = "codeEnt_preview", summary = "预览某实体 id 的生成代码")
    @SaCheckPermission("gen:entity:preview")
    @GetMapping("/{id}/preview")
    public Map<String, String> previewCode(@PathVariable("id") String id) {
        return this.genService.previewCode(id);
    }

    @Data
    public static class ImportTableInput {
        private String connectionId;
        private List<String> tables;
    }
}
