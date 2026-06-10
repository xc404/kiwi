package com.kiwi.project.tools.codegen.service;

import com.kiwi.project.tools.codegen.dao.GenEntityDao;
import com.kiwi.project.tools.codegen.dao.GenFieldDao;
import com.kiwi.project.tools.codegen.entity.GenEntity;
import com.kiwi.project.tools.codegen.entity.vo.CodeGenVo;
import com.kiwi.project.tools.codegen.entity.vo.GeneratedFile;
import com.kiwi.project.tools.codegen.utils.GenUtils;
import com.kiwi.project.tools.codegen.utils.VelocityInitializer;
import com.kiwi.project.tools.codegen.utils.VelocityUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RequiredArgsConstructor
@Service
public class GenService {

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
        for (GeneratedFile file : renderFiles(entityId)) {
            dataMap.put(file.path(), file.content());
        }
        return dataMap;
    }

    public byte[] buildZip(String entityId) {
        List<GeneratedFile> files = renderFiles(entityId);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            for (GeneratedFile file : files) {
                ZipEntry entry = new ZipEntry(file.path().replace('\\', '/'));
                zos.putNextEntry(entry);
                zos.write(file.content().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            zos.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("打包生成代码失败", e);
        }
    }

    public List<GeneratedFile> renderFiles(String entityId) {
        GenEntity genEntity = genEntityDao.findById(entityId).orElseThrow();
        CodeGenVo codeGenVo = new CodeGenVo(genEntity, genFieldDao.findByTableId(entityId));
        VelocityInitializer.initVelocity();
        VelocityContext context = VelocityUtils.prepareContext(codeGenVo);
        List<String> templates = VelocityUtils.getTemplateList(genEntity);
        return templates.stream()
                .map(template -> {
                    StringWriter sw = new StringWriter();
                    Template tpl = Velocity.getTemplate(template, StandardCharsets.UTF_8.displayName());
                    tpl.merge(context, sw);
                    String path = VelocityUtils.getFileName(template, genEntity);
                    return new GeneratedFile(path, sw.toString());
                })
                .toList();
    }

    public void writeToGenPath(String entityId) {
        GenEntity genEntity = genEntityDao.findById(entityId).orElseThrow();
        String genPath = genEntity.getGenPath();
        if (StringUtils.isBlank(genPath)) {
            return;
        }
        if (!GenConfig.isAllowOverwrite()) {
            throw new IllegalStateException("未开启 app.gen.allowOverwrite，禁止写入本地路径");
        }
        File baseDir = new File(genPath);
        for (GeneratedFile file : renderFiles(entityId)) {
            File target = new File(baseDir, file.path());
            try {
                FileUtils.writeStringToFile(target, file.content(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("写入生成文件失败: " + target.getAbsolutePath(), e);
            }
        }
    }
}
