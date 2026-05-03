package com.cryo.model.export;

import com.cryo.model.Task;
import com.cryo.model.settings.ExportSettings;
import com.cryo.task.movie.TaskStatistic;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.Optional;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class ExportSummary
{
    public static final Summary Empty = new Summary(0, 0, 0,0);

    @Data
    @AllArgsConstructor
    @ToString
    @NoArgsConstructor
    public static class Summary
    {
        private long total;
        private long completed;
        private long error;
        private long processing;
    }

    //    private long total;
    private Summary movie = Empty;
    private Summary  gain = Empty;
    private Summary dw = Empty;
    private Summary noDw = Empty;
    private Summary ctf = Empty;
    private Summary vfm = Empty;
    private Summary mdoc = Empty;
//    private long totalVfm;
//    private long error;

    public static ExportSummary create(Task task, ExportTask exportTask) {
        ExportSettings exportSettings = exportTask.getExportSettings();
        ExportSummary exportSummary = new ExportSummary();
        Task.Statistic statistic = Optional.ofNullable(exportTask.getMovie_statistic()).orElse(TaskStatistic.empty);
        Summary s = new Summary(statistic.getTotal(), statistic.getProcessed(), statistic.getError(), statistic.getProcessing());
        if( exportSettings.isExportRawMovie() || exportTask.isCryosparc()) {
            exportSummary.setMovie(s);
        }
        if( exportSettings.isExportGain() && !exportTask.isCryosparc() ) {
            exportSummary.setGain(new Summary(2, 0, 0,0));
        }
        if( exportSettings.isExportMotionDw()  && !exportTask.isCryosparc() ) {
            exportSummary.setDw(s);
        }
        if( exportSettings.isExportMotion() || exportTask.isCryosparc()) {
            exportSummary.setNoDw(s);
        }
        if( exportSettings.isExportCTF() || exportTask.isCryosparc()) {
            exportSummary.setCtf(s);
        }
        if( exportTask.isCryosparc() || exportSettings.isExportVfm() ) {
            exportSummary.setVfm(s);
        }
        if( task.getIs_tomo() ) {
            statistic = Optional.ofNullable(exportTask.getMdoc_statistic()).orElse(TaskStatistic.empty);
            s = new Summary(statistic.getTotal(), statistic.getProcessed(), statistic.getError(), statistic.getProcessing());
            exportSummary.setMdoc(s);
        }

        return exportSummary;
    }
}