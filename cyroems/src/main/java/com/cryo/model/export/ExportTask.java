package com.cryo.model.export;

import com.cryo.common.model.DataEntity;
import com.cryo.model.Task;
import com.cryo.model.TaskStatus;
import com.cryo.model.settings.CryosparcSettings;
import com.cryo.model.settings.ExportSettings;
import com.cryo.task.export.cryosparc.CryosparcCompleteStatus;
import com.cryo.task.export.cryosparc.CryosparcProject;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.Optional;


//@CompoundIndex(name = "export_task_id_and_name_index", def = "{'task_id': 1, 'name': 1}", unique = true)
@Document("exportTask")
@Data
public class ExportTask extends DataEntity
{


    private String name;
    private String task_id;

    private ExportSettings exportSettings;
    private CryosparcSettings cryosparcSettings;

    private Task.Statistic movie_statistic;

    private Task.Statistic mdoc_statistic;
    private TaskStatus status;
    @Hidden
    private Date last_detect_time;
    private boolean isCryosparc;
    private boolean isDefault;
    private CryosparcProject cryosparcProject;

    private ExportSummary exportSummary;
//    private CryosparcStatus cryosparcStatus;
    private CryosparcCompleteStatus cryosparcCompleteStatus;
    private Boolean gainExported;
    private String errorSummary;

    public boolean getGainExported() {
        return Optional.ofNullable(gainExported).orElse(false);
    }


    public String getTaskWorkDir(){
        return this.name.replaceAll("\\s+","_");
    }

    public String getOutputDir(){
        if(isCryosparc){
            return cryosparcSettings.getOutput_dir() + "/" + cryosparcSettings.getOutput_dir_tail();
        }else{
            return exportSettings.getOutput_dir() + "/" + exportSettings.getOutput_dir_tail();
        }
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public boolean isCryosparc() {
        return isCryosparc;
    }

    public void setCryosparc(boolean cryosparc) {
        isCryosparc = cryosparc;
    }
}
