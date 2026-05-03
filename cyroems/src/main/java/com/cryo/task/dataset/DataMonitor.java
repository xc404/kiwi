package com.cryo.task.dataset;

import com.cryo.dao.dataset.TaskDataSetRepository;
import com.cryo.model.Microscope;
import com.cryo.model.dataset.TaskDataset;
import com.cryo.task.support.ExportSupport;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.filefilter.AndFileFilter;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.PrefixFileFilter;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "app.task.dataset.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DataMonitor implements InitializingBean, ApplicationContextAware
{
    private final FileAlterationMonitor fileAlterationMonitor = new FileAlterationMonitor();
    private final TaskDataSetRepository taskDataSetRepository;

    private final DatasetService datasetService;

    private final Map<String, FileAlterationObserver> datasetDetectors = new HashMap<>();
    private ApplicationContext applicationContext;

    public void startDatasetMonitor() {
        this.datasetService.getDataSetConfigs().forEach(dataSetConfig -> {
            DataSetDetector dataSetDetector = new DataSetDetector(taskDataSetRepository,applicationContext.getBean(ExportSupport.class), dataSetConfig);
            File root = new File(dataSetConfig.getSource_dir());
            FileAlterationObserver fileAlterationObserver;
            try {
                fileAlterationObserver = FileAlterationObserver.builder().setFile(root)
                        .setFileFilter(
                                new AndFileFilter(
                                        DirectoryFileFilter.INSTANCE,
                                        new PrefixFileFilter(dataSetConfig.getDirectory_prefixes()),
                                        FileFilterUtils.ageFileFilter(Date.from(Instant.now().minus(Duration.ofDays(dataSetConfig.getScan_days()))),false),
                                        new IOFileFilter()
                                        {
                                            @Override
                                            public boolean accept(File file) {
                                                return file.getParentFile().equals(root);
                                            }

                                            @Override
                                            public boolean accept(File dir, String name) {
                                                return dir.getParentFile().equals(root);
                                            }
                                        }
                                )
                        )
                        .get();
            } catch( IOException e ) {
                throw new RuntimeException(e);
            }
            fileAlterationObserver.addListener(dataSetDetector);
            fileAlterationMonitor.addObserver(fileAlterationObserver);
        });
    }

    @Scheduled(cron = "0 0/1 * * * ?")
    public void execute() {
        syncDataset();
    }

    public void syncDataset(){
        List<TaskDataset> taskDatasets = this.taskDataSetRepository.findByQuery(Query.query(Criteria.where("created_at").gt(Date.from(Instant.now().minus(Duration.ofDays(10))))));
        taskDatasets.forEach(this::addTaskDatasetTask);
        Set<String> ids = taskDatasets.stream().map(t -> t.getId()).collect(Collectors.toSet());
        List<String> toBeRemoved = this.datasetDetectors.keySet().stream().filter(k -> !ids.contains(k)).toList();
        toBeRemoved.forEach(this::remove);
    }

    private synchronized void addTaskDatasetTask(TaskDataset taskDataset) {
        if(this.datasetDetectors.containsKey(taskDataset.getId())){
            return;
        }
        String rawPath = taskDataset.getRaw_path();
        File root = new File(rawPath);
        String microscope = Microscope.keyFromString(taskDataset.getMicroscope());
        DataSetConfig dataSetConfig = this.datasetService.getDataSetConfig(microscope);
        if(dataSetConfig == null){
            return;
        }
        FileAlterationObserver fileAlterationObserver;
        try {
            fileAlterationObserver = FileAlterationObserver.builder().setFile(root)
                    .setFileFilter(
                            new AndFileFilter(
                                    FileFilterUtils.fileFileFilter(),
                                    new SuffixFileFilter(dataSetConfig.getFile_types())
                            )
                    )
                    .get();
        } catch( IOException e ) {
            throw new RuntimeException(e);
        }
        fileAlterationObserver.addListener(new DatasetFileDetector(applicationContext,dataSetConfig, taskDataset ));
        fileAlterationMonitor.addObserver(fileAlterationObserver);
        this.datasetDetectors.put(taskDataset.getId(), fileAlterationObserver);
    }

    private synchronized void remove(String taskDatasetId) {
        FileAlterationObserver fileAlterationObserver = this.datasetDetectors.get(taskDatasetId);
        if(fileAlterationObserver != null){
            this.fileAlterationMonitor.removeObserver(fileAlterationObserver);
        }
        this.datasetDetectors.remove(taskDatasetId);
    }

    @Override
    public void afterPropertiesSet() throws Exception {

        try {
            fileAlterationMonitor.start();
        } catch( Exception e ) {
            throw new RuntimeException(e);
        }

        startDatasetMonitor();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
