package com.kiwi.project.tools.codegen.ctl;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.ai.tool.annotation.Tool;
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

@Tag(name = "GenTable", description = "CRUD operations for GenTable")
@RestController
@RequestMapping("/tools/codegen/entity")
@RequiredArgsConstructor
public class CodeGenEntityCtl
{
    private final GenEntityDao genTabledao;
    private final GenFieldDao genFieldDao;
    private final GenService genService;
    private final ConnectionService connectionService;

    public static class GenTableQuery
    {
        @QueryField(value = "tableName", condition = QueryField.Type.LIKE)
        public String tableName;
        @QueryField(value = "tableComment", condition = QueryField.Type.LIKE)
        public String tableComment;
    }

    @Operation(summary = "List GenTables")
    @SaCheckPermission("gen:entity:list")
    @GetMapping("")
    public Page<GenEntity> tables(GenTableQuery query, Pageable pageable) {
        return this.genTabledao.findBy(QueryParams.of(query), pageable);
    }

    @Tool(
            name = "codeEnt_aiSearch",
            description = "分页查询代码生成实体表配置。tableName、tableComment 模糊；page 从 0 开始，size 默认 20、最大 100。")
    @SaCheckPermission("gen:entity:list")
    public Page<GenEntity> aiTables(String tableName, String tableComment, Integer page, Integer size) {
        GenTableQuery q = new GenTableQuery();
        q.tableName = tableName;
        q.tableComment = tableComment;
        int p = page != null && page >= 0 ? page : 0;
        int s = size != null && size > 0 ? Math.min(size, 100) : 20;
        return tables(q, PageRequest.of(p, s));
    }

    @Tool(name = "codeEnt_get", description = "按 id 获取代码生成实体配置。")
    @Operation(summary = "Get GenTable by ID")
    @SaCheckPermission("gen:entity:view")
    @GetMapping("/{id}")
    public GenEntity getById(@Parameter(description = "entity ID") @PathVariable String id) {
        return genTabledao.findById(id).orElseThrow();
    }

    @Tool(name = "codeEnt_create", description = "新增代码生成实体配置。")
    @Operation(summary = "Create a new GenTable")
    @SaCheckPermission("gen:entity:add")
    @PostMapping("")
    public GenEntity create(
            @RequestBody(description = "GenTable to create") @org.springframework.web.bind.annotation.RequestBody GenEntity genEntity) {
        genTabledao.save(genEntity);
        return genEntity;
    }

    @Tool(name = "codeEnt_update", description = "按 id 更新代码生成实体配置。")
    @Operation(summary = "Update a GenTable by ID")
    @SaCheckPermission("gen:entity:edit")
    @PutMapping("/{id}")
    public GenEntity update(
            @Parameter(description = "entity ID") @PathVariable String id,
            @RequestBody(description = "GenTable to update") @org.springframework.web.bind.annotation.RequestBody GenEntity genEntity) {
        genEntity.setId(id);
        genTabledao.updateSelective(genEntity);
        return genEntity;
    }

    @Tool(name = "codeEnt_delete", description = "按 id 删除代码生成实体配置。")
    @Operation(summary = "Delete a GenTable by ID")
    @SaCheckPermission("gen:entity:delete")
    @DeleteMapping("/{id}")
    public void delete(@Parameter(description = "entity ID") @PathVariable String id) {


        this.genFieldDao.deleteByTableId(id);
        genTabledao.deleteById(id);

    }

    /**
     * 导入 Java 文件生成表信息
     *
     * @param javaFile 上传的 Java 文件
     */
    @Operation(summary = "Import entity from Java file")
    @SaCheckPermission("gen:entity:import")
    @PostMapping("import/javaFile")
    public void importTable(@RequestParam("file") MultipartFile javaFile) {
        try {
            CodeGenVo codeGenVo = JavaFileParser.fromJavaFile(javaFile.getInputStream());
            assert codeGenVo != null;
            this.genService.importGenTable(codeGenVo);
        } catch( IOException e ) {
            throw new RuntimeException(e);
        }
    }


    /**
     * 导入数据库表生成表信息
     *
     * @param input 包含连接 ID 和表名列表的输入对象
     */
    @Tool(name = "codeEnt_importDatabase", description = "从数据库连接导入表结构到代码生成（connectionId + 表名列表）。")
    @Operation(summary = "Import tables from database")
    @SaCheckPermission("gen:entity:import")
    @PostMapping("/import/database")
    public void importTable(@org.springframework.web.bind.annotation.RequestBody ImportTableInput input) {
        // Implementation here
        try( var con = this.connectionService.getConnection(input.connectionId) ) {
            List<CodeGenVo> codeGenVos = JdbcTableReader.readTables(con, input.tables);
            for( CodeGenVo codeGenVo : codeGenVos ) {
                this.genService.importGenTable(codeGenVo);
            }
        } catch( SQLException e ) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 预览生成的代码
     *
     * @return 生成的代码预览
     */
    @Tool(name = "codeEnt_preview", description = "预览某实体 id 的生成代码。")
    @Operation(summary = "Preview generated code")
    @SaCheckPermission("gen:entity:preview")
    @GetMapping("/{id}/preview")
    public Map<String, String> previewCode(@PathVariable("id") String id) {
        Map<String, String> stringStringMap = this.genService.previewCode(id);
        return stringStringMap;
    }

    @Data
    public static class ImportTableInput
    {
        private String connectionId;
        private List<String> tables;
    }
}