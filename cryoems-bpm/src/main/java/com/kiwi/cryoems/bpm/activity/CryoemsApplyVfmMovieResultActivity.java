package com.kiwi.cryoems.bpm.activity;

import com.kiwi.bpmn.component.utils.ExecutionUtils;
import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;
import com.kiwi.cryoems.bpm.dao.MovieResultRepository;
import com.kiwi.cryoems.bpm.model.MovieResult;
import com.kiwi.cryoems.bpm.movieresult.MovieResultAssemblyService;
import com.kiwi.cryoems.bpm.support.WorkflowVariableReader;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 将 VFM 结果写入已存在的 {@link MovieResult}。
 */
@ComponentDescription(
        name = "CryoEMS 应用 VFM 到 MovieResult",
        group = "CryoEM",
        version = "1.0",
        description =
                "根据 vfmLogFile 推断路径、复制 PNG 缩略图并解析预测框，更新已有 MovieResult。"
                        + "需在创建 MovieResult 之后调用。",
        inputs = {
                @ComponentParameter(
                        key = "movieResultId",
                        name = "MovieResult ID",
                        description = "已创建的 MovieResult 文档 id",
                        required = true),
                @ComponentParameter(
                        key = "vfmLogFile",
                        name = "VFM 日志",
                        description = "VFM *_predicted_boxes.txt 路径",
                        required = true)
        })
@Component("cyroemsApplyVfmMovieResultActivity")
@RequiredArgsConstructor
@Slf4j
public class CryoemsApplyVfmMovieResultActivity implements JavaDelegate {

    private final MovieResultRepository movieResultRepository;
    private final MovieResultAssemblyService movieResultAssemblyService;

    @Override
    public void execute(DelegateExecution execution) {
        try {
            doExecute(execution);
        } catch( Exception e ) {
            throw new RuntimeException(e);
        }
    }

    private void doExecute(DelegateExecution execution) throws Exception {
        String movieResultId = WorkflowVariableReader.requireText(execution, "movieResultId");
        String vfmLogFile = ExecutionUtils.getStringInputVariable(execution, "vfmLogFile").orElse(null);

        if (StringUtils.isBlank(vfmLogFile) || !Files.exists(Path.of(vfmLogFile))) {
            log.debug("VFM 日志缺失或不存在，跳过: {}", vfmLogFile);
            throw new BpmnError("MOVIE_RESULT_VFM", "VFM 日志缺失或不存在: " + vfmLogFile);
        }

        MovieResult movieResult =
                movieResultRepository
                        .findById(movieResultId)
                        .orElseThrow(() -> new IllegalArgumentException("MovieResult 不存在: " + movieResultId));

        movieResultAssemblyService.applyVfm(movieResult, execution, vfmLogFile);
        movieResultRepository.save(movieResult);

    }
}
