package com.cryo.service.cmd;

import cn.hutool.core.exceptions.ExceptionUtil;
import com.cryo.common.error.FatalException;
import com.cryo.common.error.RetryException;
import com.cryo.model.Microscope;
import com.cryo.service.vfm.VFMParams;
import com.cryo.task.engine.Context;
import com.cryo.task.export.cryosparc.CryosparcConstructParams;
import com.cryo.task.export.cryosparc.CryosparcProject;
import com.cryo.task.movie.MovieContext;
import com.cryo.task.utils.NumberUtils;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.dreamlu.mica.core.utils.JsonUtil;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
@RequiredArgsConstructor
public class SoftwareService implements InitializingBean
{

    public static final long DEFAULT_TIMEOUT = 6000;

    @Value("${app.software_config}")
    private String config_path;

    private final SlurmService slurmService;
    @Value("${app.slurm.enabled:true}")
    private boolean slurmEnabled = true;

    public CmdProcess cmdProcess(SoftwareConfig softwareConfig) {
        return new CmdProcess(softwareConfig);
    }


    @Data
    public static class SoftwareConfig
    {
        public Semaphore concurrentLock;
        public Lock rateLimitLock = new ReentrantLock(true);
        private long timeout = DEFAULT_TIMEOUT;
        private String path;
        private String args;
        private String slurmArgs;
        private int concurrentLimit;
        private long startime = 10;

        public boolean slurm() {
            return StringUtils.isNotBlank(this.slurmArgs);
        }

        public List<String> cmds() {
            ArrayList<String> cmds = new ArrayList<>();
            cmds.addAll(Arrays.stream(this.path.split(" +")).toList());
            if( this.args != null ) {
                String[] split = this.args.split(" +");
                cmds.addAll(Arrays.stream(split).toList());
            }
            return cmds;
        }


    }

    private final Map<SoftwareExe, SoftwareConfig> softwareConfigs = new HashMap<>();
    private final CmdProcess EmptyCmdProcess = new CmdProcess(null);
    ;

    public SoftwareConfig getSoftwareCmd(SoftwareExe softwareExe) {
        SoftwareConfig softwareConfig = new SoftwareConfig();
        softwareConfig.setPath(softwareExe.software());
        return this.softwareConfigs.getOrDefault(softwareExe, softwareConfig);


    }

    @Override
    public void afterPropertiesSet() throws Exception {

        File file = ResourceUtils.getFile(config_path);
        Map<String, SoftwareConfig> configMap = JsonUtil.readMap(new FileInputStream(file), String.class, SoftwareConfig.class);
        configMap.forEach((key, value) -> {
            SoftwareExe softwareExe = EnumUtils.getEnumIgnoreCase(SoftwareExe.class, key);
            if( value.path == null ) {
                value.path = softwareExe.software();
            }
            if( value.concurrentLimit > 0 ) {

                value.concurrentLock = new Semaphore(value.concurrentLimit);
            }
            softwareConfigs.put(softwareExe, value);
        });
    }

    public CmdProcess group(String name) {
        SoftwareConfig softwareCmd = this.getSoftwareCmd(SoftwareExe.groups);
        CmdProcess builder = new CmdProcess(softwareCmd);
        builder.command(name);
        return builder;
    }

    public CmdProcess checkPassword(String username, String password) {
        SoftwareConfig softwareCmd = this.getSoftwareCmd(SoftwareExe.check_password);
        CmdProcess builder = new CmdProcess(softwareCmd);
        builder.command("-u", username, "-p", password);
        return builder;
    }

    public CmdProcess getUserSpace(String name) {
        SoftwareConfig softwareCmd = this.getSoftwareCmd(SoftwareExe.user_df_space);
        CmdProcess builder = new CmdProcess(softwareCmd);
        builder.command(name);
        return builder;
    }

    public CmdProcess setfacl(File file, String permission) {

        return this.setfacl(file, permission, false);
    }

    public CmdProcess setfacl(File file, String permission, boolean rescue) {

        SoftwareConfig softwareCmd = this.getSoftwareCmd(SoftwareExe.setfacl);
        CmdProcess builder = new CmdProcess(softwareCmd);
        builder.command("-R");
        if( file.isDirectory() && rescue ) {
        }
        builder.command("-m", permission);
        builder.command(file.getAbsolutePath());
        return builder;
    }

    public CmdProcess chmod(File file, String permission) {

        SoftwareConfig softwareCmd = this.getSoftwareCmd(SoftwareExe.chmod);
        CmdProcess builder = new CmdProcess(softwareCmd);
//        if( file.isDirectory() ) {
//
//            builder.command("-R");
//        }
        builder.command(permission);
        builder.command(file.getAbsolutePath());

        return builder;
    }

    public CmdProcess cp(String pattern, File destDir) {

        SoftwareConfig softwareCmd = this.getSoftwareCmd(SoftwareExe.cp);
        CmdProcess builder = new CmdProcess(softwareCmd);
//        if( file.isDirectory() ) {
//
//            builder.command("-R");
//        }
        builder.command("-r", "-f");

        builder.command(pattern, destDir.getAbsolutePath());

        return builder;
    }

    public CmdProcess chown(File file, String user, String group) {
        if( !slurmEnabled ) {
            return EmptyCmdProcess;
        }
        SoftwareConfig softwareCmd = this.getSoftwareCmd(SoftwareExe.chown);
        CmdProcess builder = new CmdProcess(softwareCmd);
        if( file.isDirectory() ) {

            builder.command("-R");
        }
        if( StringUtils.isEmpty(group) ) {
            group = user;
        }
        builder.command(user + ":" + group);
        builder.command(file.getAbsolutePath());

        return builder;
    }

    public CmdProcess squeue() {
        SoftwareConfig softwareCmd = this.getSoftwareCmd(SoftwareExe.squeue);
        CmdProcess builder = new CmdProcess(softwareCmd);
        return builder;
    }

    public CmdProcess copy_and_change_own(String source, String destDir, String owner, String group) {
        SoftwareConfig softwareCmd = this.getSoftwareCmd(SoftwareExe.copy_and_change_own);
        CmdProcess builder = new CmdProcess(softwareCmd);
        builder.command(source, destDir, owner + ":" + group);
        return builder;
    }

    public CmdProcess e2proc2d(String input, String output) {
        SoftwareConfig softwareCmd = this.getSoftwareCmd(SoftwareExe.e2proc2d);
        CmdProcess builder = new CmdProcess(softwareCmd);
        builder.command("--process", "math.reciprocal", input, output);
        return builder;
    }


    public CmdProcess dm2mrc(String input, String output) {
        SoftwareConfig softwareCmd = this.getSoftwareCmd(SoftwareExe.dm2mrc);
        CmdProcess builder = new CmdProcess(softwareCmd);
        builder.command(input, output);

        return builder;
    }


    public CmdProcess tif2mrc(String input, String output) {
        SoftwareConfig softwareCmd = this.getSoftwareCmd(SoftwareExe.tif2mrc);
        CmdProcess builder = new CmdProcess(softwareCmd);
        builder.command(input, output);
        return builder;
    }

    public CmdProcess patch_log_png(String input, String output) {
        SoftwareConfig softwareCmd = this.getSoftwareCmd(SoftwareExe.motion_patch_png);
        CmdProcess builder = new CmdProcess(softwareCmd);
        builder.command("-i", input, "-o", output);
        return builder;
    }

    public CmdProcess ctf_png(String input, String output) {
        SoftwareConfig softwareCmd = this.getSoftwareCmd(SoftwareExe.ctf_png);
        CmdProcess builder = new CmdProcess(softwareCmd);
        builder.command("-i", input, "-o", output);
        return builder;
    }

    public CmdProcess mrc_png(String input, String output) {
        SoftwareConfig softwareCmd = this.getSoftwareCmd(SoftwareExe.mrc_png);
        CmdProcess builder = new CmdProcess(softwareCmd);
        builder.command("-i", input, "-o", output);
        return builder;
    }

    public CmdProcess titan3_mrc_png(String input, String output) {
        SoftwareConfig softwareCmd = this.getSoftwareCmd(SoftwareExe.titan3_mrc_png);
        CmdProcess builder = new CmdProcess(softwareCmd);
        builder.command("--mrc_file", input, "--output_file", output);
        return builder;
    }

    public CmdProcess titanMean(SoftwareExe softwareExe, String input) {
        SoftwareConfig softwareCmd = this.getSoftwareCmd(softwareExe);
        CmdProcess builder = new CmdProcess(softwareCmd);
        builder.command(input);
        return builder;
    }

    public CmdProcess createCryosparcProject(String output) {
        SoftwareConfig softwareCmd = this.getSoftwareCmd(SoftwareExe.cryosparc_create_project);
        CmdProcess builder = new CmdProcess(softwareCmd);
        builder.command("--output_path", output);
        return builder;
    }

    public CmdProcess cryosparc_motion_job(CryosparcProject project, String inputParamsFile, String output) {
        SoftwareConfig softwareCmd = this.getSoftwareCmd(SoftwareExe.cryosparc_motion_job);
        CmdProcess builder = new CmdProcess(softwareCmd);
        builder.command("--project_uid", project.getProject_uid());
        builder.command("--workspace_uid", project.getWorkspace_uid());
        builder.command("--config_file", inputParamsFile);
        builder.command("--output_file", output);
        builder.command("--debug");
        return builder;
    }

    public CmdProcess cryosparcConstruct(CryosparcConstructParams params) {
        SoftwareConfig softwareCmd = this.getSoftwareCmd(SoftwareExe.cryosparc_construct);
        CmdProcess builder = new CmdProcess(softwareCmd);
//        builder.command("--user", us    er);
//        builder.command( SoftwareExe.cryosparc_complete.software());
        builder.command("--project_uid", (params.getProject_uid()));
        builder.command("--workspace_uid", (params.getWorkspace_uid()));
//        builder.command("--raw_movie_paths", params.getRaw_movie_paths());
        builder.command("--gain_path", (params.getGain_path()));
//        builder.command("--param_file", (params.getParam_file()));
        builder.command("--output_dir", (params.getOutput_dir()));
        builder.command("--task_name", (params.getTask_name()));
        builder.command("--tmp_output_file", (params.getTmp_output_file()));
//        builder.command("--denoise_res_dir", (params.getDenoise_res_dir()));
        builder.command("--job_list_file", (params.getJob_list_file()));

        if( params.getOwner() != null ) {
            builder.command("--owner", (params.getOwner()));
        }

        if( params.getGroup() != null ) {
            builder.command("--group", (params.getGroup()));
        }
        if( params.getAcl_users() != null ) {
            builder.command("--acl_users", params.getAcl_users());
        }
        builder.command("--raw_movie_paths", quote(params.getRaw_movie_paths()));
        return builder;
    }

    public CmdProcess cryosparc_write_star(String inputDir, String outputFile) {
        SoftwareConfig softwareCmd = this.getSoftwareCmd(SoftwareExe.cryosparc_write_star);
        CmdProcess builder = new CmdProcess(softwareCmd);
        builder.command(" --txt_dir", inputDir);
        builder.command("--output_star_file", outputFile);
        return builder;
    }


    public CmdProcess mdoc_stack(List<String> files, File outputFile) {
        SoftwareConfig softwareCmd = this.getSoftwareCmd(SoftwareExe.mdoc_stack);
        CmdProcess builder = new CmdProcess(softwareCmd);
        builder.command("--files", StringUtils.join(files, ","));
        builder.command("--output", outputFile.toString());
        return builder;
    }


    public CmdProcess slurm(SoftwareExe softwareExe, String running_file, List<String> slurmArgs) {
        SoftwareConfig softwareCmd = this.getSoftwareCmd(softwareExe);
//        if(!slurmEnabled){
//            softwareExe = SoftwareExe.sh;
//        }
        CmdProcess builder = new CmdProcess(softwareCmd);
        builder.command(running_file);
        if( slurmEnabled ) {
            builder.slurm(slurmArgs.toArray(new String[0]));
        }
        return builder;
    }

    public CmdProcess slurmAcctResult(String format, String jobId) {
        SoftwareConfig softwareCmd = this.getSoftwareCmd(SoftwareExe.sacct);
        CmdProcess builder = new CmdProcess(softwareCmd);
        builder.command("--format=" + format);
        builder.command("-j", jobId);
        return builder;
    }


    public CmdProcess motionCor2
            (String fileName, MotionCor2Params params, String logfile) {
        SoftwareConfig softwareCmd = this.getSoftwareCmd(SoftwareExe.MotionCor2);

        CmdProcess builder = new CmdProcess(softwareCmd);
        if( params.microscope == "Titan1_k3" || params.microscope == "Titan2_k3" ) {
            builder.command("-InTiff", wrapString(params.input));
            if( params.major_scale != null ) {
                builder.command("-Mag", NumberUtils.toString(params.major_scale), NumberUtils.toString(params.minor_scale), NumberUtils.toString(params.distort_ang));
            }
            if( params.fmdose != null ) {
                builder.command("-FmDose", NumberUtils.toString(params.fmdose));
            }
        } else if( params.microscope == "Titan3_falcon" ) {
            builder.command("-InEer", wrapString(params.input));
            if( params.eer_sampling != null ) {
                builder.command("-EerSampling", NumberUtils.toString(params.eer_sampling));
            }
            if( StringUtils.isNotBlank(params.eer_frac_path) ) {
                builder.command("-FmIntFile", params.eer_frac_path);
            }
            if( StringUtils.isNotBlank(params.defectFile) ) {
                builder.command("-DefectFile", params.defectFile);
            }
        } else {
            throw new RuntimeException(String.format("Microscope %s is not supported.", params.microscope));
        }

        builder.command("-Gain", wrapString(params.gain));
        builder.command("-OutMrc", params.output);
        builder.command("-FtBin", NumberUtils.toString(params.binning));
        builder.command("-Patch", NumberUtils.toString(params.patch), NumberUtils.toString(params.patch));
        builder.command("-PixSize", NumberUtils.toString(params.pixsize));
        builder.command("-kV", NumberUtils.toString(params.accel_kv));
        builder.command("-LogFile", logfile);
        builder.command("-LogDir", new File(params.output).getParentFile().getAbsolutePath());

//        builder.slurm("--job-name", "motionCor2-" + fileName);
        return builder;
    }

//    public CmdProcess slurm(SlurmParams params, CmdProcess process) {
//        SoftwareConfig softwareCmd = this.getSoftwareCmd(SoftwareExe.Slurm);
//
//        CmdProcess builder = new CmdProcess();
//        builder.command(softwareCmd.cmds());
//        if (StringUtils.isNotBlank(params.jobName)) {
//
//            builder.command("--job-name="+params.jobName);
//        }
//        if (StringUtils.isNotBlank(params.gres)) {
//
//            builder.command("--gres="+ params.gres);
//        }
//        if (StringUtils.isNotBlank(params.partition)) {
//
//            builder.command("--partition="+params.partition);
//        }
//        if (params.exclusive) {
//            builder.command("--exclusive");
//        }
//        builder.command(process.command());
//        return builder;
//    }

    public CmdProcess ctffind5(String input, String output, CtffindParams params) {
        SoftwareConfig software = this.getSoftwareCmd(SoftwareExe.ctffind5);
        CmdProcess cmdProcess = new CmdProcess(software);
        cmdProcess.command("-i", input);
        cmdProcess.command("-o", output);
        cmdProcess.command("-p", NumberUtils.toString(params.getPixel_size()));
        cmdProcess.command("-v", NumberUtils.toString(params.getAccel_kv()));
        cmdProcess.command("-c", NumberUtils.toString(params.getCs_mm()));
        cmdProcess.command("-a", NumberUtils.toString(params.getAmp_contrast()));
        cmdProcess.command("-s", NumberUtils.toString(params.getSpectrum_size()));
        cmdProcess.command("--rmin", (NumberUtils.toString(params.getMin_res())));
        cmdProcess.command("--rmax", (NumberUtils.toString(params.getMax_res())));
        cmdProcess.command("--dmin", (NumberUtils.toString(params.getMin_defocus())));
        cmdProcess.command("--dmax", (NumberUtils.toString(params.getMax_defocus())));
        cmdProcess.command("--step", (NumberUtils.toString(params.getDefocus_step())));

        return cmdProcess;
    }

    private String quote(String string) {
        return "'" + string + "'";
    }

    public CmdProcess vfm(String input, String output, VFMParams params) {
        SoftwareConfig software = this.getSoftwareCmd(SoftwareExe.vfm);
        if( params.isCryosparc() ) {
            software = this.getSoftwareCmd(SoftwareExe.cryosparc_vfm);
        }
        CmdProcess cmdProcess = new CmdProcess(software);
        cmdProcess.command("--input", input);
        cmdProcess.command("--output", output);
        cmdProcess.command("--df1", NumberUtils.toString(params.getDf1()));
        cmdProcess.command("--df2", NumberUtils.toString(params.getDf2()));
        cmdProcess.command("--dfang", NumberUtils.toString(params.getDfang()));
        cmdProcess.command("--vol_kv", NumberUtils.toString(params.getVol_kv()));
        cmdProcess.command("--cs_mm", NumberUtils.toString(params.getCs_mm()));
        cmdProcess.command("--w", NumberUtils.toString(params.getW()));
        cmdProcess.command("--phase_shift", NumberUtils.toString(params.getPhase_shift()));
        cmdProcess.command("--psize_in", NumberUtils.toString(params.getPsize_in()));

        if( params.isCryosparc() ) {
            if( params.isPicking() ) {
                cmdProcess.command("--picking");
            }
        } else {
            cmdProcess.command("--picking", String.valueOf(params.isPicking()));
        }

        return cmdProcess;
    }

    public CmdProcess subtarction(String input, String output) {
        SoftwareConfig software = this.getSoftwareCmd(SoftwareExe.subtarction);
        CmdProcess cmdProcess = new CmdProcess(software);
        cmdProcess.command("--input", input);
        cmdProcess.command("--output", output);

        return cmdProcess;
    }

    public CmdProcess ruijingDefect(String input, String output) {
        SoftwareConfig software = this.getSoftwareCmd(SoftwareExe.ruijingDefect);
        CmdProcess cmdProcess = new CmdProcess(software);
        cmdProcess.command("--input", input);
        cmdProcess.command("--output", output);

        return cmdProcess;
    }

    public CmdProcess header(String movieFile, String outputFile) {
        SoftwareConfig softwareCmd = getSoftwareCmd(SoftwareExe.header);
        CmdProcess cmdProcess = new CmdProcess(softwareCmd);
        cmdProcess.command(movieFile);
//        cmdProcess.command(">", outputFile);
        cmdProcess.redirectOutput(ProcessBuilder.Redirect.to(new File(outputFile)));
        return cmdProcess;
    }

    public CmdProcess scancel(List<String> jobIds) {

        SoftwareConfig softwareCmd = getSoftwareCmd(SoftwareExe.scancel);
        CmdProcess cmdProcess = new CmdProcess(softwareCmd);
        cmdProcess.command(StringUtils.join(jobIds, ","));
        return cmdProcess;
    }


    public CmdProcess tiltxcorr(String input, String output, TiltxcorrParams params) {
        SoftwareConfig softwareCmd = getSoftwareCmd(SoftwareExe.tiltxcorr);
        CmdProcess cmdProcess = new CmdProcess(softwareCmd);
        cmdProcess.command("-inp", input);
        cmdProcess.command("-ou", output);
        cmdProcess.command("-ro", params.getTilt_axis_angle());
        cmdProcess.command("-ti", params.getTilt_path());
        cmdProcess.command("-sigma1", params.getSigma1());
        cmdProcess.command("-radius2", params.getRadius2());
        cmdProcess.command("-sigma2", params.getSigma2());
        if( StringUtils.isNotBlank(params.getBorder()) ) {
            cmdProcess.command("-bor", params.getBorder() + "," + params.getBorder());
        }
        if( StringUtils.isNotBlank(params.getIt()) ) {
            cmdProcess.command("-it", params.getIt());
        }
        if( StringUtils.isNotBlank(params.getIm()) ) {
            cmdProcess.command("-im", params.getIm());
        }
        if( StringUtils.isNotBlank(params.getPatch_size()) ) {
            cmdProcess.command("-size", params.getPatch_size() + "," + params.getPatch_size());
        }
        if( StringUtils.isNotBlank(params.getOverlap()) ) {
            cmdProcess.command("-overlap", params.getOverlap() + "," + params.getOverlap());
        }
        return cmdProcess;
    }


    public CmdProcess xftoxg(String input, String output, String n) {
        SoftwareConfig softwareCmd = getSoftwareCmd(SoftwareExe.xftoxg);
        CmdProcess cmdProcess = new CmdProcess(softwareCmd);
        cmdProcess.command("-n", n);
        cmdProcess.command("-in", input);
        cmdProcess.command("-g", output);
        return cmdProcess;
    }

    public CmdProcess newStack(String input, String output, NewstackArgs args) {
        SoftwareConfig softwareCmd = getSoftwareCmd(SoftwareExe.newstack);
        CmdProcess cmdProcess = new CmdProcess(softwareCmd);
        cmdProcess.command("-in", input);
        cmdProcess.command("-ou", output);
        if( StringUtils.isNotBlank(args.getBin()) ) {

            cmdProcess.command("-bin", args.getBin());
        }
        if( StringUtils.isNotBlank(args.getMo()) ) {

            cmdProcess.command("-mo", args.getMo());
        }
        if( StringUtils.isNotBlank(args.getFl()) ) {

            cmdProcess.command("-fl", args.getFl());
        }
        if( StringUtils.isNotBlank(args.getPrexg()) ) {

            cmdProcess.command("-x", args.getPrexg());
        }
        if( StringUtils.isNotBlank(args.getIm()) ) {
            cmdProcess.command("-im", args.getIm());
        }
        if( StringUtils.isNotBlank(args.getTa()) ) {
            cmdProcess.command("-ta", args.getTa());
        }
        if( StringUtils.isNotBlank(args.getXf()) ) {
            cmdProcess.command("-x", args.getXf());
        }
        return cmdProcess;
    }

    public CmdProcess imodchopconts(String input, String output, ImodchopcontsArgs args) {
        SoftwareConfig softwareCmd = getSoftwareCmd(SoftwareExe.imodchopconts);
        CmdProcess cmdProcess = new CmdProcess(softwareCmd);
        cmdProcess.command("-i", input);
        cmdProcess.command("-ou", output);
        cmdProcess.command("-overlap", args.getOverlap());
        cmdProcess.command("-s", args.getS());
        return cmdProcess;
    }

    public CmdProcess series_align(SeriesAlignArgs args) {
        SoftwareConfig softwareCmd = getSoftwareCmd(SoftwareExe.tilt_series_align);
        CmdProcess cmdProcess = new CmdProcess(softwareCmd);
        cmdProcess.command("--image_file", args.getMrc_file());
        cmdProcess.command("--model_file", args.getModel_file());
        cmdProcess.command("--tilt_path", args.getTilt_path());
        cmdProcess.command("--tilt_axis_angle", args.getTilt_axis_angle());
        cmdProcess.command("--pixel_size", args.getPixel_size());
        cmdProcess.command("--max_avg", args.getMax_avg());
        return cmdProcess;
    }

    public CmdProcess xfproduct(String input1, String input2, String output) {
        SoftwareConfig softwareCmd = getSoftwareCmd(SoftwareExe.xfproduct);
        CmdProcess cmdProcess = new CmdProcess(softwareCmd);
        cmdProcess.command("-in1", input1);
        cmdProcess.command("-in2", input2);
        cmdProcess.command("-ou", output);
        cmdProcess.command("--s", "1,4");
        return cmdProcess;
    }

    public CmdProcess patch2imod(String input, String output) {
        SoftwareConfig softwareCmd = getSoftwareCmd(SoftwareExe.patch2imod);
        CmdProcess cmdProcess = new CmdProcess(softwareCmd);
        cmdProcess.command("-s", "10");
        cmdProcess.command(input);
        cmdProcess.command("-ou", output);
        return cmdProcess;
    }

    public CmdProcess tilt(String input, String output, TiltArgs args) {
        SoftwareConfig softwareCmd = getSoftwareCmd(SoftwareExe.tilt);
        CmdProcess cmdProcess = new CmdProcess(softwareCmd);
        cmdProcess.command("-inp", input);
        cmdProcess.command("-ou", output);
        cmdProcess.command("-TILTFILE", args.getTilt_path());
        cmdProcess.command("-XTILTFILE", args.getXtilt());
        cmdProcess.command("-THICKNESS 1600 -IMAGEBINNED 4 -RADIAL 0.35,0.035");
        cmdProcess.command("-FalloffIsTrueSigma 1 -XAXISTILT 0 -SCALE 0,330 -PERPENDICULAR -MODE 2");
        cmdProcess.command("-AdjustOrigin -ActionIfGPUFails 1,2");
        cmdProcess.command("-FakeSIRTiterations 30 -SUBSETSTART 0,0");
        return cmdProcess;
    }

    public CmdProcess stack_and_filter(List<String> files, String inputTilt, String output) {

        SoftwareConfig softwareCmd = getSoftwareCmd(SoftwareExe.stack_and_filter);
        CmdProcess cmdProcess = new CmdProcess(softwareCmd);

        cmdProcess.command("--files", StringUtils.join(files, ","));
        cmdProcess.command("--input_tilt", inputTilt);
        cmdProcess.command("--output", output);
//        cmdProcess.command("--output_tilt", outputTilt);
//        cmdProcess.command("--plot-dir", plotDir);
        return cmdProcess;

    }

    public CmdProcess binvol(String input, String output) {
        SoftwareConfig softwareCmd = getSoftwareCmd(SoftwareExe.binvol);
        CmdProcess cmdProcess = new CmdProcess(softwareCmd);
        cmdProcess.command("-b", "2");
        cmdProcess.command("-o", output);
        cmdProcess.command(input);
        return cmdProcess;
    }

    public CmdProcess align_recon(String input, String output) {
        SoftwareConfig softwareCmd = getSoftwareCmd(SoftwareExe.align_recon);
        CmdProcess cmdProcess = new CmdProcess(softwareCmd);
        cmdProcess.command("-in", input);
        cmdProcess.command("-ou", output);
        return cmdProcess;
    }

    public String wrapString(String str) {
        if( slurmEnabled ) {
            return CmdUtils.wrapString(str);
        }
        return str;
    }


    @Data
    public static class SlurmParams
    {
        private String jobName = null;
        private String gres = null;
        private String partition = null;
        private boolean exclusive = false;
    }

    @Data
    public static class CtffindParams
    {
        private double pixel_size;
        private double accel_kv;
        private double cs_mm;
        private double amp_contrast;
        private double spectrum_size;
        private double min_res;
        private double max_res;
        private double min_defocus;
        private double max_defocus;
        private double defocus_step;
    }

    @Data
    public static class MotionCor2Params
    {
        private String microscope;
        private String input;
        private String output;
        private String gain;
        private int binning;
        private Integer eer_sampling;
        private String eer_frac_path;
        private int patch;
        private double pixsize;
        private double accel_kv;
        private Double fmdose;
        private Double major_scale;
        private Double minor_scale;
        private Double distort_ang;
        private String defectFile;

    }


    public class CmdProcess
    {
        private static final ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();

        static {
            taskExecutor.setCorePoolSize(50);
            taskExecutor.setMaxPoolSize(500);
            taskExecutor.initialize();
            taskExecutor.setThreadNamePrefix("CmdConsole-");
        }

        private final ProcessBuilder processBuilder;
        @Getter
        private final SoftwareConfig softwareConfig;
        private final List<String> cmds = new ArrayList<>();
        private final List<String> slurm = new ArrayList<>();
        private StreamGobbler outputStreamGobbler;
        private boolean locked;
        private StreamGobbler errorStreamGobbler;
        @Getter
        private Context context;
        private ProcessBuilder.Redirect redirect;
        private File logFile;
        @Setter
        @Getter
        private String jobId;
        private String result;


        public CmdProcess(SoftwareConfig softwareConfig) {
            this.softwareConfig = softwareConfig;
            this.processBuilder = new ProcessBuilder();
            this.context = MovieContext.get();
        }

        public boolean slurm() {
            return softwareConfig.slurm() && slurmEnabled;
        }

        public Process start() throws IOException {

            processBuilder.command(this.command());
            if( this.redirect != null ) {
                this.processBuilder.redirectOutput(redirect);
                this.processBuilder.redirectError(redirect);
            }
//            if(this.redirect == null){
//
//                this.logFile  = File.createTempFile("cmd-", ".out", FileUtils.getFile("/home/cryoems/logs"));
//
//                this.redirect = ProcessBuilder.Redirect.to(logFile);
//            }
//            processBuilder.redirectOutput(redirect);
//            processBuilder.redirectError(redirect);
            //            processBuilder.inheritIO();

            try {
                return this.processBuilder.start();
            } finally {
//                if( locked ) {
//                    try {
//                        Thread.sleep(500);
//                    } catch( InterruptedException e ) {
//                        log.error(e.getMessage(), e);
//                        throw new FatalException(e);
//                    }
//                    release();
//                }
            }
        }

        public synchronized boolean tryLock() {

            if( locked ) {
                return false;
            }

            if( this.softwareConfig.concurrentLock != null ) {
                boolean locked;
                try {
                    locked = this.softwareConfig.concurrentLock.tryAcquire(this.softwareConfig.getTimeout(), TimeUnit.SECONDS);

                    this.locked = locked;
                } catch( InterruptedException e ) {
                    throw new FatalException(e);
                }
                if( !locked ) {
                    log.warn("Failed to acquire pool lock for command: {}", this);
                    throw new RetryException("Unable to acquire pool lock");
                }
                log.info("Acquired pool lock for command: {}, current permits {}", this, this.softwareConfig.concurrentLock.availablePermits());
            }
            return this.locked;
        }

        public void startAndWait() {
            if( this == EmptyCmdProcess ) {
                return;
            }
            if( this.slurm() ) {
                slurmService.start(this).waitFor();
            } else {
                _startAndWait();
            }

        }

        void _startAndWait() {
            try {
                // 校验 timeout 参数
                if( this.softwareConfig.getTimeout() <= 0 ) {
                    log.error("Invalid timeout value: {}", this.softwareConfig.getTimeout());
                    throw new IllegalArgumentException("Timeout must be a positive integer");
                }
                // 尝试获取并发锁
                boolean locked = tryLock();

                try {
                    Process process = null;
                    try {
                        log.info("Start cmd: {} ", this);
                        process = this.start(); // 启动进程
                        outputStreamGobbler = new StreamGobbler(process.getInputStream());
                        errorStreamGobbler = new StreamGobbler(process.getErrorStream());

                        // 提交流处理任务
                        Future<?> output = taskExecutor.submit(outputStreamGobbler);
                        Future<?> error = taskExecutor.submit(errorStreamGobbler);

                        // 等待进程完成
                        boolean success = process.waitFor(this.softwareConfig.getTimeout(), TimeUnit.SECONDS);

                        output.get(this.softwareConfig.getTimeout(), TimeUnit.SECONDS);
                        error.get(this.softwareConfig.getTimeout(), TimeUnit.SECONDS);

                        if( !success || process.exitValue() != 0 ) {
                            log.error("Command failed with result: {}", result());
                            log.error("End cmd: {}, exit code: {}", this, process.exitValue());
                            throw new CmdException(result());
                        }

                        log.info(result());
                        log.info("End cmd: {}, exit code: {}", this, process.exitValue());
                    } finally {
                        // 确保进程被销毁
                        destroyProcessIfAlive(process);

                        // 确保 StreamGobbler 线程停止
                        stopStreamGobblers(this.outputStreamGobbler, this.errorStreamGobbler);
                    }
                } finally {
                    // 释放并发锁
                    if( locked ) {
                        this.release();
                    }
                }
            } catch( Exception e ) {
                log.error("An error occurred while executing the command: {}, {}", this, e.getMessage());
                throw ExceptionUtil.wrapRuntime(e);
            }
        }

        public synchronized void release() {
            if( this.locked ) {
                this.softwareConfig.concurrentLock.release();
                log.info("Released pool lock for command: {}, current permits {}", this, this.softwareConfig.concurrentLock.availablePermits());
                this.locked = false;
            }
        }

        // 辅助方法：销毁活跃进程
        private void destroyProcessIfAlive(Process process) {
            if( process != null && process.isAlive() ) {
                process.destroy();
                log.debug("Destroyed process for command: {}", this);
            }
        }

        // 辅助方法：停止 StreamGobbler 线程
        private void stopStreamGobblers(StreamGobbler... gobblers) {
            if( gobblers == null ) {
                return;
            }
            for( StreamGobbler gobbler : gobblers ) {
                if( gobbler != null ) {
//                    try {
//                        gobbler.join(1000L);
//                    } catch( InterruptedException e ) {
//                        log.error(e.getMessage());
//                    }
                    //                    gobbler.interrupt(); // 假设 StreamGobbler 提供 stop 方法
                    log.debug("Stopped StreamGobbler for command: {}", this);
                }
            }
        }


        public String output() {
            //        if(this.outputStreamGobbler.isAlive()) {
            //            try {
            //                this.outputStreamGobbler.join();
            //            } catch( InterruptedException e ) {
            //                log.error(e.getMessage(),e);
            //            }
            //        }
            if( this.outputStreamGobbler == null ) {
                throw new RuntimeException("cmd not start");
            }
            return this.outputStreamGobbler.writer.toString();
        }

        private String error() {
            if( this.errorStreamGobbler == null ) {
                throw new RuntimeException("cmd not start");
            }
            return this.errorStreamGobbler.writer.toString();
        }

        public String result() {

//            if(StringUtils.isEmpty(this.result) && this.logFile != null && this.logFile.exists()){
//                try {
//                    this.result =  FileUtils.readFileToString(this.logFile,  StandardCharsets.UTF_8);
//                    this.logFile.delete();
//                } catch( IOException e ) {
//                    throw new RuntimeException(e);
//                }
//            }
//            return Optional.ofNullable(this.result).orElse("");
            return this.output() + "" + this.error();
        }

        public void command(String... cmds) {
            this.cmds.addAll(List.of(cmds));
        }

        public void slurm(String... cmds) {
            this.slurm.addAll(List.of(cmds));
        }

        public List<String> command() {
            ArrayList<String> cmds = new ArrayList<>();
            if( this.slurm() ) {
                String slurmArgs = this.softwareConfig.getSlurmArgs();
                String[] split = slurmArgs.split(" +");
                cmds.addAll(Arrays.stream(split).toList());
                cmds.addAll(this.slurm);
            }
            if( this.softwareConfig.slurm() && !slurmEnabled ) {
                softwareConfig.path = "/bin/bash";
            }
            cmds.addAll(softwareConfig.cmds());
            cmds.addAll(this.cmds);
            return cmds;
        }

        public String toString() {
            return toCmdStr();
        }

        public String toCmdStr() {
            List<String> command = command();
            return StringUtils.join(command, " ");
        }

        public void command(List<String> cmds) {
            this.cmds.addAll(cmds);
        }

        public void redirectOutput(ProcessBuilder.Redirect redirect) {
            this.redirect = redirect;
            //        this.processBuilder.redirectOutput(redirect);
        }


        static class StreamGobbler implements Runnable
        {
            private final InputStream inputStream;
            private final StringBuilder writer;

            StreamGobbler(InputStream inputStream) {
                this.inputStream = inputStream;
                this.writer = new StringBuilder();
            }

            @Override
            public void run() {
                try( BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream)) ) {
                    String line;
                    while( (line = reader.readLine()) != null ) {
                        this.writer.append(line).append("\n");
                    }
                } catch( IOException e ) {
                    log.error(e.getMessage(), e);
                    try {
                        inputStream.close();
                    } catch( IOException ex ) {
                        log.error(e.getMessage(), e);
                    }
                }
            }
        }
    }
}
