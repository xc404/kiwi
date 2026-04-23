package com.cryo.model;

import com.cryo.common.model.DataEntity;
import com.cryo.model.settings.ImodSetting;
import com.cryo.model.settings.TaskSettings;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@EqualsAndHashCode(callSuper = true)
@Data
public class Task extends DataEntity
{

//    public static final String DEFAULT_TASK = "default";
//    public static final String SELF_TASK = "self";

    private String task_name;

    @Hidden
    private String config_id;
    @Hidden
    private String default_config_id;

    private String output_dir;
    private String output_dir_tail;

    private TaskSettings taskSettings;
//    private ExportSettings exportSettings;

//    private ImodSetting etSettings;

    /**
     * --------- deprecated----------
     **/
    @Hidden
    private String input_dir;
    @Hidden
//    @Deprecated
    private String microscope;  // 存 microscope_key，如 "Titan1_k3"
    private Boolean is_tomo;
    /**
     * --------- deprecated----------
     **/


    private String notes;

    private String owner;
    @Hidden
    private String belong_user;
    @Hidden
    private String group_name;
    private String group_id;    // 关联 Group._id

    private List<String> collaborators;
    private List<String> viewers;
    private TaskStatus status;
    @Hidden
    private String flow_name;
    @Hidden
    private Date last_detect_time;
    private Statistic movie_statistic;
    private Statistic export_statistic;
    private Statistic mdoc_statistic;
    @Hidden
    private String work_dir;
    @Hidden
    private Boolean gainExported;
    @Hidden
    private boolean cleaned;
    private String movie_path;


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Statistic
    {
        private Long total;
        private Long processed;
        private Long unprocessed;
        private Long processing;
        private Long error;
        private Date check_time;

        @Override
        public boolean equals(Object o) {
            if( !(o instanceof Statistic that) ) {
                return false;
            }
            return Objects.equals(getTotal(), that.getTotal()) && Objects.equals(getProcessed(), that.getProcessed()) && Objects.equals(getUnprocessed(), that.getUnprocessed()) && Objects.equals(getProcessing(), that.getProcessing()) && Objects.equals(getError(), that.getError());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getTotal(), getProcessed(), getUnprocessed(), getProcessing(), getError());
        }
    }


    public boolean equalsDefault() {
        return Objects.equals(this.config_id, default_config_id);
    }


    @Deprecated
    @JsonIgnore
    public boolean getGainExported() {
        return Optional.ofNullable(gainExported).orElse(false);
    }

    @JsonIgnore

    public ImodSetting getEtSettings() {
        return Optional.ofNullable(this.taskSettings).map(t -> t.getEtSettings()).map(t -> {
            return t.getImod();
        }).orElse(new ImodSetting());
    }


    public boolean getIs_tomo() {
        return Optional.ofNullable(is_tomo).orElse(false);
    }

    public boolean completed() {
        return this.status == TaskStatus.finished || this.status == TaskStatus.archived;
    }

    public String getDefaultOutputDir() {
        return this.output_dir + "/" + this.output_dir_tail;
    }
}
