package com.kiwi.cryoems.bpm.activity;

import com.kiwi.bpmn.component.utils.ExecutionUtils;
import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;
import com.kiwi.cryoems.bpm.dao.MovieResultRepository;
import com.kiwi.cryoems.bpm.model.MrcMetadata;
import com.kiwi.cryoems.bpm.model.MovieResult;
import com.kiwi.cryoems.bpm.movieresult.MovieResultAssemblyService;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * 创建并持久化 {@link MovieResult}；motion / ctf / vfm 业务分别由对应 Section 处理。
 */
@ComponentDescription(
        name = "CryoEMS 创建 MovieResult",
        group = "CryoEM",
        version = "1.4",
        description =
                "仅需 motionNoDwMrc、ctfOutputFile、vfmLogFile；motion/ctf/vfm 三段业务分别推断路径、"
                        + "生成缩略图并写入 MovieResult。",
        inputs = {
                @ComponentParameter(
                        key = "motionVersion",
                        name = "MotionCor2 版本",
                        description = "MotionCor2 版本号；1.6+ 使用 -Patch-*.log 命名，否则使用 *_log0-Patch-*.log",
                        schema = @Schema(defaultValue = "1.4.5")),
                @ComponentParameter(
                        key = "motionNoDwMrc",
                        name = "MotionCor2 主输出 MRC",
                        description = "MotionCor2 主输出 .mrc 路径",
                        required = true),
                @ComponentParameter(
                        key = "ctfOutputFile",
                        name = "CTFFIND5 输出 MRC",
                        description = "CTFFIND5 *_freq.mrc 路径",
                        required = true),
                @ComponentParameter(
                        key = "vfmLogFile",
                        name = "VFM 日志",
                        description = "VFM *_predicted_boxes.txt 路径；未跑 VFM 时可省略",
                        required = false)
        },
        outputs = {
                @ComponentParameter(
                        key = "movieResultId",
                        name = "movieResultId",
                        description = "MovieResult 文档 id",
                        schema = @Schema(defaultValue = "movieResultId")),
                @ComponentParameter(
                        key = "movieResultCreated",
                        name = "movieResultCreated",
                        description = "本次是否新建文档",
                        schema = @Schema(defaultValue = "movieResultCreated")),
                @ComponentParameter(
                        key = "movieResult",
                        name = "movieResult",
                        description = "MovieResult 对象（供后续节点使用）",
                        schema = @Schema(defaultValue = "movieResult"))
        })
@Component("cyroemsCreateMovieResultActivity")
@RequiredArgsConstructor
@Slf4j
public class CryoemsCreateMovieResultActivity implements JavaDelegate {

    private final MovieResultRepository movieResultRepository;
    private final MovieResultAssemblyService movieResultAssemblyService;

    @Override
    public void execute(DelegateExecution execution) {
        try {
            doExecute(execution);
        } catch (BpmnError e) {
            throw e;
        } catch (Exception e) {
            throw new BpmnError("MOVIE_RESULT", "创建 MovieResult 失败: " + e.getMessage(), e);
        }
    }

    private void doExecute(DelegateExecution execution) throws Exception {


        MovieResult movieResult = new MovieResult();
        String movieId = ExecutionUtils.getStringInputVariableAtPath(execution, "movie.id").orElse("");
        String configId = ExecutionUtils.getStringInputVariableAtPath(execution, "task.config_id").orElse("");
        String movieDataId = ExecutionUtils.getStringInputVariableAtPath(execution, "movie.data_id").orElse("");
        movieResult.setId(movieId);
        movieResult.setInstance_id(movieId);
        movieResult.setConfig_id(configId);
        movieResult.setMovie_data_id(movieDataId);
        movieResultAssemblyService.assemble(movieResult, execution);
        movieResult = movieResultRepository.save(movieResult);

        execution.setVariable("movieResultId", movieResult.getId());
//        execution.setVariable("movieResult", movieResult);

    }
}
