package com.cryo.task.tilt.stack;

import cn.hutool.core.collection.CollectionUtil;
import com.cryo.dao.MovieResultRepository;
import com.cryo.dao.dataset.MDocRepository;
import com.cryo.model.MDocResult;
import com.cryo.model.MovieResult;
import com.cryo.model.dataset.MDoc;
import com.cryo.service.FilePathService;
import com.cryo.service.cmd.SoftwareService;
import com.cryo.task.engine.Handler;
import com.cryo.task.engine.HandlerKey;
import com.cryo.task.engine.StepResult;
import com.cryo.task.engine.TaskStep;
import com.cryo.task.support.ExportSupport;
import com.cryo.task.tilt.MDocContext;
import com.cryo.task.tilt.TiltMeta;
import com.cryo.task.utils.NumberUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MdocStackHandler implements Handler<MDocContext>

{
    private final MovieResultRepository movieResultRepository;
    private final SoftwareService softwareService;
    private final FilePathService filePathService;
    private final ExportSupport exportSupport;
    private final MDocRepository mDocRepository;

    @Override
    public HandlerKey support() {
        return HandlerKey.MdocStack;
    }

    @Override
    public StepResult handle(MDocContext context) {
        MDocResult result = context.getResult();
        String name = context.getInstance().getName();
        List<TiltMeta> tilts = new ArrayList<>(result.getMeta().getTilts());
        MDoc mDoc = context.getMDoc();
        tilts.sort(Comparator.comparing(TiltMeta::getTiltAngle));
        if( CollectionUtil.isNotEmpty(mDoc.getMovie_data_ids()) ) {
            tilts = tilts.stream().filter(t -> mDoc.getMovie_data_ids().contains(t.getDataId())).toList();
        }


        List<String> ids = tilts.stream().map(t -> t.getMotionResultId()).toList();
        List<MovieResult> movieResults = this.movieResultRepository.findByQuery(Query.query(Criteria.where("_id").in(ids)));
        Map<String, MovieResult> movieResultMap = movieResults.stream().collect(Collectors.toMap(MovieResult::getId, Function.identity()));
        List<String> files = tilts.stream().map(t -> movieResultMap.get(t.getMotionResultId())).map(m -> {
            return m.getMotion().getDw().getPath();
        }).toList();
        File mdocWorkDir = filePathService.getMdocWorkDir(context);
        String titleFile = createTitleFile(context, tilts);
        File outputFile = new File(mdocWorkDir, name + "_raw_bin.mrc");

        StackResult stackResult = new StackResult();
        stackResult.setOutputFile(outputFile.getAbsolutePath());
        stackResult.setFiles(files);
        stackResult.setRawFiles(files);
        if( mDoc.isManualRebuild() ) {
            SoftwareService.CmdProcess cmdProcess = this.softwareService.mdoc_stack(files, outputFile);
            context.getInstance().addCmd("mdoc_stack", TaskStep.of(HandlerKey.MDOC_SLURM), cmdProcess.toCmdStr());
            stackResult.setTitlFile(titleFile);
        } else {

            SoftwareService.CmdProcess cmdProcess = this.softwareService.stack_and_filter(files, titleFile, outputFile.getAbsolutePath());
            cmdProcess.startAndWait();
//            context.getInstance().addCmd("mdoc_stack", TaskStep.of(HandlerKey.MDOC_SLURM), cmdProcess.toCmdStr());
            titleFile = new File(mdocWorkDir, name + "_raw_bin.rawtilt").getAbsolutePath();
            File excludeFile = new File(mdocWorkDir, name + "_raw_bin_files.txt");
            try {
                String txt = FileUtils.readFileToString(excludeFile, StandardCharsets.UTF_8);
                List<String> newFiles = Arrays.stream(StringUtils.split(txt, ",")).toList();
                stackResult.setFiles(newFiles);
            } catch( IOException e ) {
                throw new RuntimeException(e);
            }
            // update mdoc movie data ids if some files are excluded
            if( stackResult.getFiles().size() != files.size() ) {
                List<String> newDataIds = movieResults.stream().filter(m -> {
                    String path = m.getMotion().getDw().getPath();
                    return stackResult.getFiles().contains(path);
                }).map(MovieResult::getData_id).toList();
                mDoc.setMovie_data_ids(newDataIds);
                this.mDocRepository.save(mDoc);
            }

            stackResult.setTitlFile(titleFile);
            stackResult.setExcludeFile(excludeFile.toString());
            stackResult.setFiles(files);
        }


        result.setStackResult(stackResult);


        return StepResult.success("stack success");
    }

    private String createTitleFile(MDocContext context, List<TiltMeta> tilts) {
        List<Double> angles = tilts.stream().map(t -> {
            return t.getTiltAngle();
        }).toList();
        double middle;
        if( angles.size() % 2 == 0 ) {
            middle = (angles.get(angles.size() / 2) + angles.get(angles.size() / 2 - 1)) / 2.0;
        } else {
            middle = angles.get((angles.size() / 2));
        }

        List<String> lines = angles.stream().map(a -> {
            return NumberUtils.toString(a - middle);
        }).toList();
        String file = filePathService.getMdocWorkDir(context) + "/" + context.getInstance().getName() + ".rawtlt";
        try {
            FileUtils.writeLines(new File(file), lines);
            exportSupport.toSelf(new File(file));
        } catch( IOException e ) {
            throw new RuntimeException(e);
        }
        return file;
    }
}
