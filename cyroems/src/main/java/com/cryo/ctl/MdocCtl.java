package com.cryo.ctl;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.io.file.FileNameUtil;
import com.cryo.dao.MDocInstanceRepository;
import com.cryo.dao.MDocResultRepository;
import com.cryo.dao.MovieResultRepository;
import com.cryo.dao.TaskRepository;
import com.cryo.dao.dataset.MDocRepository;
import com.cryo.dao.dataset.MovieDataSetRepository;
import com.cryo.model.MDocResult;
import com.cryo.model.MovieImage;
import com.cryo.model.MovieResult;
import com.cryo.model.Task;
import com.cryo.model.dataset.MDoc;
import com.cryo.model.dataset.MovieDataset;
import com.cryo.model.tilt.MDocInstance;
import com.cryo.service.session.SessionService;
import com.cryo.task.engine.HandlerKey;
import com.cryo.task.engine.TaskStep;
import com.cryo.task.movie.TaskStatistic;
import com.cryo.task.tilt.recon.AlignReconResult;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import net.dreamlu.mica.core.result.R;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Controller
public class MdocCtl
{

    private final TaskRepository taskRepository;
    private final MDocInstanceRepository mDocInstanceRepository;
    private final MongoTemplate mongoTemplate;
    private final TaskStatistic movieStatisticTask;
    private final MDocResultRepository mDocResultRepository;
    private final MovieDataSetRepository movieDataSetRepository;
    private final MovieResultRepository movieResultRepository;
    private final MDocRepository mDocRepository;
    private final SessionService sessionService;

    @GetMapping("/api/task/{taskId}/docs")
    @ResponseBody
    public Page<MdocOutput> docs(@PathVariable("taskId") String taskId,
                                 QueryInput movieQueryInput,
                                 Pageable pageable) {
        Query query = Query.query(Criteria.where("task_id").is(taskId));
        Task task = this.taskRepository.findById(taskId).orElseThrow(() -> new RuntimeException("Task not exist"));
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

//
//        List<MDocResult> movieResults = this.mDocResultRepository.findByQuery(Query.query(Criteria.where("task_data_id")
//                .is(task.getTaskSettings().getDataset_id()).and("config_id").is(task.getDefault_config_id()))
//        );
//        Map<String, MDocResult> movieResultMap = movieResults.stream().collect(Collectors.toMap(m -> m.getData_id(), m -> m));
//        List<MovieResult> movieResults = this.movieResultRepository.findByQuery(Query.query(Criteria.where("task_data_id")
//                .is(task.getTaskSettings().getDataset_id()).and("config_id").is(task.getDefault_config_id()))
//        );

        query.fields().exclude("meta");

        Page<MDocInstance> pageMovie = this.mDocInstanceRepository.findByQuery(query, pageable);
        List<String> movieDataIds = pageMovie.stream().map(m -> m.getData_id()).toList();
        Query query1 = Query.query(Criteria.where("data_id").in(movieDataIds).and("config_id").is(task.getConfig_id()));
        query1.fields().exclude("meta", "stackResult");

        List<MDocResult> movieResults = this.mDocResultRepository.findByQuery(query1);
        Map<String, MDocResult> movieResultMap = movieResults.stream().collect(Collectors.toMap(m -> m.getData_id(), Function.identity()));
        Page<MdocOutput> all = pageMovie
                .map(m -> {
                    MDocResult movieResult = movieResultMap.get(m.getData_id());
                    return new MdocOutput(m, movieResult);
                });
        return all;
    }

//    @PostMapping("/api/mdoc/{id}/restart")
//    @ResponseBody
//    @SaCheckLogin
//    public void restore(@PathVariable("id") String id) {
//        this.mDocInstanceRepository.restore(id);
//    }


    @PostMapping("/api/mdoc/restart")
    @ResponseBody
    @SaCheckLogin
    public void restore(@RequestBody IdsInput input) {
        if( input.ids.isEmpty() ) {
            return;
        }

        Optional<MDocInstance> byId = this.mDocInstanceRepository.findById(input.ids.get(0));
        if( byId.isPresent() ) {
            Task t = checkPermission(byId.get().getTask_id());
            this.mDocInstanceRepository.restore(input.ids);
            this.movieStatisticTask.statisticMDoc(t);
        } else {
            throw new RuntimeException("No task found");
        }
    }


    @GetMapping(value = "/api/mdoc/{id}/get_align_rec", produces = "application/marc"
    )
    public @ResponseBody byte[] getImage(@PathVariable String id, HttpServletResponse response) {
        MDocInstance mDocInstance = this.mDocInstanceRepository.findById(id).orElseThrow();
        Task task = this.taskRepository.findById(mDocInstance.getTask_id()).orElseThrow();
        Optional<MDocResult> byDataId = this.mDocResultRepository.findByDataId(mDocInstance.getData_id(), task.getConfig_id());
        if( byDataId.isPresent() ) {
            AlignReconResult alignReconResult = byDataId.get().getAlignReconResult();
            if( alignReconResult != null ) {
                String file = alignReconResult.getAlign_reconOutput();
                try {
                    String name = "inline; filename=" + FileNameUtil.getName(file);
                    response.setHeader("Content-Disposition", name);
                    return FileUtils.readFileToByteArray(new File(file));
                } catch( IOException e ) {
                    throw new RuntimeException(e);
                }
            }

        }
        throw new RuntimeException("file not exist");
    }


    @GetMapping(value = "/api/mdoc/result/{id}-{imageType}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public @ResponseBody byte[] getImage(@PathVariable String id, @PathVariable("imageType") MovieImage.Type imageType) {
        MDocResult movieResult = this.mDocResultRepository.findById(id).orElseThrow();
        String file;
        switch( imageType ) {
            case mdoc_recon_xy -> file = movieResult.getAlignReconResult().getAlign_recon_x_y_view();
            case mdoc_recon_yz -> file = movieResult.getAlignReconResult().getAlign_recon_y_z_view();
            case mdoc_recon_xz -> file = movieResult.getAlignReconResult().getAlign_recon_x_z_view();
            default -> throw new UnsupportedOperationException("");
        }
        try {
            return FileUtils.readFileToByteArray(new File(file));
        } catch( IOException e ) {
            throw new RuntimeException(e);
        }
    }

    @GetMapping(value = "/api/mdoc/{id}/movies"
    )
    public @ResponseBody Page<MovieOutput> getMovies(@PathVariable String id) {


        MDocInstance mDocInstance = this.mDocInstanceRepository.findById(id).orElseThrow();
        MDoc mDoc = this.mDocRepository.findById(mDocInstance.getData_id()).orElseThrow();
        Task task = this.taskRepository.findById(mDocInstance.getTask_id()).orElseThrow();
        if( mDoc.getMeta() == null ) {
            return Page.empty();
        }
        if( mDoc.getMeta().getTilts() == null ) {
            return Page.empty();
        }
//        mDoc.getMeta().getTilts();
//        if( mDocResult.isEmpty() ){
//            return Page.empty();
//        }
//        MDocResult result = mDocResult.get();
        List<String> movieDataIds = mDoc.getMeta().getTilts().stream().map(t -> t.getDataId()).filter(dataId -> dataId != null).toList();


        Page<MovieDataset> pageMovie = this.movieDataSetRepository.findByQuery(Query.query(Criteria.where("_id").in(movieDataIds)), Pageable.unpaged());
//        List<String> movieDataIds = pageMovie.stream().map(m -> m.getId()).toList();
        Query query1 = Query.query(Criteria.where("movie_data_id").in(movieDataIds).and("config_id").is(task.getConfig_id()));
        query1.fields().exclude("vfmResult.pointList");

        List<MovieResult> movieResults = this.movieResultRepository.findByQuery(query1);
        Map<String, MovieResult> movieResultMap = movieResults.stream()
                .collect(Collectors.toMap(MovieResult::getMovie_data_id, Function.identity(), MovieResult::pickNewer));
        Map<String, Double> angleMap = mDoc.getMeta().getTilts().stream().filter(t -> t.getDataId() != null).collect(Collectors.toMap(t -> t.getDataId(), t -> t.getTiltAngle()));
        List<String> selectedIds = mDoc.getMovie_data_ids();
        if( CollectionUtil.isEmpty(selectedIds) ) {
            selectedIds = movieDataIds;
        }
        List<String> finalSelectedIds = selectedIds;
        List<MovieOutput> all = pageMovie
                .map(m -> {
                    MovieResult movieResult = movieResultMap.get(m.getId());
                    Double angle = angleMap.get(m.getId());
                    MovieOutput movieOutput = new MovieOutput(m, movieResult);
                    movieOutput.setTiltAngle(angle);
                    movieOutput.setSelected(finalSelectedIds.contains(m.getId()));
                    return movieOutput;
                }).stream().sorted(Comparator.comparing(m -> m.getTiltAngle())).toList();
        return new PageImpl<>(all, Pageable.unpaged(), all.size());
    }

    @GetMapping(value = "/api/mdoc/{id}/result")
    public @ResponseBody MdocOutput getResult(@PathVariable String id) {
//
        MDocInstance movie = this.mDocInstanceRepository.findById(id).orElseThrow();
        Task task = this.taskRepository.findById(movie.getTask_id()).orElseThrow();
        Optional<MDocResult> movieResult = this.mDocResultRepository.findByDataId(movie.getData_id(), task.getDefault_config_id());
        return new MdocOutput(movie, movieResult.orElse(null));
    }

    @PostMapping(value = "/api/mdoc/result/{id}/rate")
    public @ResponseBody MDocResult rate(@PathVariable String id, @RequestBody RateInput rateInput) {
//
//        MDocInstance movie = this.mDocInstanceRepository.findById(id).orElseThrow();
//        Task task = this.taskRepository.findById(movie.getTask_id()).orElseThrow();
        MDocResult movieResult = this.mDocResultRepository.findById(id).orElseThrow();
        checkPermission(movieResult.getTask_id());
        movieResult.setRate(rateInput.rate);
        this.mDocResultRepository.save(movieResult);
        return movieResult;
    }

    private Task checkPermission(String taskId) {
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

    @PostMapping(value = "/api/mdoc/{id}/rebuild")
    public @ResponseBody MDocInstance rebuild(@PathVariable String id, @RequestBody RebuildInput input) {
//
//        MDocInstance movie = this.mDocInstanceRepository.findById(id).orElseThrow();
//        Task task = this.taskRepository.findById(movie.getTask_id()).orElseThrow();
//        MDocResult movieResult;
//        movieResult = this.mDocResultRepository.findById(id).orElseThrow();
//        movieResult.setRate(rateInput.rate);
//        this.mDocResultRepository.save(movieResult);
        MDocInstance mDocInstance = this.mDocInstanceRepository.findById(id).orElseThrow();
        checkPermission(mDocInstance.getTask_id());
        MDoc mDoc = this.mDocRepository.findById(mDocInstance.getData_id()).orElseThrow();
        mDoc.setMovie_data_ids(input.movie_data_ids);
        mDoc.setManualRebuild(true);
        this.mDocRepository.save(mDoc);
        this.mDocInstanceRepository.restore(List.of(id));
        return mDocInstance;
    }

    @Data
    public static class IdsInput
    {
        private List<String> ids;
    }

    @Data
    public static class QueryInput
    {
        private String status;
    }

    public static class MdocOutput
    {

        @JsonIgnore
        private final MDocInstance instance;
        @JsonUnwrapped
        private final MDocResult result;

        public MdocOutput(MDocInstance movie, MDocResult movieResult) {
            this.instance = movie;
            this.result = movieResult;
        }

        public String getStatus() {

            if( this.instance.getCurrent_step().getKey() == HandlerKey.FINISHED ) {
                return "processed";
            }
            if( Optional.ofNullable(this.instance.getError()).map(e -> e.getPermanent()).orElse(false) ) {
                return "error";
            }
            if( this.instance.getCurrent_step().getKey() != HandlerKey.MDocInit ) {
                return "processing";
            }
            return "unprocessed";

        }

        public String getStatusMessage() {
            R<Void> status = instance.getStatus();
            return status != null ? status.getMsg() : "Waiting for processing";
        }

        public TaskStep getCurrentStep() {
            return this.instance.getCurrent_step();
        }

        public String getIndex() {
            String fileName = instance.getName();
            String[] split = fileName.split("_");
            for( int i = split.length - 1; i >= 0; i-- ) {
                String s = split[i];
                if( NumberUtils.isDigits(s.substring(0, 1)) ) {
                    return s;
                }
            }
            return fileName;
        }

        public String getId() {
            return this.instance.getId();
        }


        public String getResultId() {
            return Optional.ofNullable(this.result).map(r -> r.getId()).orElse(null);
        }

        public String getName() {
            return this.instance.getName();
        }
    }

    @Data
    public static class MovieOutput
    {
        @JsonIgnore
        private final MovieDataset movieDataset;
        @JsonUnwrapped
        private final MovieResult movieResult;

        private Double tiltAngle;

        private boolean selected;

        public MovieOutput(MovieDataset movieDataset, MovieResult movieResult) {
            this.movieDataset = movieDataset;
            this.movieResult = movieResult;
        }

        public String getIndex() {
            String fileName = FileNameUtil.getName(movieDataset.getName());
            String[] split = fileName.split("_");
            for( int i = split.length - 1; i >= 0; i-- ) {
                String s = split[i];
                if( NumberUtils.isDigits(s.substring(0, 1)) ) {
                    return s;
                }
            }
            return fileName;
        }

        public String getId() {
            return Optional.ofNullable(this.movieResult).map(r -> r.getInstance_id()).orElse(null);
        }


        public String getResultId() {
            return Optional.ofNullable(this.movieResult).map(r -> r.getId()).orElse(null);
        }
    }

    @Data
    public static class RateInput
    {
        public int rate;
    }

    @Data
    public static class RebuildInput
    {
        public List<String> movie_data_ids;
    }

}
