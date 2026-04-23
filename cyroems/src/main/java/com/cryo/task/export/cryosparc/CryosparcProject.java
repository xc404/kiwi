package com.cryo.task.export.cryosparc;

import com.cryo.common.model.DataEntity;
import lombok.Data;

@Data
public class CryosparcProject extends DataEntity
{
    private String project_uid;
    private String workspace_uid;
    private String workspace_title;
    private String path;
    private boolean cleaned;

    public void setWorkspace_title(String workspaceTitle) {
        this.workspace_title = workspaceTitle;
    }

    public String getWorkspace_title() {
        return workspace_title;
    }
}