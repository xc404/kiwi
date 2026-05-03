package com.cryo.task.filedetect;

import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.util.ArrayUtil;
import com.cryo.dao.GainRepository;
import com.cryo.model.Gain;
import com.cryo.model.GainConvertStatus;
import com.cryo.model.Task;
import lombok.RequiredArgsConstructor;

import java.io.File;
import java.util.Optional;

//@Service
@RequiredArgsConstructor
public class GainFileHandler implements TaskFileHandler {

    public static final String[] GainFileSuffix = {"dm4","gain"};
    private final GainRepository gainRepository;
    @Override
    public boolean support(String suffix) {
        return ArrayUtil.contains(GainFileSuffix, suffix);
    }

    @Override
    public synchronized void handle(Task task, File file) {
        Optional<Gain> gainByTask = this.gainRepository.getGainByTask(task.getId());
        if(gainByTask.isPresent()){
            return;
        }
        Gain gain = new Gain();
        gain.setTask_id(task.getId());
        gain.setTask_name(task.getTask_name());
        gain.setGain_conversion_status(GainConvertStatus.unprocessed);
        gain.setFile_path(file.getAbsolutePath());
        gain.setFile_name(FileNameUtil.getPrefix(file.getName()));
        this.gainRepository.insert(gain);
    }
}
