package com.cryo.task.movie;


import com.cryo.model.Movie;
import com.cryo.model.MovieResult;
import com.cryo.model.Task;
import com.cryo.model.dataset.TaskDataset;
import com.cryo.model.export.ExportMovie;
import com.cryo.model.settings.TaskSettings;
import com.cryo.task.engine.Context;
import com.cryo.task.engine.flow.IFlow;
import org.springframework.context.ApplicationContext;

public class MovieContext extends com.cryo.task.engine.BaseContext<Movie, MovieResult> implements Context<Movie, MovieResult>
{
//    private StringWriter slurmCmds = new StringWriter();

    public MovieContext(ApplicationContext applicationContext, TaskDataset taskDataset, IFlow<Movie, MovieResult> flow, Task task, Movie movie) {
        super(applicationContext, taskDataset, flow, task, movie, MovieResult.class);

    }


    public Movie getMovie() {

        return getInstance();
    }

    public boolean isExport() {
        return instance instanceof ExportMovie;
    }

//    public String getCurrentConfigId() {

    /// /        TaskStep currentStep = this.getCurrentStep();
    /// /        if( task.is`
    /// /        if( task.isCryosparc() ) {
    /// /            return task.getConfig_id();
    /// /        }
//        return this.task.getDefault_config_id();
//    }
    public TaskSettings getTaskSettings() {
//        if(task.getIs_tomo()){
//            return task.getTaskSettings();
//        }
//        if( task.isCryosparc() ) {
        return task.getTaskSettings();
//        }
//        return this.defaultTaskSettings;
    }

    @Override
    protected MovieResult createResult() {
        MovieResult result = super.createResult();
        result.setType(isExport() ? "export" : "movie");
        return result;
    }
}
