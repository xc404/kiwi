package com.kiwi.cryoems.bpm.mdoc.support;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Path;

/**
 * 从 {@code workDirRoot} + {@code instance.name} 解析出 mdoc stack 阶段的所有产物路径，
 * 与 {@link com.kiwi.cryoems.bpm.movie.result.motion.MotionPathResolver} 同形。
 *
 * <p>纯路径字面计算，不创建目录、不读写文件、不访问数据库；由 Activity / 装配器在拿到
 * {@link com.kiwi.cryoems.bpm.mdoc.model.MDocInstance#getName()} 与流程变量 {@code work_dir}
 * 之后调用，避免在多个 Activity 中重复拼路径字符串、降低字面漂移风险。</p>
 *
 * <p>字面顺序与 cyroems {@code MdocStackHandler#handle} 完全一致：
 * <pre>
 *   ${workDirRoot}/mdoc/${name}/
 *       ${name}.rawtlt
 *       ${name}_raw_bin.mrc
 *       ${name}_raw_bin.rawtilt
 *       ${name}_raw_bin_files.txt
 *       ${name}_mdoc_stack.sh
 *       ${name}_mdoc_stack.log
 *       ${name}_stack_and_filter.log
 * </pre></p>
 */
@Component
public class MdocPathResolver {

    /**
     * 解析 mdoc 工作目录与 stack 阶段全部产物路径。
     *
     * @param workDirRoot 流程变量 {@code work_dir}，对齐 cyroems {@code FilePathService#getWorkDir}
     * @param name        {@code MDocInstance.name}，作为所有文件名前缀
     * @return 完整 {@link MdocPaths}（所有字段为绝对路径字符串，未保证文件存在）
     */
    public MdocPaths resolve(String workDirRoot, String name) {
        requireText(workDirRoot, "workDirRoot");
        requireText(name, "name");
        String trimmedName = name.trim();
        Path workDir = Path.of(workDirRoot.trim(), "mdoc", trimmedName).toAbsolutePath().normalize();

        String rawTitleFile = workDir.resolve(trimmedName + ".rawtlt").toString();
        String outputMrc = workDir.resolve(trimmedName + "_raw_bin.mrc").toString();
        String outputTitleFile = workDir.resolve(trimmedName + "_raw_bin.rawtilt").toString();
        String excludeFile = workDir.resolve(trimmedName + "_raw_bin_files.txt").toString();

        return new MdocPaths(
                trimmedName,
                workDir.toString(),
                rawTitleFile,
                outputMrc,
                outputTitleFile,
                excludeFile);
    }

    /**
     * 解析 mdoc 全流程（stack + coarse-align + patch-tracking + series-align + align-recon）产物路径，
     * 一一对应 {@code script/mdoc_reconstruct.sh} 13 段命令字面与 cyroems 各 Handler 中
     * {@code filePathService.getMdocWorkDir(context) + "/" + name + ".<ext>"}。
     *
     * @param workDirRoot   流程变量 {@code work_dir}
     * @param name          {@code MDocInstance.name}
     * @param thumbDirRoot  缩略图根目录（脚本 {@code --thumb-dir}），通常为 {@code ${work_dir}/thumbnails}
     * @return 完整 {@link MdocReconstructPaths}
     */
    public MdocReconstructPaths resolveAll(String workDirRoot, String name, String thumbDirRoot) {
        requireText(workDirRoot, "workDirRoot");
        requireText(name, "name");
        requireText(thumbDirRoot, "thumbDirRoot");
        String trimmedName = name.trim();
        Path workDir = Path.of(workDirRoot.trim(), "mdoc", trimmedName).toAbsolutePath().normalize();
        Path thumbDir = Path.of(thumbDirRoot.trim()).toAbsolutePath().normalize();

        // stack
        String rawTitleFile = workDir.resolve(trimmedName + ".rawtlt").toString();
        String outputMrc = workDir.resolve(trimmedName + "_raw_bin.mrc").toString();
        String outputTitleFile = workDir.resolve(trimmedName + "_raw_bin.rawtilt").toString();
        String excludeFile = workDir.resolve(trimmedName + "_raw_bin_files.txt").toString();

        // coarse-align
        String prexf = workDir.resolve(trimmedName + ".prexf").toString();
        String prexg = workDir.resolve(trimmedName + ".prexg").toString();
        String preali = workDir.resolve(trimmedName + "_preali.mrc").toString();

        // patch-tracking
        String ptFid = workDir.resolve(trimmedName + "_pt.fid").toString();
        String fid = workDir.resolve(trimmedName + ".fid").toString();

        // series-align（python 隐式产物）
        String threeDmod = workDir.resolve(trimmedName + ".3dmod").toString();
        String resid = workDir.resolve(trimmedName + ".resid").toString();
        // 注意：cyroems 命名 "${name}fid.xyz"，name 与 "fid" 之间无下划线
        String fidXyz = workDir.resolve(trimmedName + "fid.xyz").toString();
        String tlt = workDir.resolve(trimmedName + ".tlt").toString();
        String xtilt = workDir.resolve(trimmedName + ".xtilt").toString();
        String tltxf = workDir.resolve(trimmedName + ".tltxf").toString();
        String nogapsFid = workDir.resolve(trimmedName + "_nogaps.fid").toString();

        // align-recon
        String fidXf = workDir.resolve(trimmedName + "_fid.xf").toString();
        String resmod = workDir.resolve(trimmedName + ".resmod").toString();
        String ali = workDir.resolve(trimmedName + "_ali.mrc").toString();
        String aliBin1 = workDir.resolve(trimmedName + "_ali_bin1.mrc").toString();
        // tilt 与 binvol 同指（binvol 原位覆盖 tilt 输出）
        String fullRec = workDir.resolve(trimmedName + "_full_rec_bin4.mrc").toString();

        // thumbnails（align_recon_v2.py 一并产出 _xy/_yz/_xz 三视图）
        String thumbBase = trimmedName + "_full_rec_bin8_unit8";
        String thumb = thumbDir.resolve(thumbBase + ".mrc").toString();
        String thumbXY = thumbDir.resolve(thumbBase + "_xy.png").toString();
        String thumbYZ = thumbDir.resolve(thumbBase + "_yz.png").toString();
        String thumbXZ = thumbDir.resolve(thumbBase + "_xz.png").toString();

        return new MdocReconstructPaths(
                trimmedName,
                workDir.toString(),
                thumbDir.toString(),
                rawTitleFile,
                outputMrc,
                outputTitleFile,
                excludeFile,
                prexf,
                prexg,
                preali,
                ptFid,
                fid,
                threeDmod,
                resid,
                fidXyz,
                tlt,
                xtilt,
                tltxf,
                nogapsFid,
                fidXf,
                resmod,
                ali,
                aliBin1,
                fullRec,
                thumb,
                thumbXY,
                thumbYZ,
                thumbXZ);
    }

    private static void requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("流程变量 " + name + " 不能为空");
        }
    }
}
