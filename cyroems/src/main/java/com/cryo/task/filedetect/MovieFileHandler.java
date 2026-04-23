package com.cryo.task.filedetect;

import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.util.ArrayUtil;
import com.cryo.common.utils.FileUtils;
import com.cryo.dao.MovieRepository;
import com.cryo.model.Movie;
import com.cryo.task.engine.HandlerKey;
import com.cryo.task.engine.TaskStep;
import com.cryo.model.Task;
import lombok.RequiredArgsConstructor;
import net.dreamlu.mica.core.result.R;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MovieFileHandler implements TaskFileHandler {

    public static final String[] MovieFileSuffix = {"tif","tiff","eer"};
    private final MovieRepository movieRepository;
//    private final MicroscopeService microscopeService;
    @Override
    public boolean support(String suffix) {
        return ArrayUtil.contains(MovieFileSuffix, suffix);
    }

    @Override
    public synchronized void handle(Task task, File file) {
        Optional<Movie> byFile = this.movieRepository.findByFile(task.getId(), file.getAbsolutePath());
        if(byFile.isPresent()){
            return;
        }
//        task.getMicroscope().
        Movie movie = new Movie();
        movie.setTask_id(task.getId());
        movie.setTask_name(task.getTask_name());
        movie.setFile_path(file.getAbsolutePath());
        movie.setStatus(R.success());
        movie.setFile_name(FileNameUtil.getPrefix(file));
        movie.setFile_create_at(FileUtils.lastModified(file));
//        movie.setFile_name_index();
        movie.setCurrent_step(TaskStep.of(HandlerKey.INIT));
        this.movieRepository.insert(movie);
    }


}
