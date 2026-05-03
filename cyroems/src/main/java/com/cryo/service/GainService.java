package com.cryo.service;

import com.cryo.dao.dataset.TaskDataSetRepository;
import com.cryo.dao.TaskRepository;
import com.cryo.dao.export.ExportTaskRepository;
import com.cryo.model.Task;
import com.cryo.model.dataset.TaskDataset;
import com.cryo.model.export.ExportTask;
import com.cryo.task.support.ExportSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
@RequiredArgsConstructor
@Slf4j
public class GainService
{


    private final FilePathService filePathService;
    private final ExportSupport exportSupport;
    private final TaskDataSetRepository taskDataSetRepository;
    private final TaskRepository taskRepository;
    private final ExportTaskRepository exportTaskRepository;
    public void exportGain(ExportTask exportTask) {
        Task task = this.taskRepository.findById(exportTask.getTask_id()).orElseThrow();
        TaskDataset dataset = this.taskDataSetRepository.findById(task.getTaskSettings().getDataset_id()).orElseThrow();
        copy(task, exportTask,dataset);
    }

    public void copy(Task task, ExportTask exportTask, TaskDataset taskDataset) {

       if(exportTask.getGainExported()){
           return;
       }
       if(exportTask.isCryosparc()){
           return;
       }
       if(!exportTask.getExportSettings().isExportGain()){
           return;
       }
        TaskDataset.Gain gain = taskDataset.getGain0();
        if( gain == null ) {
            return;
        }

        File outputDir = filePathService.getTaskOutputDir(task,exportTask.getOutputDir());
        this.exportSupport.copyToUser(task, gain.getUsable_path(), outputDir);
        this.exportSupport.copyToUser(task, gain.getPath(), outputDir);
        exportTask.setGainExported(true);
        this.exportTaskRepository.save(exportTask);
    }

}
