package com.cryo.service;

import com.cryo.dao.CryosparcProjectRepository;
import com.cryo.service.cryosparc.CryosparcClient;
import com.cryo.task.export.ExportTaskVo;
import com.cryo.task.export.cryosparc.CryosparcProject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dreamlu.mica.core.utils.DateUtil;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Calendar;
import java.util.Date;

@Service
@RequiredArgsConstructor
@Slf4j
public class CryosparcProjectService
{
    private final FilePathService filePathService;
    private final CryosparcClient cryosparcClient;
    private final CryosparcProjectRepository cryosparcProjectRepository;

    public CryosparcProject createProjectAndWorkspace(ExportTaskVo exportTask) {

        File project_path = getProjectPath();
        String project_title = "cryoems_" + project_path.getName();
        String owner_user_id = exportTask.getTask().getOwner();
        String project_uid = cryosparcClient.createProject(owner_user_id, project_path.getAbsolutePath(), project_title);

        String workspace_title = "cryoems_" + exportTask.getExportTask().getName();
        String workspace_desc = "cryoems_" + exportTask.getExportTask().getName();


        CryosparcProject workspace = this.cryosparcClient.createWorkspace(project_uid, workspace_title, workspace_desc);
        workspace.setId(workspace.getProject_uid());
        workspace.setPath(project_path.getAbsolutePath());
        if( this.cryosparcProjectRepository.findById(workspace.getId()).isPresent() ) {
            this.cryosparcProjectRepository.save(workspace);
        }
        return workspace;
    }

    private File getProjectPath() {
        String date = DateUtil.format(new Date(), "yyyy");

        Calendar calendar = Calendar.getInstance();
        int i = calendar.get(Calendar.WEEK_OF_YEAR);
        String dir = date + "-week" + i;
        String workDir = filePathService.getWorkDir("cryosparc_export_project").getAbsolutePath();
        return new File(new File(workDir), dir);
    }
}
