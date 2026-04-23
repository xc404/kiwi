package com.cryo.task.dataset;

import cn.hutool.core.io.file.FileNameUtil;
import com.cryo.common.utils.FileUtils;
import com.cryo.dao.dataset.MDocRepository;
import com.cryo.dao.dataset.MovieDataSetRepository;
import com.cryo.dao.dataset.TaskDataSetRepository;
import com.cryo.model.dataset.MDoc;
import com.cryo.model.dataset.MovieDataset;
import com.cryo.model.dataset.TaskDataset;
import com.cryo.task.gain.GainConvertor;
import com.cryo.task.support.ExportSupport;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.monitor.FileAlterationListener;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.springframework.context.ApplicationContext;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Slf4j
public class DatasetFileDetector extends FileAlterationListenerAdaptor implements FileAlterationListener
{
//    private DataSetConfig dataSetConfig;
    private final TaskDataset taskDataset;

    //    private Ta taskDatasetRepository;
    private final TaskDataSetRepository taskDataSetRepository;
    private final MovieDataSetRepository movieDataSetRepository;
    private final MDocRepository mDocRepository;
    private boolean delete_movie = false;
    private final ExportSupport exportSupport;
    private final GainConvertor gainConvertor;


    public DatasetFileDetector( ApplicationContext applicationContext, DataSetConfig dataSetConfig,TaskDataset taskDataset) {
        this.taskDataset = taskDataset;
        this.delete_movie = dataSetConfig.isDelete_movie();
        this.taskDataSetRepository = applicationContext.getBean(TaskDataSetRepository.class);
        this.exportSupport = applicationContext.getBean(ExportSupport.class);
        this.gainConvertor = applicationContext.getBean(GainConvertor.class);
        this.mDocRepository = applicationContext.getBean(MDocRepository.class);
        this.movieDataSetRepository = applicationContext.getBean(MovieDataSetRepository.class);

    }

    @Override
    public void onFileCreate(File file) {
//        Date lastModified = com.cryo.common.utils.FileUtils.lastModified(file);
//        if( task.getLast_file_detect_time() != null && lastModified.before(task.getLast_file_detect_time()) ) {
//            log.info("ignore file {} change, file has been handled, detect time {} , file create at {}", file, task.getLast_file_detect_time(), lastModified);
//            return;
//        }

        synchronized( file.getAbsolutePath().intern() ) {
            try {
                String suffix = FileNameUtil.getSuffix(file);
                switch( suffix ) {
                    case "tif":
                    case "eer":
                        processMovie(file);
                        break;
                    case "dm4":
                    case "gain":
                        processGain(file);
                        break;
                    case "mdoc":
                        processMdoc(file);
                        break;
                }
            } catch( Exception e ) {
                log.error(e.getMessage(), e);
            }
        }
    }


    public void processMovie(File file) {
        synchronized( file.getAbsolutePath().intern() ) {
            File dest = new File(taskDataset.getMovie_path(), file.getName());
            Optional<MovieDataset> byFile = this.movieDataSetRepository.findByFile(taskDataset.getId(), dest.getAbsolutePath());
            if( byFile.isPresent() ) {
                return;
            }
            copyFile(file, dest);
            MovieDataset movie = new MovieDataset();
            movie.setBelonging_data(taskDataset.getId());
            movie.setPath(dest.getAbsolutePath());
            movie.setName(FileNameUtil.getPrefix(file));
            movie.setMtime(FileUtils.lastModified(file));
            movie.setCreated_at(new Date());
            this.movieDataSetRepository.insert(movie);
            if( delete_movie ) {
                file.delete();
            }
        }

    }


    public void processGain(File file) {

        if(taskDataset.getGain0() != null){
            return;
        }
        TaskDataset.Gain gain = new TaskDataset.Gain();
        File dest = new File(taskDataset.getMovie_path(), file.getName());
        copyFile(file, dest);
        File converted = gainConvertor.convert(dest);

        gain.setPath(dest.getAbsolutePath());
        gain.setUsable_path(converted.getAbsolutePath());
        gain.setCreated_at(new Date());
        gain.setMtime(FileUtils.lastModified(file));
//        gain.setUsable_path(dest.getAbsolutePath());

        taskDataset.setGain(List.of(gain));
        this.taskDataSetRepository.save(taskDataset);
    }

    public void processMdoc(File file) {
        synchronized( file.getAbsolutePath().intern() ) {
            File dest = new File(taskDataset.getMovie_path(), file.getName());
            Optional<MDoc> byFile = this.mDocRepository.findByFile(taskDataset.getId(), dest.getAbsolutePath());
            if( byFile.isPresent() ) {
                return;
            }
            copyFile(file, dest);
            MDoc movie = new MDoc();
            movie.setBelonging_data(taskDataset.getId());
            movie.setPath(dest.getAbsolutePath());
            movie.setName(FileNameUtil.getPrefix(file));
            movie.setMtime(FileUtils.lastModified(file));
            movie.setCreated_at(new Date());
            this.mDocRepository.insert(movie);
        }
    }

    private File copyFile(File src, File dest) {
//        File dest = new File(taskDataset.getMovie_path(), file.getName());
        try {
            if( !org.apache.commons.io.FileUtils.contentEquals(src, dest) ) {
                org.apache.commons.io.FileUtils.copyFile(src, dest, true);
//                exportSupport.setPermission(dest);
            }

        } catch( IOException e ) {
            throw new RuntimeException(e);
        }
        return dest;
    }

}
