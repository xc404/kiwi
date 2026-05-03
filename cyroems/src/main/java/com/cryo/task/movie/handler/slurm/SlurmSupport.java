package com.cryo.task.movie.handler.slurm;

import cn.hutool.core.io.FileUtil;
import com.cryo.model.Instance;
import com.cryo.task.engine.*;
import com.cryo.service.FilePathService;
import com.cryo.service.cmd.SoftwareExe;
import com.cryo.service.cmd.SoftwareService;
import com.cryo.task.support.ExportSupport;
import com.cryo.task.utils.TaskUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import net.dreamlu.mica.core.utils.JsonUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.text.StringSubstitutor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.cryo.service.cmd.CmdUtils.wrapString;
import static org.apache.commons.io.FileUtils.readFileToString;

@Service
@RequiredArgsConstructor
public class SlurmSupport implements InitializingBean
{

    public static final String SLURM_RESULT = "slurmResult";
    public Map<HandlerKey, SlurmConfig> slurmConfigMap = new HashMap<>();

    @Value("${app.slurm_config}")
    private String slurm_config;
//    static {
//        slurmTemplateResources.put(MovieStep.Slurm, "classpath:cryoems-slurm.sh");
//        slurmTemplateResources.put(MovieStep.EXPORT_SLURM, "classpath:cryoems-export.sh");
//    }

    private final FilePathService filePathService;
    private final SoftwareService softwareService;
    private final ExportSupport exportSupport;

    @Override
    public void afterPropertiesSet() throws Exception {
        File file = ResourceUtils.getFile(slurm_config);
        Map<String, SlurmConfig> configMap = JsonUtil.readMap(new FileInputStream(file), String.class, SlurmConfig.class);
        configMap.forEach((key, value) -> {
            HandlerKey movieStep = EnumUtils.getEnumIgnoreCase(HandlerKey.class, key);
            slurmConfigMap.put(movieStep, value);
        });
    }

    @Data
    public static class SlurmConfig
    {
        private String templateFile;
        private SoftwareExe softwareExe;
    }


//    @Override
//    public MovieStep support() {
//        return MovieStep.Slurm;
//    }

    public StepResult handle(Context movieContext, TaskStep taskStep) {
//        MovieStep movieStep = movieContext.getMovie().getCurrent_step();
        Instance movie = movieContext.getInstance();
        List<StepCmd> list = movie.getCmds().stream().filter(cmd -> cmd.getExeStep().equals(taskStep)).toList();
        if( list.isEmpty() ) {
            return StepResult.success("No commands to execute, skipped");
        }
        HandlerKey handlerKey = taskStep.getKey();
        SlurmConfig slurmConfig = slurmConfigMap.getOrDefault(handlerKey, slurmConfigMap.get(HandlerKey.DefaultSlurm));
        String resourceTemplatePath = slurmConfig.getTemplateFile();

        File file;
        try {
            file = ResourceUtils.getFile(resourceTemplatePath);
        } catch( FileNotFoundException e ) {
            throw new RuntimeException(e);
        }
        File workDir = filePathService.getWorkDir(movieContext);
        String name = movie.getName();
        File target = new File(workDir, exportSupport.getUser() + "-" + taskStep + "-" + name + ".sh");
        try {

            String contentTemplate = readFileToString(file, StandardCharsets.UTF_8);
            Map<String, String> dataMap = new HashMap<>();
            // 创建StringSubstitutor，入参是要替换的业务数据map
            StringSubstitutor sub = new StringSubstitutor(dataMap);
            // 占位符字段替换为具体业务数据，入参为模板字符串

            dataMap.put("movie_name", name);
            String resolvedString = sub.replace(contentTemplate);
            FileUtils.writeStringToFile(target, resolvedString, StandardCharsets.UTF_8);
            List<String> lines = new ArrayList<>();
            list.forEach(cmd -> {
                lines.add("");
                lines.add("#" + cmd.getKey());
                lines.add("echo \"" + cmd.getKey() + " started at $(date +'%Y-%m-%d %H:%M:%S')\"");
                lines.add(cmd.getCmd());
                lines.add("echo \"" + cmd.getKey() + " ended at $(date +'%Y-%m-%d %H:%M:%S')\"");
            });
            FileUtil.appendLines(lines, target, StandardCharsets.UTF_8);

        } catch( IOException e ) {
            throw new RuntimeException(e);
        }
        softwareService.chmod(target, "755").startAndWait();
        softwareService.chown(target, exportSupport.getUser(), exportSupport.getUser()).startAndWait();
        File dir = filePathService.getWorkDir(movieContext,"logs");
        try {

            File logFile = FileUtils.getFile(dir, MessageFormat.format("/{0}-{1}.log", taskStep, name));
            if( logFile.exists() ) {
                FileUtils.forceDelete(logFile);
            }
            List<String> slurmArgs = new ArrayList<>();
            slurmArgs.add("--output");
            slurmArgs.add((logFile.getAbsolutePath()));
            slurmArgs.add("--error");
            slurmArgs.add((logFile.getAbsolutePath()));
            SoftwareService.CmdProcess slurm = softwareService.slurm(slurmConfig.softwareExe, target.getAbsolutePath(), slurmArgs);
            slurm.startAndWait();

            SlurmResult slurmResult = new SlurmResult();
            slurmResult.setBashFile(target.getAbsolutePath());
            slurmResult.setJobId(slurm.getJobId());
            slurmResult.setLogFile(logFile.getAbsolutePath());
//            movie.ad(slurmResult);

            StepResult slurmJobSubmitted = StepResult.success("Slurm job submitted");
            slurmJobSubmitted.getData().put(SLURM_RESULT, slurmResult);
            slurmJobSubmitted.setPersistent(false);

            SlurmAccResult slurmResult1 = getSlurmResult(slurmResult.getJobId());
            if( slurmResult1 != null ) {
                slurmResult.setNode(slurmResult1.getNode());
                slurmResult.setState(slurmResult1.getState());
                slurmResult.setExitcode(slurmResult1.getExitcode());
                if( "FAILED".equals(slurmResult1.getState()) ) {
                    slurmJobSubmitted.setSuccess(false);
                    slurmJobSubmitted.setRetryable(true);
                    slurmJobSubmitted.setMessage("slurm job error " + slurmResult.getJobId() + ", error " + slurmResult1.getExitcode() + ", node " + slurmResult1.getNode());
                    return slurmJobSubmitted;
                }
            }
            try {
                TaskUtils.checkFileExist(logFile.getAbsolutePath());
            } catch( Exception e ) {
                slurmJobSubmitted.setSuccess(false);
                slurmJobSubmitted.setRetryable(true);
                slurmJobSubmitted.setMessage("slurm job error " + slurmResult.getJobId() + ", slurm log not exist");
            }
            return slurmJobSubmitted;
        } catch( IOException e ) {
            throw new RuntimeException(e);
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SlurmAccResult
    {
        private String node;
        private String state;
        private String exitcode;
    }

    private SlurmAccResult getSlurmResult(String jobId) {
        SoftwareService.CmdProcess cmdProcess = this.softwareService.slurmAcctResult("nodelist,state,exitcode", jobId);
        cmdProcess.startAndWait();
        String result = cmdProcess.result();
        String[] lines = result.split("\n");
        if( lines.length < 3 ) {
            return null;
        }
        String[] split = lines[2].split("\\s+");
        String node = split[1];
        String state = split[2];
        String exitcode = split[3];
        return new SlurmAccResult(node, state, exitcode);
    }

}
