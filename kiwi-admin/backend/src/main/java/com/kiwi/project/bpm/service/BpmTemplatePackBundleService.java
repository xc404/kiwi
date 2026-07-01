package com.kiwi.project.bpm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.project.bpm.dao.BpmProcessDefinitionDao;
import com.kiwi.project.bpm.dao.BpmProjectEnvVarDao;
import com.kiwi.project.bpm.dao.BpmTemplateEnvVarDao;
import com.kiwi.project.bpm.dao.BpmTemplatePackDao;
import com.kiwi.project.bpm.dao.BpmTemplateProcessDao;
import com.kiwi.project.bpm.dto.InstallTemplatePackInput;
import com.kiwi.project.bpm.dto.InstallTemplatePackResult;
import com.kiwi.project.bpm.dto.PublishTemplatePackInput;
import com.kiwi.project.bpm.dto.TemplatePackZipDownload;
import com.kiwi.project.bpm.model.BpmProcess;
import com.kiwi.project.bpm.model.BpmProjectEnvVar;
import com.kiwi.project.bpm.model.BpmTemplateEnvVar;
import com.kiwi.project.bpm.model.BpmTemplatePack;
import com.kiwi.project.bpm.model.BpmTemplatePackManifest;
import com.kiwi.project.bpm.model.BpmTemplateProcess;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class BpmTemplatePackBundleService {

    private static final String ManifestFile = "manifest.json";
    private static final String ReadmeFile = "README.md";
    private static final String EnvVarsFile = "env-vars.json";
    private static final String ProcessesDir = "processes/";

    private final BpmTemplatePackDao packDao;
    private final BpmTemplateProcessDao processDao;
    private final BpmTemplateEnvVarDao envVarDao;
    private final BpmTemplatePackService packService;
    private final BpmTemplatePackPublishService publishService;
    private final BpmTemplatePackInstallService installService;
    private final BpmProcessDefinitionDao bpmProcessDao;
    private final BpmProjectEnvVarDao projectEnvVarDao;
    private final BpmOwnershipAccessService ownershipAccessService;
    private final ObjectMapper objectMapper;

    public Resource exportPack(String packId, String userId) {
        BpmTemplatePack pack = packService.requireReadablePack(packId, userId);
        List<BpmTemplateProcess> processes = processDao.findByPackIdOrderBySortAsc(packId);
        List<BpmTemplateEnvVar> envVars = envVarDao.findByPackIdOrderBySortAscKeyAsc(packId);
        byte[] zip = buildZip(pack, processes, envVars);
        String filename = pack.getSlug() + "-" + pack.getVersion() + ".kiwi-template-pack";
        return new ByteArrayResource(zip) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    public TemplatePackZipDownload exportPackDownload(String packId, String userId) {
        return toZipDownload(exportPack(packId, userId));
    }

    public TemplatePackZipDownload exportProjectDownload(String projectId, String userId) {
        return toZipDownload(exportProject(projectId, userId));
    }

    private TemplatePackZipDownload toZipDownload(Resource resource) {
        try {
            return new TemplatePackZipDownload(resource.getContentAsByteArray(), resource.getFilename());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "导出模板包失败");
        }
    }

    public Resource exportProject(String projectId, String userId) {
        ownershipAccessService.assertOwnsProject(userId, projectId);
        List<BpmProcess> processes = bpmProcessDao.findBy(Query.query(Criteria.where("projectId").is(projectId)));
        if (processes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "项目下没有流程");
        }
        BpmTemplatePack pack = new BpmTemplatePack();
        pack.setName("project-export");
        pack.setSlug("export-" + projectId);
        pack.setVersion("1.0.0");
        pack.setReadme("");
        pack.setManifest(new BpmTemplatePackManifest());
        List<BpmTemplateProcess> tps = new ArrayList<>();
        int sort = 0;
        for (BpmProcess p : processes) {
            BpmTemplateProcess tp = new BpmTemplateProcess();
            tp.setProcessKey(p.getId());
            tp.setName(p.getName());
            tp.setBpmnXml(p.getBpmnXml());
            tp.setEntry(p.isEntry());
            tp.setSort(sort++);
            tps.add(tp);
        }
        List<BpmProjectEnvVar> penv = projectEnvVarDao.findBy(
                Query.query(Criteria.where("projectId").is(projectId)));
        List<BpmTemplateEnvVar> tev = penv.stream().map(this::toTemplateEnv).toList();
        byte[] zip = buildZip(pack, tps, tev);
        return new ByteArrayResource(zip) {
            @Override
            public String getFilename() {
                return "project-" + projectId + ".kiwi-template-pack";
            }
        };
    }

    @Transactional
    public BpmTemplatePack importPack(MultipartFile file, PublishTemplatePackInput meta, String userId) {
        BundleContent content = parseZip(file);
        PublishTemplatePackInput input = meta != null ? meta : new PublishTemplatePackInput();
        if (StringUtils.isBlank(input.getName())) {
            input.setName(content.packMeta.getOrDefault("name", "Imported template pack").toString());
        }
        if (StringUtils.isBlank(input.getSummary()) && content.readme != null) {
            input.setSummary(content.readme.length() > 200 ? content.readme.substring(0, 200) : content.readme);
        }
        if (StringUtils.isBlank(input.getReadme())) {
            input.setReadme(content.readme);
        }
        BpmTemplatePack pack = publishService.publishFromBundle(input, userId, content);
        pack.setChecksum(content.checksum);
        packDao.updateSelective(pack);
        return pack;
    }

    @Transactional
    public InstallTemplatePackResult importAndInstall(MultipartFile file, InstallTemplatePackInput installInput, String userId) {
        BundleContent content = parseZip(file);
        PublishTemplatePackInput meta = new PublishTemplatePackInput();
        meta.setName(content.packMeta.getOrDefault("name", "Imported pack").toString());
        meta.setVisibility(BpmTemplatePack.Visibility.Private);
        BpmTemplatePack pack = publishService.publishFromBundle(meta, userId, content);
        return installService.installPack(pack.getId(), installInput, userId);
    }

    private byte[] buildZip(BpmTemplatePack pack, List<BpmTemplateProcess> processes, List<BpmTemplateEnvVar> envVars) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                Map<String, Object> manifest = new LinkedHashMap<>();
                manifest.put("name", pack.getName());
                manifest.put("version", pack.getVersion());
                manifest.put("slug", pack.getSlug());
                if (pack.getManifest() != null) {
                    manifest.put("kiwiManifest", pack.getManifest());
                }
                writeEntry(zos, ManifestFile, objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(manifest).getBytes(StandardCharsets.UTF_8));
                if (StringUtils.isNotBlank(pack.getReadme())) {
                    writeEntry(zos, ReadmeFile, pack.getReadme().getBytes(StandardCharsets.UTF_8));
                }
                writeEntry(zos, EnvVarsFile, objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsBytes(envVars));
                for (BpmTemplateProcess p : processes) {
                    String fileName = ProcessesDir + p.getProcessKey() + ".bpmn";
                    writeEntry(zos, fileName, p.getBpmnXml().getBytes(StandardCharsets.UTF_8));
                }
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "导出模板包失败", e);
        }
    }

    private void writeEntry(ZipOutputStream zos, String name, byte[] data) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(data);
        zos.closeEntry();
    }

    private BundleContent parseZip(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请上传 .kiwi-template-pack 文件");
        }
        BundleContent content = new BundleContent();
        content.processes = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                byte[] data = zis.readAllBytes();
                digest.update(data);
                String name = entry.getName();
                if (ManifestFile.equals(name)) {
                    content.packMeta = objectMapper.readValue(data, Map.class);
                } else if (ReadmeFile.equals(name)) {
                    content.readme = new String(data, StandardCharsets.UTF_8);
                } else if (EnvVarsFile.equals(name)) {
                    content.envVars = objectMapper.readValue(data,
                            objectMapper.getTypeFactory().constructCollectionType(List.class, BpmTemplateEnvVar.class));
                } else if (name.startsWith(ProcessesDir) && name.endsWith(".bpmn")) {
                    String key = name.substring(ProcessesDir.length(), name.length() - ".bpmn".length());
                    content.processes.put(key, new String(data, StandardCharsets.UTF_8));
                }
                zis.closeEntry();
            }
            content.checksum = HexFormat.of().formatHex(digest.digest());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法解析模板包: " + e.getMessage(), e);
        }
        if (content.processes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模板包内没有流程 BPMN 文件");
        }
        return content;
    }

    private BpmTemplateEnvVar toTemplateEnv(BpmProjectEnvVar ev) {
        BpmTemplateEnvVar te = new BpmTemplateEnvVar();
        te.setKey(ev.getKey());
        te.setValue(ev.getValue());
        te.setEncrypted(ev.getEncrypted());
        te.setDescription(ev.getDescription());
        te.setSort(ev.getSort());
        return te;
    }

    public static class BundleContent {
        Map<String, Object> packMeta = new LinkedHashMap<>();
        String readme;
        List<BpmTemplateEnvVar> envVars = new ArrayList<>();
        Map<String, String> processes = new LinkedHashMap<>();
        String checksum;
    }
}
