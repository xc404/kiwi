package com.cryo.ctl;

import cn.hutool.core.io.file.FileNameUtil;
import com.cryo.common.query.QueryField;
import com.cryo.common.query.QueryParam;
import com.cryo.common.query.QueryParams;
import com.cryo.dao.UserRepository;
import com.cryo.dao.dataset.MDocRepository;
import com.cryo.dao.dataset.MovieDataSetRepository;
import com.cryo.dao.dataset.TaskDataSetRepository;
import com.cryo.model.dataset.MDoc;
import com.cryo.model.dataset.MovieDataset;
import com.cryo.model.dataset.TaskDataset;
import com.cryo.model.settings.TaskDataSetSetting;
import com.cryo.model.user.User;
import com.cryo.service.session.SessionService;
import com.cryo.service.session.SessionUser;
import com.cryo.task.dataset.DataMonitor;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@Slf4j
public class DatasetCtl
{
    private final TaskDataSetRepository taskDataSetRepository;

    @Autowired(required = false)
    private final DataMonitor dataMonitor;
    @Autowired
    private final SessionService sessionService;
    private final UserRepository userRepository;
    private final MovieDataSetRepository movieDataSetRepository;
    private final MDocRepository mDocRepository;

    public DatasetCtl(TaskDataSetRepository taskDataSetRepository, @Autowired(required = false) DataMonitor dataMonitor, SessionService sessionService, UserRepository userRepository, MovieDataSetRepository movieDataSetRepository, MDocRepository mDocRepository) {
        this.taskDataSetRepository = taskDataSetRepository;
        this.dataMonitor = dataMonitor;
        this.sessionService = sessionService;
        this.userRepository = userRepository;
        this.movieDataSetRepository = movieDataSetRepository;
        this.mDocRepository = mDocRepository;
    }

    public static class QueryDataSetInput
    {
        @QueryField(value = "movie_path", op = QueryParam.QueryFieldOP.LIKE)
        public String name;
    }


    @GetMapping("/api/dataset")
    @ResponseBody
    public Page<TaskDatasetOutput> taskDatasets(QueryDataSetInput input, Pageable pageable) {

        Query query = QueryParams.from(input).toMongo();
//        if( input.status == null || input.status.isEmpty() ) {
//            query.addCriteria(Criteria.where("status").ne(TaskStatus.archived));
//        }
        SessionUser sessionUser = this.sessionService.getSessionUser();
        query.skip(pageable.getOffset());
        query.limit(pageable.toLimit());
        Criteria criteria = new Criteria();
//        if(queryTaskInput.owner== null || queryTaskInput.owner.isEmpty()){
//
//        }
        switch( sessionUser.getUser().getRole() ) {
            case "normal":
                criteria.orOperator(
                        Criteria.where("owner").exists(false),
                        Criteria.where("owner").is(sessionUser.getUser().getId()),
                        Criteria.where("collaborators").elemMatch(new Criteria().is(sessionUser.getUser().getId()))
                );
                break;
            case "group_admin":
                criteria.orOperator(
                        Criteria.where("group_name").is(sessionUser.getUser().getUser_group()),
                        Criteria.where("owner").is(sessionUser.getUser().getId()),
                        Criteria.where("collaborators").elemMatch(new Criteria().is(sessionUser.getUser().getId()))
                );
                break;
            case "admin","super_admin", "device_admin":
                break;
            default:
                throw new RuntimeException("Invalid user role");
        }
        query.addCriteria(criteria);
        query.with(Sort.by(Sort.Order.desc("created_at")));
        Page<TaskDataset> taskDatasets = this.taskDataSetRepository.findByQuery(query, pageable);
        List<String> userIds = new ArrayList<>();
        taskDatasets.getContent().forEach(task -> {
            userIds.addAll(Optional.ofNullable(task.getCollaborators()).orElse(List.of()));
            userIds.add(task.getOwner());
        });
        Map<String, User> users = this.userRepository.findByQuery(Query.query(Criteria.where("id").in(userIds))).stream().collect(Collectors.toMap(u -> u.getId(), u -> u));
        return taskDatasets.map(taskDataset -> {
            return getOutput(taskDataset, users);
        });
    }

    @PostMapping("/api/dataset")
    @ResponseBody
    public TaskDataset updateDatasets(@RequestBody UpdateDatasetInput taskDataset) {
        TaskDataset dataset = new TaskDataset();
        BeanUtils.copyProperties(taskDataset, dataset);
        this.taskDataSetRepository.save(dataset);
        return dataset;
    }


    @PostMapping("/api/dataset/sync")
    @ResponseBody
    public void syncDatasets() {
        this.dataMonitor.syncDataset();
    }

    private static TaskDatasetOutput getOutput(TaskDataset taskDataset, Map<String, User> users) {
        User user = users.get(taskDataset.getOwner());
        List<User> collaborators = Optional.ofNullable(taskDataset.getCollaborators()).orElse(List.of()).stream().map(u -> users.get(u)).toList();
        return new TaskDatasetOutput(taskDataset, user, collaborators);
    }

    @PostMapping("/api/dataset/{dataSetId}/patch_movies")
    @ResponseBody
    private void patch_movies(@PathVariable String dataSetId, @RequestBody TaskDataset.Gain gainInfo) throws IOException {

        TaskDataset taskDataset = this.taskDataSetRepository.findById(dataSetId).orElseThrow();
        patchMovie(taskDataset);
        patchMdoc(taskDataset);
        if (gainInfo != null && StringUtils.isNotBlank(gainInfo.getPath())) {
            patchGain(taskDataset, gainInfo);
        }
    }

    private void patchGain(TaskDataset taskDataset, TaskDataset.Gain gainInfo) {
        File file = new File(taskDataset.getRaw_path());
        //
        File[] gains = FileUtils.listFiles(file, new String[]{"gain", "dm4", "mrc"}, false).toArray(new File[0]);
        List<TaskDataset.Gain> gainList = Optional.ofNullable(taskDataset.getGain()).orElse(List.of());
        for( File g : gains ) {
            boolean exist = gainList.stream().anyMatch(gain -> gain.getPath().equals(g.getAbsolutePath()));
            if( exist ) {
                continue;
            }
            if (g.getAbsolutePath().equals(gainInfo.getPath())) {
                TaskDataset.Gain gain = new TaskDataset.Gain();
                BeanUtils.copyProperties(gainInfo, gain);
                gain.setPath(g.getAbsolutePath());
                gain.setUsable_path(g.getAbsolutePath());
                gain.setMtime(com.cryo.common.utils.FileUtils.lastModified(g));
                gain.setCreated_at(new Date());
                taskDataset.getGain().add(gain);
                log.info("Patched gain {}", g.getAbsolutePath());
                this.taskDataSetRepository.save(taskDataset);
                break;
            }
        }
        log.error("data_id {}: {} gains file did not exist in raw path for patch", taskDataset.getId(), gainInfo.getPath());
    }


    private void patchMovie(TaskDataset taskDataset) {
        int counter = 0;
        File file = new File(taskDataset.getMovie_path());
        //
        File[] movies = FileUtils.listFiles(file, new String[]{"eer", "tif", "tiff"}, false).toArray(new File[0]);
        for( File m : movies ) {
            Query query = Query.query(Criteria.where("belonging_data").in(new ObjectId(taskDataset.getId()), taskDataset.getId())
                    .and("path").is(m.getAbsolutePath()));
            List<MovieDataset> movieDatasets = this.movieDataSetRepository.findByQuery(query);
            if( !movieDatasets.isEmpty() ) {
                continue;
            }
            MovieDataset movie = new MovieDataset();
            movie.setBelonging_data(taskDataset.getId());
            movie.setPath(m.getAbsolutePath());
            movie.setName(FileNameUtil.getPrefix(m));
            movie.setMtime(com.cryo.common.utils.FileUtils.lastModified(m));
            movie.setCreated_at(new Date());
            this.movieDataSetRepository.insert(movie);
            counter += 1;
            log.info("Patched movie {}, Total patched {}", m.getAbsolutePath(), counter);
        }
    }

    private void patchMdoc(TaskDataset taskDataset) {
        int counter = 0;
        File file = new File(taskDataset.getMovie_path());
        //
        File[] movies = FileUtils.listFiles(file, new String[]{"mdoc"}, false).toArray(new File[0]);
        for( File m : movies ) {
            Query query = Query.query(Criteria.where("belonging_data").in(new ObjectId(taskDataset.getId()), taskDataset.getId())
                    .and("path").is(m.getAbsolutePath()));
            List<MDoc> mDocs = this.mDocRepository.findByQuery(query);
            if( !mDocs.isEmpty() ) {
                continue;
            }
            MDoc mDoc = new MDoc();
            mDoc.setBelonging_data(taskDataset.getId());
            mDoc.setPath(m.getAbsolutePath());
            mDoc.setName(FileNameUtil.getPrefix(m));
            mDoc.setMtime(com.cryo.common.utils.FileUtils.lastModified(file));
            mDoc.setCreated_at(new Date());
            this.mDocRepository.insert(mDoc);
            counter += 1;
            log.info("Patched mdoc {}, Total patched {}", m.getAbsolutePath(), counter);
        }
    }


    public static class UpdateDatasetInput
    {
        private String owner;
        private String group;
        private List<String> collaborators;
        private String config_id;
        private TaskDataSetSetting taskDataSetSetting;
    }

    @Getter
    @RequiredArgsConstructor
    public static class TaskDatasetOutput
    {
        @JsonUnwrapped
        private final TaskDataset taskDataset;
        private final User owner;
        private final List<User> collaborators;
    }
}
