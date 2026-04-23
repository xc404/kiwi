package com.cryo.service;

import cn.hutool.core.io.file.FileNameUtil;
import com.cryo.dao.dataset.TaskDataSetRepository;
import com.cryo.model.Task;
import com.cryo.model.dataset.TaskDataset;
import com.cryo.service.cmd.SoftwareService;
import com.cryo.task.engine.Context;
import com.cryo.task.movie.MovieContext;
import com.cryo.task.support.ExportSupport;
import com.cryo.task.tilt.MDocContext;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import static com.cryo.task.support.ExportSupport.CRYOSPARC_USER;

@Service
@RequiredArgsConstructor
public class FilePathService implements InitializingBean
{

    @Value("${app.file.work_dir}")
    private String workDir = "";
    @Value("${app.task.slurm_log_dir}")
    private String slurm_log_dir = "";

    @Value("${app.file.copy_with_shell}")
    private boolean copy_with_shell = false;


    private final ExportSupport exportSupport;
    private final SoftwareService softwareService;
    private final TaskDataSetRepository taskDataSetRepository;

    public File getMotionWorkDir(Context movieContext) {
        return getWorkDir(movieContext, "/motion");
    }

    public File getImageWorkDir(Context movieContext) {
        return getWorkDir(movieContext, "/thumbnails");
    }


    public File getEstimationWorkDir(Context movieContext) {
        return getWorkDir(movieContext, "/ctf");
    }

    public File getVFMWorkDir(Context movieContext) {
        return getWorkDir(movieContext, "/vfm");
    }

//    public File getGainWorkDir(Task task) {
//        String taskRoot = task.getTask_name() + "_" + task.getId();
//        return getWorkDir(taskRoot, "gain");
//    }

    public File getWorkDir(Context movieContext, String... child) {
        String contextDir = movieContext.getContextDir();
        TaskDataset taskDataset = movieContext.getTaskDataset();
        String name = FileNameUtil.getName(taskDataset.getMovie_path()).replaceAll("\\s", "_");
        String taskRoot = name + "_" + taskDataset.getId();
        List<String> list = new ArrayList<>();
        list.add(taskRoot);
        list.add(contextDir);
        list.addAll(List.of(child));
        return getWorkDir(list.toArray(new String[0]));
    }

    public File getWorkDir(Task task, String... child) {
        String contextDir = task.getWork_dir();
//        TaskDataset taskDataset = movieContext.getTaskDataset();
//        String name = FileNameUtil.getName(task.getTask_name()).replaceAll("\\s", "_");
        String movie_path = task.getMovie_path();
        if( task.getMovie_path() == null ) {
            TaskDataset dataset = this.taskDataSetRepository.findById(task.getTaskSettings().getDataset_id()).orElseThrow();
            movie_path = dataset.getMovie_path();
        }
        String name = FileNameUtil.getName(movie_path).replaceAll("\\s", "_");
        String taskRoot = name + "_" + task.getTaskSettings().getDataset_id();
        List<String> list = new ArrayList<>();
        list.add(taskRoot);
        list.add(contextDir);
        list.addAll(List.of(child));
        return getWorkDir(list.toArray(new String[0]));
    }

    public File getWorkDir(String... child) {

        File root = FileUtils.getFile(this.workDir);
        File file = FileUtils.getFile(root, child);
        File result = file;
        if( !copy_with_shell ) {
            Stack<File> stack = new Stack<>();
            while( true ) {
                stack.push(file);
                if( file.equals(root) ) {
                    break;
                }
                file = file.getParentFile();
            }
            while( !stack.isEmpty() ) {
                file = stack.pop();
                if( !file.exists() ) {
                    try {
                        FileUtils.forceMkdir(file);
                    } catch( Throwable e ) {
                        throw new RuntimeException(e);
                    }
                }
                exportSupport.toSelf(file);
            }
        }
        return result;
    }


//    public File getOutputDir(Task task, String... child) {
//
//        File root = FileUtils.getFile(task.getOutput_dir(), task.getOutput_dir_tail());
//        File file = root;
//        if( child != null ) {
//            file = FileUtils.getFile(file, child);
//        }
//        File result = file;
//        if( !copy_with_shell ) {
//            forceCreate(task, file, root);
//            softwareService.setfacl(file, "u:" + CRYOSPARC_USER + ":rwx").startAndWait();
//        }
//
//        return result;
//    }

    public File getTaskOutputDir(Task task, String parent, String... child) {
        File root = FileUtils.getFile(parent);
        File file = root;
        if( child != null ) {
            file = FileUtils.getFile(file, child);
        }
        boolean exist = file.exists();
        File result = file;
        if( !copy_with_shell ) {
            forceCreate(task, file, root);
            if( !exist ) {
                softwareService.setfacl(file, "u:" + CRYOSPARC_USER + ":rwx").startAndWait();
            }
        }
        File leaf = result;
        if( !exist ) {
            while( true ) {
                File parentFile = leaf.getParentFile();
                if( parentFile == null || parentFile.getPath().equals("/home") || parentFile.getPath().equals("/") ) {
                    break;
                }
                softwareService.setfacl(parentFile, "u:" + exportSupport.getUser() + ":rx").startAndWait();
                leaf = parentFile;
            }
        }
        return result;
    }

    private void forceCreate(Task task, File file, File root) {
        Stack<File> stack = new Stack<>();
        while( true ) {
            stack.push(file);
            if( file.equals(root) ) {
                break;
            }
            file = file.getParentFile();
        }
        while( !stack.isEmpty() ) {
            file = stack.pop();
            if( !file.exists() ) {
                try {
                    FileUtils.forceMkdir(file);
                    exportSupport.setOwnerAndPermission(task, file);
                } catch( IOException e ) {
                    throw new RuntimeException(e);
                }
            }
        }
    }


    public File getWriteBackDir(MovieContext movieContext, String... child) {
//        Task task = movieContext.getTask();
        String moviePath = movieContext.getTaskDataset().getMovie_path();
        File root = FileUtils.getFile(moviePath, movieContext.getContextDir());
        File file = root;
        if( child != null ) {
            file = FileUtils.getFile(file, child);
        }
        File result = file;
        if( !copy_with_shell ) {
            Stack<File> stack = new Stack<>();
            while( true ) {
                stack.push(file);
                if( file.equals(root) ) {
                    break;
                }
                file = file.getParentFile();
            }
            while( !stack.isEmpty() ) {
                file = stack.pop();
                if( !file.exists() ) {
                    try {
                        FileUtils.forceMkdir(file);
                        exportSupport.setOwnerAndPermission(file, exportSupport.getWriteBackUser(), exportSupport.getWriteBackGroup());
                    } catch( IOException e ) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        return result;
    }


    @Override
    public void afterPropertiesSet() throws Exception {
    }

    public File getSlurmLog(String file) {
        return FileUtils.getFile(this.slurm_log_dir, file);
    }

    public File getMdocWorkDir(MDocContext movieContext) {

        return getWorkDir(movieContext, "mdoc", movieContext.getInstance().getName());
    }
}
