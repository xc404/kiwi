package com.cryo.service.vfm;

import com.cryo.model.Instance;
import com.cryo.model.Movie;
import com.cryo.service.cmd.SoftwareExe;
import com.cryo.service.cmd.SoftwareService;
import com.cryo.task.engine.BaseContext;
import com.cryo.task.engine.HandlerKey;
import com.cryo.task.engine.StepResult;
import com.cryo.task.engine.TaskStep;
import com.cryo.task.movie.MovieContext;
import com.cryo.task.movie.handler.slurm.SlurmSupport;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dreamlu.mica.core.utils.JsonUtil;
import org.awaitility.core.ConditionTimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.time.Duration;
import java.util.concurrent.Semaphore;

import static org.awaitility.Awaitility.await;

@Service
@RequiredArgsConstructor
@Slf4j
public class VfmService
{
    private final SoftwareService softwareService;
    private final SlurmSupport slurmSupport;
    @Value("${app.vfm.endpoint:http://localhost:8008/tasks}")
    private String vfm_endpoint = "http://localhost:8008/tasks";
    @Value("${app.vfm.mode:shell}")
    private String vmf_mode = "shell";
    private final RestTemplate restTemplate = new RestTemplate();

    private Semaphore concurrentLimitLock = new Semaphore(180);

    public StepResult handle(BaseContext movieContext, String input, String output, VFMParams params) {
        if( vmf_mode.equals("endpoint") ) {
            return this.vfmWithApi(movieContext, input, output, params);
        }
        return this.vfmInShell(movieContext, input, output, params);
    }

    private StepResult vfmInShell(BaseContext movieContext, String input, String output, VFMParams params) {

        SoftwareService.CmdProcess cmdProcess = this.softwareService.vfm(input, output, params);
        TaskStep slurm = TaskStep.of(movieContext.getCurrentStep().getStep(), HandlerKey.VFM_SLURM);
        Movie instance = (Movie) movieContext.getInstance();
        instance.addCmd(SoftwareExe.vfm.name(), slurm, cmdProcess.toString());
//        VFMResult vfmResult = new VFMResult();
//        vfmResult.setLogFile(logFile.getAbsolutePath());
//        vfmResult.setPngFile(pngFile.getAbsolutePath());
//        vfmResult.setOutputFile(outputFile.getAbsolutePath());
        TaskStep slurmStep = TaskStep.of(movieContext.getCurrentStep().getStep(), HandlerKey.VFM_SLURM);
        StepResult handle = this.slurmSupport.handle(movieContext, slurmStep);
        return handle;
    }

    private StepResult vfmWithApi(BaseContext movieContext, String input, String output, VFMParams params) {

        try {
            concurrentLimitLock.acquire();
            File outputFile = new File(output);
            String parent = outputFile.getParent();
            ObjectNode objectNode = JsonUtil.createObjectNode();
            objectNode.put("file_path", input);
            objectNode.put("output_dir", parent);
            objectNode.put("df1", params.getDf1());
            objectNode.put("df2", params.getDf2());
            objectNode.put("dfang", params.getDfang());
            objectNode.put("vol_kv", params.getVol_kv());
            objectNode.put("cs_mm", params.getCs_mm());
            objectNode.put("w", params.getW());
            objectNode.put("phase_shift", params.getPhase_shift());
            objectNode.put("psize_in", params.getPsize_in());
            objectNode.put("picking", params.isPicking());

            ResponseEntity<String> stringResponseEntity = restTemplate.postForEntity(vfm_endpoint, objectNode, String.class);
            log.info("vfm request " +  stringResponseEntity.getBody());
            if( stringResponseEntity.getBody() == null || !stringResponseEntity.getBody().contains("Task has been added to the queue") ) {
                throw new RuntimeException("vfm endpoint " + stringResponseEntity.getBody());
            }
            try {
                await().pollInterval(Duration.ofSeconds(10)).atMost(Duration.ofSeconds(600)).until(() -> outputFile.exists());
            } catch( ConditionTimeoutException e ) {
                return StepResult.error("vfm endpoint no file create + " + stringResponseEntity.getBody());
            }
            return StepResult.success("vfm endpoint " + objectNode);
        } catch( InterruptedException e ) {
            throw new RuntimeException(e);
        }finally {
            concurrentLimitLock.release();
        }

    }
}
