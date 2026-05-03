package com.cryo.task.detect;

import cn.hutool.core.io.file.FileNameUtil;
import com.cryo.dao.MDocInstanceRepository;
import com.cryo.dao.TaskRepository;
import com.cryo.dao.dataset.MDocRepository;
import com.cryo.dao.export.ExportTaskRepository;
import com.cryo.model.Task;
import com.cryo.model.dataset.MDoc;
import com.cryo.model.export.ExportTask;
import com.cryo.model.tilt.MDocInstance;
import com.cryo.service.ExportMovieService;
import com.cryo.task.engine.HandlerKey;
import com.cryo.task.engine.TaskStep;
import com.cryo.task.export.ExportTaskVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dreamlu.mica.core.result.R;
import org.apache.commons.lang3.time.DateUtils;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service

@RequiredArgsConstructor
@Slf4j
public class MDocDetectorSupport
{
    private final MDocRepository mDocRepository;
    private final MDocInstanceRepository mDocInstanceRepository;
    private final TaskRepository taskRepository;
    private final ExportTaskRepository exportTaskRepository;
    private final ExportMovieService exportMovieService;

    public void detect(Task task) {
        Date lastDetectTime = task.getLast_detect_time();
        if( lastDetectTime == null ) {
            lastDetectTime = new Date(0);
        } else {
            lastDetectTime = DateUtils.addMinutes(lastDetectTime, -5);
        }
//        Date date = new Date();
        Query query = Query.query(Criteria.where("belonging_data").in(new ObjectId(task.getTaskSettings().getDataset_id()), task.getTaskSettings().getDataset_id()));
        List<MDoc> mdocData = this.mDocRepository.findByQuery(query.addCriteria(Criteria.where("created_at").gt(lastDetectTime)));
        if( mdocData.isEmpty() ) {
            return;

        }
        mdocData.sort(Comparator.comparing(MDoc::getCreated_at, Comparator.nullsFirst(Comparator.naturalOrder())));
        mdocData.forEach(movieDataset -> {
            if( movieDataset.getName().contains("override") ) {
                return;
            }
            saveMdoc(task, movieDataset);
        });
    }

    public void detectExport(ExportTaskVo exportTaskVo) {
        ExportTask task = exportTaskVo.getExportTask();
        Date lastDetectTime = task.getLast_detect_time();
        if( lastDetectTime == null ) {
            lastDetectTime = new Date(0);
        } else {
            lastDetectTime = DateUtils.addMinutes(lastDetectTime, -5);
        }
//        Date date = new Date();
        Query query = Query.query(Criteria.where("belonging_data").in(new ObjectId(exportTaskVo.getTask().getTaskSettings().getDataset_id()), exportTaskVo.getTask().getTaskSettings().getDataset_id()));
        List<MDoc> mdocData = this.mDocRepository.findByQuery(query.addCriteria(Criteria.where("created_at").gt(lastDetectTime)));
        if( mdocData.isEmpty() ) {
            return;

        }
        mdocData.sort(Comparator.comparing(MDoc::getCreated_at, Comparator.nullsFirst(Comparator.naturalOrder())));
        mdocData.forEach(movieDataset -> {
            if( movieDataset.getName().contains("override") ) {
                return;
            }
            createExportMovie(exportTaskVo.getTask(), task, movieDataset);
        });
    }


    private void saveMdoc(Task task, MDoc movieDataset) {
        Optional<MDocInstance> byDataId = this.mDocInstanceRepository.findByDataId(task.getId(), movieDataset.getId());
        if( byDataId.isPresent() ) {
            return;
        }
        String file = movieDataset.getPath();
        MDocInstance movie = new MDocInstance();
        movie.setTask_id(task.getId());
        movie.setTask_name(task.getTask_name());
        movie.setFile_path(file);
        movie.setStatus(R.success());
        movie.setName(FileNameUtil.getPrefix(file));
        movie.setData_id(movieDataset.getId());
        movie.setFile_create_at(movieDataset.getMtime());
        movie.setCurrent_step(TaskStep.of(HandlerKey.MDocInit));
        this.mDocInstanceRepository.insert(movie);
    }

    private void createExportMovie(Task task, ExportTask exportTask, MDoc movieDataset) {
        this.exportMovieService.createExportMdoc(exportTask, movieDataset, null);
    }


}
