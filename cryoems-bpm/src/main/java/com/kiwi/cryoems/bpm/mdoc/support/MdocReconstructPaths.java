package com.kiwi.cryoems.bpm.mdoc.support;

/**
 * mdoc 全流程（stack + 重建）产物路径，覆盖 {@code script/mdoc_reconstruct.sh} 13 段命令的全部输出，
 * 字面顺序与 cyroems 端 {@code MdocStackHandler} / {@code CoarseAlign} / {@code PatchTracking} /
 * {@code SeriesAlign} / {@code AlignRecon} 各 Handler 中
 * {@code filePathService.getMdocWorkDir(context) + "/" + name + ".<ext>"} 一一对应。
 *
 * <p>所有字段均为绝对路径字符串；脚本未产出某步骤时对应字段并非 {@code null}（仅约定字面），
 * 由调用方按实际产物存在性自行裁剪。</p>
 *
 * <p>派生约定（{@code workDir = ${workDirRoot}/mdoc/${name}}，{@code thumbDir} 由调用方传入）：</p>
 *
 * <table>
 *   <tr><th>字段</th><th>路径字面</th><th>对应脚本步骤</th></tr>
 *   <tr><td>{@code outputMrc}</td><td>{@code ${workDir}/${name}_raw_bin.mrc}</td><td>stack / stack_and_filter</td></tr>
 *   <tr><td>{@code rawTitleFile}</td><td>{@code ${workDir}/${name}.rawtlt}</td><td>stack（手动重建分支）</td></tr>
 *   <tr><td>{@code outputTitleFile}</td><td>{@code ${workDir}/${name}_raw_bin.rawtilt}</td><td>stack_and_filter</td></tr>
 *   <tr><td>{@code excludeFile}</td><td>{@code ${workDir}/${name}_raw_bin_files.txt}</td><td>stack_and_filter</td></tr>
 *   <tr><td>{@code prexf}</td><td>{@code ${workDir}/${name}.prexf}</td><td>tiltxcorr (1)</td></tr>
 *   <tr><td>{@code prexg}</td><td>{@code ${workDir}/${name}.prexg}</td><td>xftoxg (2)</td></tr>
 *   <tr><td>{@code preali}</td><td>{@code ${workDir}/${name}_preali.mrc}</td><td>newstack (3)</td></tr>
 *   <tr><td>{@code ptFid}</td><td>{@code ${workDir}/${name}_pt.fid}</td><td>patch tiltxcorr (4)</td></tr>
 *   <tr><td>{@code fid}</td><td>{@code ${workDir}/${name}.fid}</td><td>imodchopconts (5)</td></tr>
 *   <tr><td>{@code threeDmod}</td><td>{@code ${workDir}/${name}.3dmod}</td><td>tilt_series_align.py (6)</td></tr>
 *   <tr><td>{@code resid}</td><td>{@code ${workDir}/${name}.resid}</td><td>tilt_series_align.py (6)</td></tr>
 *   <tr><td>{@code fidXyz}</td><td>{@code ${workDir}/${name}fid.xyz}</td><td>tilt_series_align.py (6) — 注意 name 与 "fid" 直连无下划线</td></tr>
 *   <tr><td>{@code tlt}</td><td>{@code ${workDir}/${name}.tlt}</td><td>tilt_series_align.py (6)</td></tr>
 *   <tr><td>{@code xtilt}</td><td>{@code ${workDir}/${name}.xtilt}</td><td>tilt_series_align.py (6)</td></tr>
 *   <tr><td>{@code tltxf}</td><td>{@code ${workDir}/${name}.tltxf}</td><td>tilt_series_align.py (6)</td></tr>
 *   <tr><td>{@code nogapsFid}</td><td>{@code ${workDir}/${name}_nogaps.fid}</td><td>tilt_series_align.py (6)</td></tr>
 *   <tr><td>{@code fidXf}</td><td>{@code ${workDir}/${name}_fid.xf}</td><td>xfproduct (7)</td></tr>
 *   <tr><td>{@code resmod}</td><td>{@code ${workDir}/${name}.resmod}</td><td>patch2imod (8)</td></tr>
 *   <tr><td>{@code ali}</td><td>{@code ${workDir}/${name}_ali.mrc}</td><td>newstack (9)</td></tr>
 *   <tr><td>{@code aliBin1}</td><td>{@code ${workDir}/${name}_ali_bin1.mrc}</td><td>newstack (10)</td></tr>
 *   <tr><td>{@code fullRec}</td><td>{@code ${workDir}/${name}_full_rec_bin4.mrc}</td><td>tilt (11) / binvol (12)</td></tr>
 *   <tr><td>{@code thumb}</td><td>{@code ${thumbDir}/${name}_full_rec_bin8_unit8.mrc}</td><td>align_recon_v2.py (13)</td></tr>
 *   <tr><td>{@code thumbXY/YZ/XZ}</td><td>{@code ${thumbDir}/${name}_full_rec_bin8_unit8_(xy|yz|xz).png}</td><td>align_recon_v2.py 三视图</td></tr>
 * </table>
 */
public record MdocReconstructPaths(
        String name,
        String workDir,
        String thumbDir,
        // ---- stack 阶段（与 MdocPaths 同名同义） ----
        String rawTitleFile,
        String outputMrc,
        String outputTitleFile,
        String excludeFile,
        // ---- coarse-align 阶段 ----
        String prexf,
        String prexg,
        String preali,
        // ---- patch-tracking 阶段 ----
        String ptFid,
        String fid,
        // ---- series-align 阶段（python 隐式产物） ----
        String threeDmod,
        String resid,
        String fidXyz,
        String tlt,
        String xtilt,
        String tltxf,
        String nogapsFid,
        // ---- align-recon 阶段 ----
        String fidXf,
        String resmod,
        String ali,
        String aliBin1,
        String fullRec,
        // ---- thumbnails ----
        String thumb,
        String thumbXY,
        String thumbYZ,
        String thumbXZ) {

    /** 兼容 stack 阶段已有的 {@link MdocPaths} 视图（仅 stack 子集），便于现有代码继续按窄契约消费。 */
    public MdocPaths toStackPaths() {
        return new MdocPaths(name, workDir, rawTitleFile, outputMrc, outputTitleFile, excludeFile);
    }
}
