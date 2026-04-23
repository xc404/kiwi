package com.cryo.task.export.cryosparc;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileNameUtil;
import com.cryo.dao.CryosparcPatchRepository;
import com.cryo.dao.UserRepository;
import com.cryo.dao.dataset.TaskDataSetRepository;
import com.cryo.dao.export.ExportMovieRepository;
import com.cryo.dao.export.ExportTaskRepository;
import com.cryo.model.CryosparcPatch;
import com.cryo.model.Microscope;
import com.cryo.model.Movie;
import com.cryo.model.Task;
import com.cryo.model.dataset.TaskDataset;
import com.cryo.model.export.ExportMovie;
import com.cryo.model.export.ExportTask;
import com.cryo.model.settings.CryosparcSettings;
import com.cryo.model.user.User;
import com.cryo.service.ExportTaskService;
import com.cryo.service.FilePathService;
import com.cryo.service.cmd.CmdException;
import com.cryo.service.cmd.SoftwareService;
import com.cryo.service.cryosparc.CryosparcClient;
import com.cryo.service.cryosparc.CryosparcJob;
import com.cryo.service.cryosparc.JobRequest;
import com.cryo.service.cryosparc.JobState;
import com.cryo.service.cryosparc.JobType;
import com.cryo.task.support.ExportSupport;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import net.dreamlu.mica.core.utils.JsonUtil;
import org.apache.commons.io.FileUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.cryo.task.support.ExportSupport.CRYOSPARC_USER;

@Slf4j
public class CryosparcCompleteHanlder
{
    //    public static final String IMPORT_PARTICLES = "import_particles";
//    public static final String EXTRACT_MICROGRAPHS_MULTI = "extract_micrographs_multi";
//    public static final String CLASS_2_D_NEW = "class_2D_new";
    public static final String IMPORTED_PROJECT_JSON = "imported_project.json";
    private final CryosparcPatchRepository cryosparcPatchRepository;
    private final FilePathService filePathService;
    private final ExportSupport exportSupport;
    private final ExportMovieRepository movieRepository;
    private final SoftwareService softwareService;
    private final TaskDataSetRepository taskDataSetRepository;
    private final UserRepository userRepository;
    private final ExportTaskRepository exportTaskRepository;
    private final Task task;
    private final ExportTask exportTask;
    //    private String output_dir = "/home/cryosparc-data";
    private CryosparcCompleteStatus cryosparcCompleteStatus;
    private final ExportTaskService exportTaskService;
    private final CryosparcClient cryosparcClient;
    private String completeProjectId;
    private final String workspace_uid = "W1";
    private Duration waitInterval = Duration.ofSeconds(60);

    public CryosparcCompleteHanlder(ApplicationContext applicationContext,
                                    Task task, ExportTask exportTask) {
//        this.output_dir = exportDir;
        this.task = task;
        this.exportTask = exportTask;
        this.cryosparcPatchRepository = applicationContext.getBean(CryosparcPatchRepository.class);
        this.filePathService = applicationContext.getBean(FilePathService.class);
        this.exportSupport = applicationContext.getBean(ExportSupport.class);
        this.movieRepository = applicationContext.getBean(ExportMovieRepository.class);
        this.softwareService = applicationContext.getBean(SoftwareService.class);
        this.taskDataSetRepository = applicationContext.getBean(TaskDataSetRepository.class);
        this.userRepository = applicationContext.getBean(UserRepository.class);
        this.exportTaskRepository = applicationContext.getBean(ExportTaskRepository.class);
        this.exportTaskService = applicationContext.getBean(ExportTaskService.class);

        this.cryosparcClient = applicationContext.getBean(CryosparcClient.class);
    }


    public void complete() {
        init();
        CryosparcCompleteResult result;
        try {
            result = this.completeInternal();

        } catch( Exception e ) {
            log.error(e.getMessage(), e);
            result = CryosparcCompleteResult.error(e.getMessage());
        }
        if( result.isSuccess() ) {

            cryosparcCompleteStatus.setStatus(CryosparcCompleteStatus.Status.Success);
            cryosparcCompleteStatus.setClassification_status(true);
            cryosparcCompleteStatus.setExtract_micrographs_status(true);
            cryosparcCompleteStatus.setImport_particles_status(true);
            cryosparcCompleteStatus.setCopy_to_user_status(true);
//            cryosparcCompleteStatus.getSummary().setCompleted(cryosparcCompleteStatus.getSummary().getTotal());
//            cryosparcCompleteStatus.getSummary().setCalculateTime("0");
//            cryosparcCompleteStatus.getSummary().setProgress(100);
        } else {
            cryosparcCompleteStatus.setStatus(CryosparcCompleteStatus.Status.Failed);
        }
        cryosparcCompleteStatus.setEndTime(new Date());
        cryosparcCompleteStatus.setMessage(result.getMessage());
        saveCryosparcCompleteStatus();
    }

    protected void init() {

        cryosparcCompleteStatus = new CryosparcCompleteStatus();
        cryosparcCompleteStatus.setStatus(CryosparcCompleteStatus.Status.Processing);
        cryosparcCompleteStatus.setStartTime(new Date());
//        cryosparcCompleteStatus.setSummary(new ConstructProcessLog());
        saveCryosparcCompleteStatus();
    }

    private void saveCryosparcCompleteStatus() {
        this.exportTaskRepository.setCryosparcCompleteStatus(this.exportTask.getId(), cryosparcCompleteStatus);
    }

    public CryosparcCompleteResult completeInternal() {
        if( !exportTask.isCryosparc() ) {
            log.info("cryosparc not enabled");
            return CryosparcCompleteResult.error("cryosparc not enabled");
        }
        CryosparcProject cryosparcProject = exportTask.getCryosparcProject();
        if( cryosparcProject == null ) {
            log.error("cryosparc is not created");
            return CryosparcCompleteResult.error("cryosparc is not created");
        }
        File output = cryosparc_construct();
        Long total = exportTask.getMovie_statistic().getTotal();
        try {
            log.info("wait for cryosparc import movies complete");
            Thread.sleep(getWaitMillis(total)); // wait for cryosparc import movies complete
        } catch( InterruptedException e ) {
            throw new RuntimeException(e);
        }
        try {
            String s = FileUtils.readFileToString(new File(output, IMPORTED_PROJECT_JSON), StandardCharsets.UTF_8);
            this.completeProjectId = JsonUtil.readTree(s).get("imported_project_uid").asText();
        } catch( IOException e ) {
            throw new RuntimeException(e);
        }

        File file = writeStar();

        JobState importparticle = importparticle(file);
        if( !importparticle.isSuccess() ) {
            log.error("import particle failed");
            return CryosparcCompleteResult.error("import particle failed");
        }

        JobState extract_micrographs = extract_micrographs(importparticle);

        if( !extract_micrographs.isSuccess() ) {
            log.error("extract micrographs failed");
            return CryosparcCompleteResult.error("extract micrographs failed");
        }

        JobState classification = class_2D_new(extract_micrographs);
        if( !classification.isSuccess() ) {
            log.error("classification failed");
            return CryosparcCompleteResult.error("classification failed");
        }

        detach();
        try {
            log.info("wait for cryosparc detach complete");
            Thread.sleep(getWaitMillis(total)); // wait for cryosparc detach complete
        } catch( InterruptedException e ) {
            throw new RuntimeException(e);
        }

        copyTarget(output);

        if( exportTask.getCryosparcSettings().isAutoAttach() ) {
            this.attach(cryosparcProject.getPath());
        }


        ConstructProcessLog constructProcessLog = new ConstructProcessLog();
        constructProcessLog.setProgress(100);
        constructProcessLog.setStatus("completed");

        this.cryosparcCompleteStatus.setSummary(constructProcessLog);
        this.saveCryosparcCompleteStatus();
        return CryosparcCompleteResult.success(output.getAbsolutePath());
    }

    private void attach(String path) {
        User user = this.userRepository.findById(task.getOwner()).orElseThrow();
        cryosparcClient.attach(user.getCryosparcUserId(), path);
        this.cryosparcCompleteStatus.setAutoAttachStatus(true);
        this.saveCryosparcCompleteStatus();
    }

    private long getWaitMillis(Long total) {
        return Math.max(total / 100 * this.waitInterval.toMillis(), 5 * 60000L);
    }

    private void copyTarget(File output) {
        File cryosparcOutputDir = getCryosparcOutputDir();
        softwareService.cp(output.getAbsolutePath() + "/.", cryosparcOutputDir).startAndWait();
        if( isCryocparcUserHome() ) {
            exportSupport.setOwnerAndPermission(cryosparcOutputDir, CRYOSPARC_USER, CRYOSPARC_USER);
            try {
                softwareService.setfacl(cryosparcOutputDir, "u:" + task.getGroup_name() + ":rx").startAndWait();
            } catch( Exception e ) {
                log.error(e.getMessage(), e);
            }

        } else {
            exportSupport.setOwnerAndPermission(cryosparcOutputDir, task.getBelong_user(), task.getGroup_name());
            try {
                softwareService.setfacl(cryosparcOutputDir, "u:" + CRYOSPARC_USER + ":rx").startAndWait();
            } catch( Exception e ) {
                log.error(e.getMessage(), e);
            }
        }
        this.cryosparcCompleteStatus.setCopy_to_user_status(true);
        this.saveCryosparcCompleteStatus();
    }

    private void detach() {
        cryosparcClient.detach(this.completeProjectId);
    }

    private JobState class_2D_new(JobState extractMicrographs) {
        CryosparcSettings cryosparcSettings = exportTask.getCryosparcSettings();
        JobRequest jobRequest = new JobRequest();
        jobRequest.setProject_uid(this.completeProjectId);
        jobRequest.setWorkspace_uid(this.workspace_uid);
        jobRequest.setJob_type(JobType.class_2D_new);

        jobRequest.setAncestor_job_uid(extractMicrographs.getCs_job_uid());
        jobRequest.setParams(Map.of(
                "class2D_K", cryosparcSettings.getClass2D_K()
        ));
        return getJobState(jobRequest);
    }

    private JobState extract_micrographs(JobState importparticle) {
        CryosparcSettings cryosparcSettings = exportTask.getCryosparcSettings();
        JobRequest jobRequest = new JobRequest();
        jobRequest.setProject_uid(this.completeProjectId);
        jobRequest.setWorkspace_uid(this.workspace_uid);
        jobRequest.setJob_type(JobType.extract_micrographs_multi);

        jobRequest.setAncestor_job_uid(importparticle.getCs_job_uid());
        jobRequest.setParams(Map.of(
                "box_size_pix", cryosparcSettings.getBox_size_pix()
        ));
        return getJobState(jobRequest);
    }

    private JobState importparticle(File file) {

        JobRequest jobRequest = new JobRequest();
        jobRequest.setProject_uid(this.completeProjectId);
        jobRequest.setWorkspace_uid(this.workspace_uid);
        jobRequest.setAncestor_job_uid("J3");
        jobRequest.setJob_type(JobType.import_particles);
        jobRequest.setParams(Map.of(
                "particle_meta_path", file.getAbsolutePath()
        ));

        return getJobState(jobRequest);
    }

    private JobState getJobState(JobRequest jobRequest) {
        CryosparcJob cryosparcJob = this.cryosparcClient.submitAndWait(jobRequest);
        cryosparcCompleteStatus.addJob(cryosparcJob.getJobRequest().getJob_type(), cryosparcJob);
        if( cryosparcJob.getJobState().isSuccess() ) {

            switch( cryosparcJob.getJobRequest().getJob_type() ) {
                case import_particles:
                    this.cryosparcCompleteStatus.setImport_particles_status(true);
                    break;

                case extract_micrographs_multi:
                    this.cryosparcCompleteStatus.setExtract_micrographs_status(true);
                    break;

                case class_2D_new:
                    this.cryosparcCompleteStatus.setClassification_status(true);
                    break;
            }
        }
        this.saveCryosparcCompleteStatus();
        if( !cryosparcJob.getJobState().isSuccess() ) {
            throw new RuntimeException(cryosparcJob.getJobState().getError());
        }
        return cryosparcJob.getJobState();
    }

    private File writeStar() {
        String vfm = filePathService.getWorkDir(task, exportTask.getTaskWorkDir(), "vfm").getAbsolutePath();
        File output_star_file = new File(getWorkDir(), "particle_file.star");
        this.softwareService.cryosparc_write_star(vfm, output_star_file.getAbsolutePath()).startAndWait();
        this.cryosparcCompleteStatus.setWrite_star_status(true);
        this.saveCryosparcCompleteStatus();
        return output_star_file;
    }

    private File cryosparc_construct() {
        CryosparcProject cryosparcProject = exportTask.getCryosparcProject();
        File jobList = getJobList();
        TaskDataset taskDataset = this.taskDataSetRepository.findById(task.getTaskSettings()
                .getDataset_id()).orElseThrow();
        File workDir = getWorkDir();
        File output = getOutputDir();
        String usablePath = task.getMicroscope() == "Titan3_falcon" ? taskDataset.getGain0().getPath() : taskDataset.getGain0().getUsable_path();

        String defaultOutputDir = task.getDefaultOutputDir();
        usablePath = new File(defaultOutputDir, FileNameUtil.getName(usablePath)).getAbsolutePath();

        CryosparcConstructParams cryosparcConstructParams = new CryosparcConstructParams();
        cryosparcConstructParams.setJob_list_file(jobList.getAbsolutePath());
        cryosparcConstructParams.setProject_uid(cryosparcProject.getProject_uid());
        cryosparcConstructParams.setWorkspace_uid(cryosparcProject.getWorkspace_uid());
        cryosparcConstructParams.setGain_path(usablePath);
        cryosparcConstructParams.setRaw_movie_paths(getMoviePath());
//        cryosparcConstructParams.setParam_file(params.getAbsolutePath());
        cryosparcConstructParams.setOutput_dir(output.getAbsolutePath());
        cryosparcConstructParams.setTmp_output_file(new File(output, IMPORTED_PROJECT_JSON).getAbsolutePath());
//        cryosparcConstructParams.setDenoise_res_dir(filePathService.getWorkDir(task, exportTask.getTaskWorkDir(), "vfm").getAbsolutePath());
        cryosparcConstructParams.setTask_name(task.getTask_name());
//        cryosparcConstructParams.setExported_movie_dir(task.getOutput_dir() + "/" + task.getOutput_dir_tail());
//        if( isCryocparcUserHome() ) {
//            cryosparcConstructParams.setOwner(CRYOSPARC_USER);
//            cryosparcConstructParams.setGroup(CRYOSPARC_USER);
//            cryosparcConstructParams.setAcl_users(task.getBelong_user());
//        }

        SoftwareService.CmdProcess cryosparcConstruct = this.softwareService.cryosparcConstruct(cryosparcConstructParams);
        File logFile = new File(workDir, "cryosparc_construct.log");
        CompleteSummaryProcessor completeSummaryProcessor = new CompleteSummaryProcessor(logFile);

        try {
            completeSummaryProcessor.start();
            cryosparcConstruct.redirectOutput(ProcessBuilder.Redirect.to(logFile));
            cryosparcConstruct.startAndWait();
        } catch( CmdException e ) {
            log.error(e.getMessage(), e);
            if( logFile.exists() ) {
                try {
                    throw new RuntimeException(FileUtils.readFileToString(logFile, "UTF-8"), e);
                } catch( IOException ex ) {
                    throw new RuntimeException(ex);
                }
            }
        } finally {
            completeSummaryProcessor.setRunning(false);
            completeSummaryProcessor.interrupt();
        }
//        File cryosparcOutputDir = output;
//        if( !isCryocparcUserHome() ) {
//
//            cryosparcOutputDir = getCryosparcOutputDir();
//            softwareService.cp(output.getAbsolutePath() + "/.", cryosparcOutputDir).startAndWait();
//            exportSupport.setOwnerAndPermission(cryosparcOutputDir, task.getBelong_user(), task.getBelong_user());
//            try {
//                softwareService.setfacl(cryosparcOutputDir, "u:" + CRYOSPARC_USER + ":rwx").startAndWait();
//            } catch( Exception e ) {
//                log.error(e.getMessage(), e);
//            }
//        }

        return output;

//        File cryosparcOutputDir = getCryosparcOutputDir();
//        softwareService.cp(output.getAbsolutePath() + "/.", cryosparcOutputDir).startAndWait();
//        if( isCryocparcUserHome() ) {
//            exportSupport.setOwnerAndPermission(cryosparcOutputDir, CRYOSPARC_USER, CRYOSPARC_USER);
//            try {
//                softwareService.setfacl(cryosparcOutputDir, "u:" + task.getGroup_name() + ":rwx").startAndWait();
//            } catch( Exception e ) {
//                log.error(e.getMessage(), e);
//            }
//
//        } else {
//            exportSupport.setOwnerAndPermission(cryosparcOutputDir, task.getBelong_user(), task.getBelong_user());
//            try {
//                softwareService.setfacl(cryosparcOutputDir, "u:" + CRYOSPARC_USER + ":rwx").startAndWait();
//            } catch( Exception e ) {
//                log.error(e.getMessage(), e);
//            }
//        }
//        return cryosparcOutputDir;
    }

    private boolean isCryocparcUserHome() {
        return exportTask.getOutputDir().startsWith(exportTaskService.getHOME_CRYOSPARC_DATA());
    }

    private File getCryosparcOutputDir() {
        return filePathService.getTaskOutputDir(task, exportTask.getOutputDir());
    }

    private String getMoviePath() {
        List<ExportMovie> movieDatasets = this.movieRepository.findByQuery(Query.query(Criteria.where("task_id").is(exportTask.getId())).limit(1));

        Movie movieDataset = movieDatasets.get(0);
        String path = movieDataset.getFile_path();
        File file = new File(path);
//        File parentFile = file.getParentFile();
        String suffix = FileNameUtil.getSuffix(file);
        return task.getDefaultOutputDir() + "/*." + suffix;
    }

    private File getJobList() {

        List<CryosparcPatch> cryosparcPatches = this.cryosparcPatchRepository.findByQuery(Query.query(Criteria.where("exportTaskId").is(exportTask.getId()).and("status").is("success")));
        List<Map<String, String>> jobList = cryosparcPatches.stream().map(cryosparcPatch -> {
            CryosparcResult cryosparcResult = cryosparcPatch.getCryosparcResult();
            return Map.of(
                    "import", cryosparcResult.getImport_job_uid(),
                    "motion", cryosparcResult.getMotioncor_job_uid(),
                    "ctf", cryosparcResult.getCtf_job_uid()
            );
        }).toList();

        File workDir = getWorkDir();
        File file = new File(workDir, "cryosparc_job_list.json");
        JsonNode jsonNode = JsonUtil.valueToTree(jobList);
        FileUtil.writeString(jsonNode.toPrettyString(), file, StandardCharsets.UTF_8);
        exportSupport.toSelf(file);
        return file;
    }

    private File getWorkDir() {
        File workDir = filePathService.getWorkDir(task, exportTask.getTaskWorkDir());
        return workDir;
    }

    private File getOutputDir() {
//        if( isCryocparcUserHome() ) {
//            return getCryosparcOutputDir();
//        }
        int index = 0;
        while( true ) {
            File workDir = filePathService.getWorkDir(task, exportTask.getTaskWorkDir());
            File file = new File(workDir, "cryosparc_export" + index);
            if( !file.exists() ) {
                break;
            }
            index++;
        }
        return filePathService.getWorkDir(task, exportTask.getTaskWorkDir(), "cryosparc_export" + index);
    }


    public class CompleteSummaryProcessor extends Thread
    {
        private final File logFile;
        private final BufferedReader bufferedReader;
        private boolean running = true;

        public CompleteSummaryProcessor(File logFile) {
            this.logFile = logFile;
            if( !logFile.exists() ) {
                try {
                    logFile.createNewFile();
                } catch( IOException e ) {
                    throw new RuntimeException(e);
                }
            }
            exportSupport.toSelf(logFile);
            try {
                this.bufferedReader =

                        new BufferedReader(new FileReader(logFile, StandardCharsets.UTF_8));
            } catch( IOException e ) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void run() {
            while( running ) {
                String line;
                try {
                    while( ((line = bufferedReader.readLine()) != null) ) {
                        if( line.startsWith("{") && line.endsWith("}") ) {
                            ConstructProcessLog constructProcessLog = parseLine(line);
                            if( constructProcessLog != null ) {
                                cryosparcCompleteStatus.setSummary(constructProcessLog);
                                saveCryosparcCompleteStatus();
                            }

                        }
                    }
                } catch( IOException e ) {
                    log.error(e.getMessage(), e);
                }
            }
        }

        private ConstructProcessLog parseLine(String line) {
            return JsonUtil.readValue(line, ConstructProcessLog.class);
        }

        public void setRunning(boolean running) {
            this.running = running;
        }

        public boolean isRunning() {
            return running;
        }
    }

    public static void main(String[] args) {
        String line = "100%|█████████████████████████████████████████████████████████████████████████████████████████████████████████████████████████████████████████████████████████████████████| 128/128 [50:06<00:00, 23.49s/it]";
//        CompleteSummaryProcessor.setCryosparcCompleteStatus(line);
    }
}
