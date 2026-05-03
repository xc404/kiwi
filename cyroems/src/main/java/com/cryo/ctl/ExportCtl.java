package com.cryo.ctl;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.cryo.dao.MovieResultRepository;
import com.cryo.dao.TaskRepository;
import com.cryo.dao.export.ExportMovieRepository;
import com.cryo.dao.export.ExportTaskRepository;
import com.cryo.model.Movie;
import com.cryo.model.Task;
import com.cryo.model.TaskStatus;
import com.cryo.model.export.ExportMovie;
import com.cryo.model.export.ExportSummary;
import com.cryo.model.export.ExportTask;
import com.cryo.service.ExportTaskService;
import com.cryo.service.TaskService;
import com.cryo.service.session.SessionService;
import com.cryo.task.engine.HandlerKey;
import com.cryo.task.engine.TaskStep;
import com.cryo.task.export.ErrorExportListener;
import com.cryo.task.export.ExportTaskVo;
import com.cryo.task.export.cryosparc.CryosparcService;
import com.cryo.task.movie.TaskStatistic;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.dreamlu.mica.core.result.R;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Controller
public class ExportCtl
{
    @AllArgsConstructor
    public static class ExportTaskSpace
    {
        public long used;
        public long request;
    }

    private final TaskRepository taskRepository;
    private final ExportTaskRepository exportTaskRepository;
    private final ExportMovieRepository movieRepository;
    private final TaskStatistic taskStatistic;
    private final MovieResultRepository movieResultRepository;
    private final SessionService sessionService;
    private final ExportTaskService exportTaskService;
    private final TaskService taskService;
    private final TaskScheduler taskScheduler;
    private final ErrorExportListener errorExportListener;
    private final CryosparcService cryosparcService;

    @GetMapping("/api/task/{taskId}/exports")
    @ResponseBody
    @SaCheckLogin
    public Page<ExportTaskOutput> getExports(@PathVariable("taskId") String taskId, Pageable pageable) {
        Task task = this.taskRepository.findById(taskId).orElseThrow();
        Query query = Query.query(Criteria.where("task_id").is(taskId));
        return this.exportTaskRepository.findByQuery(query, pageable).map(exportTask -> new ExportTaskOutput(task, exportTask));
    }


    @GetMapping("/api/export/{id}")
    @ResponseBody
    @SaCheckLogin
    public ExportTaskOutput getExport(@PathVariable("id") String id) {
        ExportTask exportTask = this.exportTaskRepository.findById(id).orElseThrow();
        Task task = this.taskRepository.findById(exportTask.getTask_id()).orElseThrow();
        return new ExportTaskOutput(task, exportTask);
    }


    @PostMapping("/api/export")
    @ResponseBody
    @SaCheckLogin
    public ExportTask createExport(@RequestBody ExportTask exportTask) {

        Task task = checkPermission(exportTask.getTask_id());
        if( task.isCleaned() ) {
            throw new RuntimeException("task has been cleaned");
        }
        this.exportTaskService.create(exportTask);
        ExportTaskVo exportTaskVo = new ExportTaskVo(task, exportTask);
        for( int i = 0; i < 90; i += 30 ) {

            this.taskScheduler.schedule(() -> {
                this.taskStatistic.statisticExport(exportTaskVo);
            }, Instant.now().plus(Duration.ofSeconds(i + 30)));
        }

        return exportTask;
    }

    @GetMapping("/api/export/{id}/estimateSpace")
    @ResponseBody
    public ExportTaskSpace getEstimateSpace(@PathVariable("id") String id) {
        return this.exportTaskService.getEstimateSpace(this.exportTaskRepository.findById(id).orElseThrow());
    }


    @GetMapping("/api/export/{id}/movies")
    @ResponseBody

    public Page<MovieOutput> movies(@PathVariable("id") String id,
                                    MovieQueryInput movieQueryInput,
                                    Pageable pageable) {
        Query query = Query.query(Criteria.where("task_id").is(id));
        query.fields().exclude("steps");

        if( StringUtils.isNotBlank(movieQueryInput.status) ) {
            switch( movieQueryInput.status ) {
                case "unprocessed":
                    query.addCriteria(Criteria.where("current_step.key").is(HandlerKey.INIT))
                            .addCriteria(Criteria.where("error.permanent").ne(true))
                            .addCriteria(Criteria.where("process_status.processing").ne(true))
                    ;
                    break;
                case "completed":
                case "processed":
                    query.addCriteria(Criteria.where("current_step.key").is(HandlerKey.FINISHED));
                    break;
                case "error":
                    query.addCriteria(Criteria.where("error.permanent").is(true));
                    break;
                case "processing":
                    query.addCriteria(Criteria.where("process_status.processing").is(true));
                case "all":
            }

        }


        Page<ExportMovie> pageMovie = this.movieRepository.findByQuery(query, pageable);

        Page<MovieOutput> all = pageMovie
                .map(m -> {
//                    MovieResult movieResult = movieResultMap.get(m.getMovie_data_id());
                    return new MovieOutput(m);
                });
        return all;
    }

    @PostMapping("/api/export/{id}/start")
    @ResponseBody
    @SaCheckLogin
    public ExportTask startTask(@PathVariable("id") String id) {
        Task task = checkExportPermission(id);
        return setTaskStatus(id, TaskStatus.running);
    }

    private Task checkExportPermission(String id) {
        ExportTask exportTask = this.exportTaskRepository.findById(id).orElseThrow();
        return this.checkPermission(exportTask.getTask_id());
    }

//    @PostMapping("/api/export/{id}/restart")
//    @ResponseBody
//    @SaCheckLogin
//    public ExportTask restartTask(@PathVariable("id") String id) {
//        checkExportPermission(id);
//        return setTaskStatus(id, TaskStatus.running);
//    }

    private ExportTask setTaskStatus(String id, TaskStatus taskStatus) {
        ExportTask task = this.taskService.setExportTaskStatus(id, taskStatus);
        return task;
    }

    @PostMapping("/api/export/{id}/stop")
    @ResponseBody
    @SaCheckLogin
    public ExportTask stopTask(@PathVariable("id") String id) {
        checkExportPermission(id);
        return setTaskStatus(id, TaskStatus.stopped);
    }

    @PostMapping("/api/export/{id}/complete")
    @ResponseBody
    @SaCheckLogin
    public ExportTask completeTask(@PathVariable("id") String id) {
        Task task = checkExportPermission(id);

        return setTaskStatus(id, TaskStatus.finished);
    }

    @PostMapping("/api/export/{id}/cryo-complete")
    @ResponseBody
    @SaCheckLogin
    public ExportTask completeCryosparcTask(@PathVariable("id") String id) {
        Task task = checkExportPermission(id);
        ExportTask exportTask = this.exportTaskRepository.findById(id).orElseThrow();
        this.cryosparcService.complete(new ExportTaskVo(task, exportTask), true);
        return exportTask;
    }


//    @PostMapping("/api/export/movie/{id}/restart")
//    @ResponseBody
//    @SaCheckLogin
//    public void restore(@PathVariable("id") String id) {
//        Movie movie = this.movieRepository.findById(id).orElseThrow();
//        checkPermission(movie.getTask_id());
//        this.movieRepository.restore(id);
//    }

    @PostMapping("/api/export/{id}/error/movies/restart")
    @ResponseBody
    @SaCheckLogin
    public void restartError(@PathVariable("id") String id) {
        ExportTask exportTask = this.exportTaskRepository.findById(id).orElseThrow();
        if( exportTask.isCryosparc() ) {
            if( exportTask.getCryosparcCompleteStatus() != null ) {
                this.completeCryosparcTask(id);
                return;
            }
        }
        Task task = checkPermission(exportTask.getTask_id());
        Query query = Query.query(Criteria.where("task_id").is(id));
        query.fields().exclude("steps");
        query.addCriteria(Criteria.where("error.permanent").is(true));
        List<ExportMovie> exportMovies = this.movieRepository.findByQuery(query);
        List<String> ids = exportMovies.stream().map(movie -> movie.getId()).toList();
        this.movieRepository.restore(ids);
        this.taskStatistic.statisticExport(new ExportTaskVo(task, exportTask));
    }


    @PostMapping("/api/export/movies/restart")
    @ResponseBody
    @SaCheckLogin
    public void restore(@RequestBody IdsInput input) {
        if( input.ids.isEmpty() ) {
            return;
        }
        Movie byId = this.movieRepository.findById(input.ids.get(0)).orElseThrow();
        ExportTask exportTask = this.exportTaskRepository.findById(byId.getTask_id()).orElseThrow();
        Task task = checkPermission(exportTask.getTask_id());
        this.movieRepository.restore(input.ids);
        this.taskStatistic.statisticExport(new ExportTaskVo(task, exportTask));
    }

    @PostMapping("/api/export/{id}/sendEmail")
    @ResponseBody
    @SaCheckLogin
    public void sendEmail(@PathVariable("id") String id) {
        ExportTask exportTask = this.exportTaskRepository.findById(id).orElseThrow();
        Task task = checkPermission(exportTask.getTask_id());

        // 示例：调用邮件服务发送邮件
        // 调用邮件服务（需确保邮件服务已在项目中配置）
        this.errorExportListener.sendErrorMail(task, exportTask);
    }


    private Task checkPermission(String taskId) {
//        ExportTask exportTask = this.exportTaskRepository.findById(taskId).orElseThrow();
        Optional<Task> task = this.taskRepository.findById(taskId);
        if( task.isPresent() ) {
            Task t = task.get();
            String owner = t.getOwner();
            if( !sessionService.isAdmin() && !owner.equals(sessionService.getSessionUser().getUser().getId()) ) {
                throw new RuntimeException("No permission");
            }
//            this.movieStatisticTask.statisticMovies(t);
        } else {
            throw new RuntimeException("No task found");
        }
        return task.get();
    }

    @Data
    public static class IdsInput
    {
        private List<String> ids;
    }

    @Data
    public static class MovieQueryInput
    {
        private String status;
    }

    public static class MovieOutput
    {

        @JsonUnwrapped
        private final Movie movie;
//        @JsonUnwrapped
//        private final MovieResult result;

        public MovieOutput(Movie movie) {
            this.movie = movie;
        }

        public String getStatus() {

            if( this.movie.getCurrent_step().getKey() == HandlerKey.FINISHED ) {
                return "processed";
            }
            if( Optional.ofNullable(this.movie.getError()).map(e -> e.getPermanent()).orElse(false) ) {
                return "error";
            }
            if( this.movie.getCurrent_step().getKey() != HandlerKey.INIT ) {
                return "processing";
            }
            return "unprocessed";

        }

        public String getStatusMessage() {
            R<Void> status = movie.getStatus();
            return status != null ? status.getMsg() : "Waiting for processing";
        }

        public TaskStep getCurrentStep() {
            return this.movie.getCurrent_step();
        }

        public String getIndex() {
            String fileName = movie.getFile_name();
            String[] split = fileName.split("_");
            for( int i = split.length - 1; i >= 0; i-- ) {
                String s = split[i];
                if( NumberUtils.isDigits(s.substring(0, 1)) ) {
                    return s;
                }
            }
            return fileName;
        }
    }


    public static class ExportTaskOutput
    {
        @JsonIgnore
        private final Task task;

        @JsonUnwrapped
        private final ExportTask exportTask;

        @Getter
        private ExportSummary exportSummary;

        public ExportTaskOutput(Task task, ExportTask exportTask) {
            this.task = task;
            this.exportTask = exportTask;

            if( exportTask.getExportSummary() != null ) {
                exportSummary = exportTask.getExportSummary();
            } else {
                Task.Statistic taskStatistic = exportTask.getMovie_statistic();
                if( taskStatistic == null ) {
                    exportSummary = new ExportSummary();

                } else {
                    exportSummary = ExportSummary.create(task, exportTask);

                    if( exportTask.getGainExported() ) {
                        exportSummary.setGain(new ExportSummary.Summary(2, 2, 0, 0));
                    }
                }


            }
        }

        public boolean isFinished() {
            return exportTask.getStatus() == TaskStatus.finished;
        }


    }

}
