package com.cryo.ctl.test;

import com.cryo.common.mongo.MongoTemplate;
import com.cryo.model.Task;
import com.cryo.model.TaskStatus;
import com.cryo.model.dataset.TaskDataset;
import com.cryo.model.export.ExportTask;
import com.cryo.task.movie.handler.motion.MotionCor2;
import lombok.RequiredArgsConstructor;
import net.dreamlu.mica.core.result.R;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class MovieTestCtl
{
//    private final GainRepository gainRepository;
//    private final MovieRepository movieRepository;
//    private final TaskRepository taskRepository;
    /// /    private final InstanceEngine instanceEngine;
//    private final FlowManager flowManager;
//    private final TaskDataSetRepository taskDataSetRepository;

    @Autowired()
    @Qualifier("datasetMongoTemplate")
    private MongoTemplate mongoTemplate;

    @PostMapping("export/view")
    @ResponseBody
    public R test1() {
        List<ExportTask> exportTasks = mongoTemplate.findAll(ExportTask.class);
        List<ExportTask> finishedTasks = exportTasks.stream().filter(t -> t.getStatus() == TaskStatus.finished).toList();
        StringBuilder stringBuilder = new StringBuilder();
        finishedTasks.forEach(f -> {
            Task task = this.mongoTemplate.findById(f.getTask_id(), Task.class);
            TaskDataset taskDataset = this.mongoTemplate.findById(task.getTaskSettings().getDataset_id(), TaskDataset.class);
            boolean success = true;
            if( taskDataset.getMovies_count() != f.getMovie_statistic().getTotal() ) {
                success = false;
            }
            stringBuilder.append(StringUtils.join(new String[]{f.getId(), String.valueOf(f.getMovie_statistic().getTotal())
                    , String.valueOf(taskDataset.getMovies_count()),
                    String.valueOf(success)
            }, ",")).append("\n");
        });
        return R.success(stringBuilder.toString());
    }
//
//    @PostMapping("/test/movie/{id}/next")
//    @ResponseBody
//    public R next(@PathVariable("id") String id) {
//        return _next(id, true);
//    }
//
//    private R _next(String id, boolean autoNext) {
//        Movie movie = this.movieRepository.findById(id).orElse(null);
//        if( movie == null ) {
//            throw new RuntimeException("movie not exist");
//        }
//        Gain gain = this.gainRepository.getGainByTask(movie.getTask_id()).orElse(null);
//        if( gain == null ) {
//            throw new RuntimeException("gain not exist");
//        }
//
//        Task task = this.taskRepository.findById(movie.getTask_id()).orElse(null);
//        if( task == null ) {
//            throw new RuntimeException("task not exist");
//        }
//        TaskDataset taskDataset = this.taskDataSetRepository.findById(task.getTaskSettings().getDataset_id()).orElse(null);
//        IFlow iFlow = this.flowManager.getFlow(task, taskDataset);
//        MovieContext movieContext = new MovieContext(null, taskDataset, iFlow, task, gain, movie);
//        if( autoNext ) {
//            return movieProcessor.next(movieContext);
//        } else {
//            return movieProcessor.step(movieContext);
//        }
//    }

    private final MotionCor2 motionCor2;

    @GetMapping("/api/test")
    @ResponseBody
    public Map test() {
        return Map.of(
                "defectFilePath", motionCor2.getDefectFilePath(),
                "motionVersion", motionCor2.getMotionVersion(),
                "ruijinEnabled", motionCor2.isRuijinEnabled()
        );
    }

}
