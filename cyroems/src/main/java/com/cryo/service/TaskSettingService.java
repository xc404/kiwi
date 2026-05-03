package com.cryo.service;

import com.cryo.model.Task;
import com.cryo.model.settings.TaskSettings;
import net.dreamlu.mica.core.utils.JsonUtil;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;

@Service
public class TaskSettingService implements InitializingBean
{
    private final String task_setting_resource = "classpath:settings/default-task-setting.json";
    private final String task_et_resource = "classpath:settings/default-et-setting.json";

    private String taskJson ;
    private String etTaskJson;

    public TaskSettings getDefaultTaskSettings(Task task)
    {
        String json = task.getIs_tomo() ?  etTaskJson : taskJson;
        TaskSettings taskSettings = JsonUtil.readValue(json, TaskSettings.class);


        taskSettings.setDataset_id(task.getTaskSettings().getDataset_id());
        taskSettings.setTaskDataSetSetting(task.getTaskSettings().getTaskDataSetSetting());
        return taskSettings;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        File file = ResourceUtils.getFile(task_setting_resource);
        this.taskJson = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
        this.etTaskJson = FileUtils.readFileToString(ResourceUtils.getFile(task_et_resource), StandardCharsets.UTF_8);
    }
}
