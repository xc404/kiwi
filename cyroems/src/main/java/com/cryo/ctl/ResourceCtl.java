package com.cryo.ctl;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.hutool.core.io.file.FileNameUtil;
import com.cryo.model.CtfEstimationSoftware;
import com.cryo.model.Microscope;
import com.cryo.model.MicroscopeConfig;
import com.cryo.model.MotionCorrectionSoftware;
import com.cryo.service.ExportTaskService;
import com.cryo.service.FilePathService;
import com.cryo.service.MicroscopeService;
import com.cryo.service.session.SessionService;
import com.cryo.service.session.SessionUser;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;


@Controller
@RequiredArgsConstructor
public class ResourceCtl {

    //    @Value("${task.root_dir}")
//    private String task_root_dir;
    @Data
    public static class DirInput {
        private String start_dir;
    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class MovieDirInput extends DirInput {
        private Microscope microscope;
    }

    @AllArgsConstructor
    public static class ResourceOutput {
        public String [] microscopes;
        public MotionCorrectionSoftware[] motion_corrections;
        public CtfEstimationSoftware[] ctf_estimations;
    }

    private final SessionService sessionService;
    private final FilePathService filePathService;
    private final MicroscopeService microscopeService;
    private final ExportTaskService exportTaskService;


    @GetMapping("/api/resources")
    @ResponseBody
    public ResourceOutput getResources() {
        return new ResourceOutput(new String[]{"Titan1_k3", "Titan2_k3", "Titan3_falcon"}, MotionCorrectionSoftware.values(), CtfEstimationSoftware.values());
    }

    @GetMapping("/api/get_task_output_dir")
    @ResponseBody
    @SaCheckLogin
    public List<PathNode> get_task_output_dir(DirInput dirInput) {

        SessionUser sessionUser = sessionService.getSessionUser();
        File startDir;
        if (dirInput.start_dir != null) {
            startDir = new File(dirInput.start_dir);
        } else {
            startDir = new File(sessionUser.getUser().getDefault_dir());
            return List.of(new PathNode(startDir,true));
        }

        if (!startDir.exists() || !startDir.isDirectory()) {
            throw new RuntimeException("Directory does not exist");
        }
        return Arrays.stream(startDir.listFiles(f -> f.isDirectory()))
                .sorted()
                .map(f -> new PathNode(f)).toList();
    }


    @GetMapping("/api/get_cryosparc_DATA_task_output_dir")
    @ResponseBody
    @SaCheckLogin
    public List<PathNode> get_cryosparc_task_output_dir(DirInput dirInput) {

        SessionUser sessionUser = sessionService.getSessionUser();
        File startDir;
        if (dirInput.start_dir != null) {
            startDir = new File(dirInput.start_dir);
        } else {
            startDir = exportTaskService.getDefaultCryosparcOutputDir();
            return List.of(new PathNode(startDir,true));
        }

        if (!startDir.exists() || !startDir.isDirectory()) {
            throw new RuntimeException("Directory does not exist");
        }
        return Arrays.stream(startDir.listFiles(f -> f.isDirectory()))
                .sorted()
                .map(f -> new PathNode(f)).toList();
    }

//    @GetMapping("/api/get_task_input_dir")
//    @ResponseBody
//    @SaCheckLogin
//    public List<PathNode> get_task_input_dir(MovieDirInput microscopeInput) {
//
//        MicroscopeConfig microscopeConfig = this.microscopeService.getMicroscopeConfig(microscopeInput.microscope);
//        String rootPath = microscopeConfig.getRoot_path();
//        File startDir;
//        if(StringUtils.isNotBlank(microscopeInput.getStart_dir())){
//            startDir = new File(microscopeInput.getStart_dir());
//        }else{
//            startDir = new File(rootPath);
//            return List.of(new PathNode(startDir,true));
//        }
//        if (!startDir.exists() || !startDir.isDirectory()) {
//            throw new RuntimeException("Directory does not exist");
//        }
//        return Arrays.stream(startDir.listFiles(f -> f.isDirectory()))
//                .sorted()
//                .map(f -> new PathNode(f)).toList();
//    }

    @GetMapping("/api/config/reload")
    @ResponseBody
    public Map<String, MicroscopeConfig> config_reload() {

        Map<String, MicroscopeConfig> configMap = this.microscopeService.reload();
        return configMap;
    }

    @Getter
    public static class PathNode {
        public final String key;
        public final String title;
        public final List<PathNode> children;

        public PathNode(File file) {
            this(file,false);
        }
        public PathNode(File file,boolean rescue) {
            this.key = file.getAbsolutePath();
            this.title = FileNameUtil.getPrefix(file);
            if(rescue){
                this.children = Arrays.stream(file.listFiles(f -> f.isDirectory())).map(f -> new PathNode(f)).toList();
            }else{
                this.children = null;
            }
        }

    }
}
