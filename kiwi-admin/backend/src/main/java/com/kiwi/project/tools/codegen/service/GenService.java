package com.kiwi.project.tools.codegen.service;

import com.kiwi.project.tools.codegen.dao.GenEntityDao;
import com.kiwi.project.tools.codegen.dao.GenFieldDao;
import com.kiwi.project.tools.codegen.entity.GenEntity;
import com.kiwi.project.tools.codegen.entity.vo.CodeGenVo;
import com.kiwi.project.tools.codegen.utils.GenUtils;
import com.kiwi.project.tools.codegen.utils.VelocityInitializer;
import com.kiwi.project.tools.codegen.utils.VelocityUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class GenService
{
    private final GenEntityDao genEntityDao;
    private final GenFieldDao genFieldDao;

    public void importGenTable(CodeGenVo codeGenVo) {
        GenUtils.initTable(codeGenVo.getGenEntity());
        var genEntity = this.genEntityDao.save(codeGenVo.getGenEntity());
        codeGenVo.getFields().forEach(column -> {
            column.setEntityId(genEntity.getId());
            GenUtils.initColumnField(column);
            this.genFieldDao.save(column);
        });
    }


    public Map<String, String> previewCode(String entityId) {
        Map<String, String> dataMap = new LinkedHashMap<>();
        // 查询表信息
        GenEntity genEntity = genEntityDao.findById(entityId).orElseThrow();
        CodeGenVo codeGenVo = new CodeGenVo(genEntity, genFieldDao.findByTableId(entityId));
        // 设置主子表信息
        // 设置主键列信息
        VelocityInitializer.initVelocity();

        VelocityContext context = VelocityUtils.prepareContext(codeGenVo);

        // 获取模板列表
        List<String> templates = VelocityUtils.getTemplateList(genEntity);
        File dir = new File("F://codegen");
        for( String template : templates ) {
            // 渲染模板
            StringWriter sw = new StringWriter();
            Template tpl = Velocity.getTemplate(template, StandardCharsets.UTF_8.displayName());
            String fileName = VelocityUtils.getFileName(template, genEntity);
            File file = new File(dir, fileName);

            tpl.merge(context, sw);
            try {
                FileUtils.writeStringToFile(file, sw.toString(), StandardCharsets.UTF_8);
            } catch( IOException e ) {
                throw new RuntimeException(e);
            }
            dataMap.put(template, sw.toString());
        }
        return dataMap;
    }

}
