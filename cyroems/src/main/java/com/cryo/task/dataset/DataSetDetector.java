package com.cryo.task.dataset;

import com.cryo.dao.dataset.TaskDataSetRepository;
import com.cryo.model.dataset.TaskDataset;
import com.cryo.task.support.ExportSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.monitor.FileAlterationListener;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RequiredArgsConstructor
@Slf4j
public class DataSetDetector extends FileAlterationListenerAdaptor implements FileAlterationListener
{
    private final TaskDataSetRepository taskDataSetRepository;
    private final ExportSupport exportSupport;
    private final DataSetConfig dataSetConfig;
    @Override
    public void onDirectoryChange(File directory) {
        List<TaskDataset> dataSets = this.taskDataSetRepository.findByQuery(Query.query(Criteria.where("raw_path").is(directory.getAbsolutePath())));
        TaskDataset taskDataset;
        if(!dataSets.isEmpty()){
            taskDataset = dataSets.get(0);
            taskDataset.setGain(null);
            taskDataset.setMtime(new Date(directory.lastModified()));
        }else{
            taskDataset = new TaskDataset();
            taskDataset.setCreated_at(new Date());
            taskDataset.setRaw_path(directory.getAbsolutePath());
            String dirName = directory.getName();
            taskDataset.setIs_tomo(dirName.toLowerCase().startsWith("tomo"));
            taskDataset.setMicroscope(this.dataSetConfig.getMicroscope());
            taskDataset.setMtime(new Date(directory.lastModified()));
            Pattern p  = Pattern.compile("(20\\d{2})(0[1-9]|1[0-2])([0-2]\\d|3[01])");
            Matcher matcher = p.matcher(dirName);
            boolean find = matcher.find();
            if(!find){
                log.warn("file {} not match the pattern", dirName);
                return;
            }

            String group = matcher.group(0);

//            dataSetConfig.getTarget()
            String year = group.substring(0,4);

            String ym = group.substring(0,6);
            int month = Integer.parseInt(group.substring(4,6));
            int quarter = (month-1) / 3 + 1;
            String canonicalPath;
            try {
                canonicalPath = FileUtils.getFile(new File(dataSetConfig.getTarget()), new File(dataSetConfig.getSource_dir()).getName(),
                        year + "Q" + quarter,
                        ym,
                        dirName).getCanonicalPath();

                File file = new File(canonicalPath);
                FileUtils.forceMkdir(file);
//                exportSupport.setPermission(file);
            } catch( IOException e ) {
                throw new RuntimeException(e);
            }
            taskDataset.setMovie_path(canonicalPath);
        }
        this.taskDataSetRepository.save(taskDataset);
    }

    @Override
    public void onDirectoryCreate(File directory) {
        this.onDirectoryChange(directory);
    }


    public static void main(String[] args) {
        Pattern p  = Pattern.compile("(20\\d{2})(0[1-9]|1[0-2])([0-2]\\d|3[01])");

        System.out.println("20250618".matches(p.pattern()));
        Matcher matcher = p.matcher("20200809_mmdd");

        boolean b = matcher.find();
        System.out.println(b);
        String group = matcher.group(0);
        System.out.println(group);
        System.out.println(group.substring(0,4));
        System.out.println(group.substring(4,6));
    }
}
