package com.cryo.ctl;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.hutool.core.map.MapUtil;
import com.cryo.common.query.QueryField;
import com.cryo.common.query.QueryParam;
import com.cryo.common.query.QueryParams;
import com.cryo.dao.MDocInstanceRepository;
import com.cryo.dao.MovieRepository;
import com.cryo.dao.TaskRepository;
import com.cryo.dao.UserRepository;
import com.cryo.dao.dataset.TaskDataSetRepository;
import com.cryo.dao.export.ExportTaskRepository;
import com.cryo.model.Microscope;
import com.cryo.model.Task;
import com.cryo.model.TaskStatus;
import com.cryo.model.dataset.TaskDataset;
import com.cryo.model.export.ExportTask;
import com.cryo.model.settings.TaskDataSetSetting;
import com.cryo.model.user.User;
import com.cryo.service.ExportTaskService;
import com.cryo.service.TaskService;
import com.cryo.service.TaskSettingService;
import com.cryo.service.session.SessionService;
import com.cryo.service.session.SessionUser;
import com.cryo.task.clean.TaskCleaner;
import com.cryo.task.dataset.DatasetService;
import com.cryo.task.movie.TaskStatistic;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.dreamlu.mica.core.utils.DateUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Controller
public class TaskCtl
{
    private final TaskRepository taskRepository;
    private final SessionService sessionService;
    private final TaskService taskService;
    private final UserRepository userRepository;
    private final MovieRepository movieRepository;
    private final MDocInstanceRepository mDocInstanceRepository;
    private final TaskStatistic movieStatisticTask;
    private final TaskSettingService taskSettingService;
    private final TaskDataSetRepository taskDataSetRepository;
    private final TaskCleaner taskCleaner;
    private final ExportTaskService exportTaskService;
    private final ExportTaskRepository exportTaskRepository;
    private final DatasetService datasetService;

    @Data
    public static class QueryTaskInput
    {
        @QueryField(value = "created_at", op = QueryParam.QueryFieldOP.GTE)
        @DateTimeFormat(pattern = "yyyy-MM-dd")
        public Date start_time;
        @QueryField(value = "created_at", op = QueryParam.QueryFieldOP.LTE)
        @DateTimeFormat(pattern = "yyyy-MM-dd")
        public Date end_time;
        @QueryField(value = "owner", op = QueryParam.QueryFieldOP.IN)
        public List<String> owner;
        @QueryField(value = "status", op = QueryParam.QueryFieldOP.IN)
        public List<String> status;

        public Microscope microscope;
    }

    public static class TaskVo
    {

    }


    @GetMapping("/api/task/{id}")
    @ResponseBody
    public TaskOutput getTask(@PathVariable("id") String id) {
        Task task = this.taskRepository.findById(id).orElseThrow();

        List<String> userIds = new ArrayList<>();
        userIds.addAll(Optional.ofNullable(task.getCollaborators()).orElse(List.of()));
        userIds.addAll(Optional.ofNullable(task.getViewers()).orElse(List.of()));
        userIds.add(task.getOwner());
        Map<String, User> users = this.userRepository.findByQuery(Query.query(Criteria.where("id").in(userIds))).stream().collect(Collectors.toMap(u -> u.getId(), u -> u));
        ExportTask exportTask = this.exportTaskRepository.findById(ExportTaskService.getDefaultExportId(task)).orElse(null);

        return getTaskOutput(task, users, MapUtil.of(task.getId(), exportTask));
    }


    @SaCheckLogin
    @GetMapping("/api/tasks")
    @ResponseBody
    public Page<TaskOutput> tasks(QueryTaskInput queryTaskInput, Pageable pageable) {
        Query query = QueryParams.from(queryTaskInput).toMongo();
        if( queryTaskInput.status == null || queryTaskInput.status.isEmpty() ) {
            query.addCriteria(Criteria.where("status").ne(TaskStatus.archived));
        }
        SessionUser sessionUser = this.sessionService.getSessionUser();
//        query.skip(pageable.getOffset());
//        query.limit(pageable.toLimit());
        Criteria criteria = new Criteria();
//        if(queryTaskInput.owner== null || queryTaskInput.owner.isEmpty()){
//
//        }
        switch( sessionUser.getUser().getRole() ) {
            case "normal":
                criteria.orOperator(
                        Criteria.where("owner").is(sessionUser.getUser().getId()),
                        Criteria.where("collaborators").elemMatch(new Criteria().is(sessionUser.getUser().getId()))
//                        Criteria.where("viewers").elemMatch(new Criteria().is(sessionUser.getUser().getId())),
                );
                break;
            case "group_admin":
                criteria.orOperator(
                        Criteria.where("group_name").is(sessionUser.getUser().getUser_group()),
                        Criteria.where("owner").is(sessionUser.getUser().getId()),
                        Criteria.where("collaborators").elemMatch(new Criteria().is(sessionUser.getUser().getId()))
//                        Criteria.where("viewers").elemMatch(new Criteria().is(sessionUser.getUser().getId())),
                );
                break;
            case "admin", "viewer", "super_admin","device_admin":
                if( queryTaskInput.owner == null || queryTaskInput.owner.isEmpty() ) {
                    criteria.and("owner").exists(true);
                }
                break;
            default:
                throw new RuntimeException("Invalid user role");
        }
        query.addCriteria(criteria);
        query.with(Sort.by(Sort.Order.desc("created_at")));
        Page<Task> tasks = this.taskRepository.findByQuery(query, pageable);
        List<String> userIds = new ArrayList<>();
        tasks.getContent().forEach(task -> {
            userIds.addAll(Optional.ofNullable(task.getCollaborators()).orElse(List.of()));
            userIds.addAll(Optional.ofNullable(task.getViewers()).orElse(List.of()));
            userIds.add(task.getOwner());
        });
        Map<String, User> users = this.userRepository.findByQuery(Query.query(Criteria.where("id").in(userIds))).stream().collect(Collectors.toMap(u -> u.getId(), u -> u));
        List<String> exportIds = tasks.stream().map(task -> ExportTaskService.getDefaultExportId(task)).toList();
        Map<String, ExportTask> exportTasks = this.exportTaskRepository.findByQuery(Query.query(Criteria.where("id").in(exportIds))).stream().collect(Collectors.toMap(e -> e.getTask_id(), e -> e));
        return tasks.map(task -> {
            return getTaskOutput(task, users, exportTasks);
        });
    }

    private static TaskOutput getTaskOutput(Task task, Map<String, User> users, Map<String, ExportTask> exportTaskMap) {
        User user = users.get(task.getOwner());
        List<User> collaborators = Optional.ofNullable(task.getCollaborators()).orElse(List.of()).stream().map(u -> users.get(u)).toList();
        List<User> viewers = Optional.ofNullable(task.getViewers()).orElse(List.of()).stream().map(u -> users.get(u)).toList();
        ExportTask exportTask = exportTaskMap.get(task.getId());
        return new TaskOutput(task, user, collaborators, viewers, Optional.ofNullable(exportTask)
                .map(e -> e.getMovie_statistic()).orElse(null));
    }


    @PostMapping("/api/task")
    @ResponseBody
    @SaCheckLogin
    @SaCheckRole(value = {"admin", "group_admin", "normal"}, mode = SaMode.OR)
    public synchronized Task saveTask(@RequestBody Task task) {
        SessionUser sessionUser = sessionService.getSessionUser();
        TaskDataset dataset = this.taskDataSetRepository.findById(task.getTaskSettings().getDataset_id()).orElseThrow(() -> new RuntimeException("task dataset not found"));
        task.setIs_tomo(dataset.getIs_tomo());
        task.setStatus(TaskStatus.stopped);
        if( datasetService.isPermissionEnabled() && StringUtils.isNotBlank(dataset.getOwner()) && !Objects.equals(dataset.getOwner(), sessionUser.getUser().getId()) ) {
            throw new RuntimeException("task dataset not owned by you");
        }
        boolean create = StringUtils.isBlank(task.getId());
        if( create ) {
            task.setOwner(sessionUser.getUser().getId());
            task.setGroup_name(sessionUser.getUser().getUser_group());
            task.setGroup_id(sessionUser.getUser().getGroup_id());
            task.setMovie_path(dataset.getMovie_path());
            task.setWork_dir(task.getTask_name().replace(" ", "_") + DateUtil.format(new Date(), "-yyyyMMddHHmmss"));
            task.setBelong_user(sessionUser.getUser().getId());
//            task.setId(IdUtil.fastSimpleUUID());
            task.setInput_dir(dataset.getMovie_path());
            task.setMicroscope(dataset.getMicroscope());

        } else {
            checkPermission(task.getId());
        }
        if( dataset.getTaskDataSetSetting() != null ) {
            if( !Objects.equals(dataset.getTaskDataSetSetting(), task.getTaskSettings().getTaskDataSetSetting()) ) {
                throw new RuntimeException("不能修改（Pixel Size, Total Dose per Movie），请联系管理员");
            }
        }

        onTaskSettingChange(task);
        if( dataset.getConfig_id() == null ) {
            updateDataset(task);
        } else if( create && datasetService.isPermissionEnabled() ) {
            // 新建 task 时，将 dataset 的 owner 更新为当前用户
            dataset.setOwner(task.getOwner());
            this.taskDataSetRepository.save(dataset);
        }

        Task save = this.taskRepository.save(task);
        if( create ) {
            createDefaultExport(save);
        }
        return save;
    }

    private void createDefaultExport(Task save) {
        this.exportTaskService.createDefaultExportTask(save);
    }

    private void onTaskSettingChange(Task task) {
        // set config_id;
        task.setDefault_config_id(task.getTaskSettings().getDataset_id());
        task.setConfig_id(task.getTaskSettings().getDataset_id());

//        TaskSettings defaultTaskSettings = this.taskSettingService.getDefaultTaskSettings(task);
////            defaultTaskSettings.setTaskDataSetSetting(task.getTaskSettings().getTaskDataSetSetting());
////            defaultTaskSettings.setDataset_id(dataset.getId());


//        List<Task> taskList = this.taskRepository.findByDataSetId(task.getTaskSettings().getDataset_id()); //contain self;
//        Optional<Task> defaultEquals = taskList.stream().filter(t -> {
//            return t.getTaskSettings().getTaskDataSetSetting().equals(task.getTaskSettings().getTaskDataSetSetting());
//        }).findFirst();
//        defaultEquals.ifPresent(value -> task.setDefault_config_id(value.getDefault_config_id()));
//        Optional<Task> taskEquals = taskList.stream().filter(t -> {
//            return t.getTaskSettings().equals(task.getTaskSettings());
//        }).findFirst();
//
//        taskEquals.ifPresent(value -> task.setConfig_id(value.getConfig_id()));
//
//        if( task.getConfig_id() == null ) {
//            task.setConfig_id(IdUtil.fastSimpleUUID());
//        }
//        if( task.getDefault_config_id() == null ) {
//            if( task.getTaskSettings().settingsEquals(defaultTaskSettings) ) {
//                task.setDefault_config_id(task.getConfig_id());
//            } else {
//                task.setDefault_config_id(IdUtil.fastSimpleUUID());
//            }
//        }
    }

    private void updateDataset(Task task) {
        TaskDataset update = new TaskDataset();
        update.setId(task.getTaskSettings().getDataset_id());
        update.setConfig_id(task.getDefault_config_id());
        update.setTaskDataSetSetting(task.getTaskSettings().getTaskDataSetSetting());
        if( datasetService.isPermissionEnabled() ) {
            update.setOwner(task.getOwner());
        }
        this.taskDataSetRepository.save(update);
    }

    @PostMapping("/api/task/{id}/dataset_setting")
    @ResponseBody
    @SaCheckLogin
    @SaCheckRole("admin")
    public Task updateDataset(@PathVariable("id") String id, @RequestBody TaskDataSetSetting dataSetSetting) {

        Task task = this.taskRepository.findById(id).orElseThrow();
        TaskDataset dataset = this.taskDataSetRepository.findById(task.getTaskSettings().getDataset_id()).orElseThrow();
        if( Objects.equals(dataset.getTaskDataSetSetting(), dataSetSetting) ) {
            throw new RuntimeException("参数值没有变化");
        }
        task.getTaskSettings().setTaskDataSetSetting(dataSetSetting);
        onTaskSettingChange(task);
        this.taskRepository.save(task);
        updateDataset(task);
        return task;
    }

    @PostMapping("/api/task/{id}/start")
    @ResponseBody
    @SaCheckLogin
    public Task startTask(@PathVariable("id") String id) {
        Task task = checkPermission(id);

        List<Task> runningTasks = this.taskService.getRunningTasks();
        Optional<Task> exist = runningTasks.stream().filter(t -> t.getTaskSettings().getDataset_id().equals(task.getTaskSettings().getDataset_id())).findFirst();
        if( exist.isPresent() ) {
            throw new RuntimeException("已有相同数据集的任务" + exist.get().getTask_name() + "在运行");
        }
        TaskDataset dataset = this.taskDataSetRepository.findById(task.getTaskSettings().getDataset_id()).orElseThrow();
        if( !Objects.equals(dataset.getTaskDataSetSetting(), task.getTaskSettings().getTaskDataSetSetting()) ) {
            throw new RuntimeException("该数据集的默认参数已改变");
        }
        if( !new File(dataset.getMovie_path()).exists() ) {
            throw new RuntimeException("数据集目录已失效");
        }
        if( dataset.getGain0() == null || StringUtils.isBlank(dataset.getGain0().getUsable_path()) ) {

            throw new RuntimeException("gain文件不存在,请稍后再试");
        }
        if( !new File(dataset.getGain0().getUsable_path()).exists() ) {
            throw new RuntimeException("gain文件已失效");
        }
        return setTaskStatus(id, TaskStatus.running);
    }

    @PostMapping("/api/task/{id}/restart")
    @ResponseBody
    @SaCheckLogin
    public Task restartTask(@PathVariable("id") String id) {
        checkPermission(id);
        return setTaskStatus(id, TaskStatus.running);
    }

    @PostMapping("/api/task/{id}/stop")
    @ResponseBody
    @SaCheckLogin
    public Task stopTask(@PathVariable("id") String id) {
        checkPermission(id);
        return setTaskStatus(id, TaskStatus.stopped);
    }

    @PostMapping("/api/task/{id}/finish")
    @ResponseBody
    @SaCheckLogin
    public Task finishTask(@PathVariable("id") String id) {
        checkPermission(id);
        return setTaskStatus(id, TaskStatus.finished);
    }

    @PostMapping("/api/task/{id}/archive")
    @ResponseBody
    @SaCheckLogin
    public Task archiveTask(@PathVariable("id") String id) {
        checkPermission(id);
        return setTaskStatus(id, TaskStatus.archived);
    }


    @PostMapping("/api/task/{id}/restore_movies")
    @ResponseBody
    @SaCheckLogin
    public void restoreErrorMovies(@PathVariable("id") String id) {
        Task task = checkPermission(id);
//        Gain gain = this.gainRepository.getGainByTask(id).orElse(null);
//        if (gain != null && gain.getGain_conversion_status() == GainConvertStatus.completed) {
//            if(!gain.isExported()){
//                gainTask.exportGain(task,gain);
//                this.gainRepository.save(gain);
//            }
//        }
//        if( !task.getGainExported() ) {
//            gainTask.exportGain(task);
//        }
        this.movieRepository.continueByTaskId(id);
        this.mDocInstanceRepository.continueByTaskId(id);
        this.movieStatisticTask.statisticTask(task);
    }


    @PostMapping("/api/task/{id}/restore")
    @ResponseBody
    @SaCheckLogin
    public Task restoreTask(@PathVariable("id") String id) {
        return setTaskStatus(id, TaskStatus.stopped);
    }

    private Task setTaskStatus(String task_id, TaskStatus taskStatus) {
        Task task = taskService.setTaskStatus(task_id, taskStatus);
        return task;
    }


    @PostMapping("/api/task/{id}/clean")
    @ResponseBody
    public Task cleanTask(@PathVariable("id") String id) {
        Task task = this.taskRepository.findById(id).orElseThrow();


        this.taskCleaner.cleanTask(task);
        return task;
    }

    @PostMapping("/api/task/clean")
    @ResponseBody
    public void clean() {
        this.taskCleaner.clean();
    }


//    @PostMapping("/api/task/{id}/finish")
//    @ResponseBody
//    public void export(@PathVariable("id") String id) {
//        Task task = this.taskRepository.findById(id).orElseThrow();
//        this.taskService.completeTask(task);
//    }


    private Task checkPermission(String taskId) {
        Optional<Task> task = this.taskRepository.findById(taskId);
        if( task.isPresent() ) {
            Task t = task.get();
            String owner = t.getOwner();
            if( !sessionService.isAdmin() && !owner.equals(sessionService.getSessionUser().getUser().getId()) ) {
                throw new RuntimeException("No permission");
            }
            this.movieStatisticTask.statisticMovies(t);
        } else {
            throw new RuntimeException("No task found");
        }
        return task.get();
    }

    public static class TaskIdInput
    {
        public String task_id;
    }

    @Getter
    @RequiredArgsConstructor
    public static class TaskOutput
    {
        @JsonUnwrapped
        private final Task task;
        private final User owner;
        private final List<User> collaborators;
        private final List<User> viewers;
        private final Task.Statistic exportStatistic;
    }


}
