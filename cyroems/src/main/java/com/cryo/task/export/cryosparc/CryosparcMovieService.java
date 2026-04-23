package com.cryo.task.export.cryosparc;

import cn.hutool.core.io.file.FileNameUtil;
import com.cryo.dao.dataset.TaskDataSetRepository;
import com.cryo.dao.export.ExportMovieRepository;
import com.cryo.dao.export.ExportTaskRepository;
import com.cryo.model.dataset.TaskDataset;
import com.cryo.model.export.ExportMovie;
import com.cryo.task.engine.HandlerKey;
import com.cryo.task.export.ExportTaskVo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.io.File;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CryosparcMovieService
{
    @Value("${app.task.export.cryosparc.patch:23}")
    private final int patchCount = 23;

    private final ExportMovieRepository exportMovieRepository;
    private final TaskDataSetRepository taskDataSetRepository;
    private final ExportTaskRepository exportTaskRepository;

    @Data
    @AllArgsConstructor
    public static class PatchMovies
    {
        private String pattern;
        private String prefix;
        private List<ExportMovie> movies;
    }

    public PatchMovies getPatchMovies(ExportTaskVo exportTaskVo) {
        String taskId = exportTaskVo.getExportTask().getId();
        TaskDataset taskDataset = this.taskDataSetRepository.findById(exportTaskVo.getTask().getTaskSettings().getDataset_id()).orElseThrow();
        if(taskDataset.getMovie_sync_done()){
            List<ExportMovie> unprocessedMovies = getUnprocessedMovies(taskId, null);
            if(unprocessedMovies.size() < taskDataset.getMovies_count()){
                return null;
            }
            String filePath = unprocessedMovies.get(0).getFile_path();
//            String name = first.getName();
            String suffix = "." + FileNameUtil.getSuffix(filePath);
            File dir = new File(filePath).getParentFile();
            return new PatchMovies(dir + "/*" + suffix, dir + "/*" + suffix, unprocessedMovies);
        }
        // TODO: 全量sync完成之后，之后再做提交


        List<ExportMovie> exportMovies = getUnprocessedMovies(taskId,2* this.patchCount);
        if( exportMovies.isEmpty() ) {
            return null;
        }
        ExportMovie first = exportMovies.get(0);
        if( exportMovies.size() == 1 ) {
            return new PatchMovies(first.getFile_path(), first.getFile_path(), List.of(first));
        }
        String name = first.getName();
        String suffix = "." + FileNameUtil.getSuffix(first.getFile_path());
        File dir = new File(first.getFile_path()).getParentFile();

        File[] allFiles = dir.listFiles(f -> {
            return f.isFile() && f.getName().endsWith(suffix);
        });
        if( allFiles == null ) {
            throw new RuntimeException("files is empty");
        }

        String[] split = name.split("_");
        StringBuilder prefix = new StringBuilder();
        for( int i = 0; i < split.length; i++ ) {
            if( i == 0 ) {
                prefix.append(split[i]);
            } else {
                prefix.append("_").append(split[i]);
            }

            if( Arrays.stream(allFiles).filter(f -> f.getName().startsWith(prefix.toString())).count() == 1 ) {
                break;
            }
        }

        for( int i = prefix.length(); i > 0; i-- ) {
            prefix.deleteCharAt(i - 1);

            long allCount = Arrays.stream(allFiles).filter(f -> f.getName().startsWith(prefix.toString())).count();
            long cCount = exportMovies.stream().filter(f -> f.getName().startsWith(prefix.toString())).count();

            if( allCount != cCount ) {
//                if(cCount <= 1){
//                    throw new RuntimeException("can't found the movie pattern");
//                }
                break;
            }
            if( cCount > this.patchCount + this.patchCount / 2 || cCount >= exportMovies.size() ) {
                break;
            }
        }
        char c = name.charAt(prefix.length());
        List<ExportMovie> filteredMovies = new ArrayList<>();
        List<Character> cs = new ArrayList<>();
        while( c >= '0' && c <= '9' ) {
            char finalC = c;
            List<ExportMovie> list = exportMovies.stream().filter(movie -> movie.getName().startsWith(prefix.toString() + finalC)).toList();
            if( !list.isEmpty() ) {
                if( list.size() + filteredMovies.size() == exportMovies.size() ) {
                    break;
                }
                filteredMovies.addAll(list);
                cs.add(finalC);
                if( filteredMovies.size() >= this.patchCount / 2 ) {
                    break;
                }
            }
            c = (char) (c + 1);
        }
        if( !filteredMovies.isEmpty() ) {
            String s = prefix + "[" + StringUtils.join(cs, "") + "]";
            return new PatchMovies(dir + "/" + s + "*" + suffix, s, filteredMovies);
        }
        return null;

    }

    private List<ExportMovie> getUnprocessedMovies(String taskId, Integer count) {

        Query query = Query.query(Criteria.where("task_id").is(taskId));
        query.with(Sort.by(Sort.Order.asc("file_create_at")));
        query.addCriteria(Criteria.where("current_step.key").is(HandlerKey.INIT)
                .and("error.permanent").ne(true)
                .and("cryospacStatus").is(ExportMovie.CryospacStatus.Init));
//        int count = 2 * this.patchCount;
        if(count != null){

            query.limit(count);
        }
        List<ExportMovie> exportMovies = this.exportMovieRepository.findByQuery(query);
        if( exportMovies.isEmpty() ) {
            return exportMovies;
        }
        if(count == null) {
            return exportMovies;
        }
        if( exportMovies.size() < count ) {
            
            ExportMovie exportMovie = exportMovies.get(exportMovies.size() - 1);
            if( !longTimeBefore(exportMovie) ) {
                return List.of();
            }
        }
        return exportMovies;
    }

    // 用movie 插入时间判断 是否长时间没有新的数据产生，如果是，则认为已经没有新的数据产生，返回剩余的数据进行处理
    private boolean longTimeBefore(ExportMovie exportMovie) {
        return exportMovie.getCreated_at().before(new Time(System.currentTimeMillis() - 1000 * 60 * 30));
    }
}
