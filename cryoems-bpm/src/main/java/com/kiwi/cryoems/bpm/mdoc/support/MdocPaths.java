package com.kiwi.cryoems.bpm.mdoc.support;

/**
 * mdoc stack 步骤产物路径（对齐 cyroems {@code MdocStackHandler#handle} 写出的全部路径，
 * 与 {@link com.kiwi.cryoems.bpm.movie.result.motion.MotionPaths} 同形）。
 *
 * <p>所有字段均为绝对路径字符串；文件未必已经存在（取决于流程阶段），仅约定路径字面：</p>
 *
 * <ul>
 *     <li>{@code workDir} —— {@code ${workDirRoot}/mdoc/${name}}，stack 阶段工作目录；</li>
 *     <li>{@code rawTitleFile} —— {@code ${name}.rawtlt}，手动重建分支输入的 tilt 角文件；</li>
 *     <li>{@code outputMrc} —— {@code ${name}_raw_bin.mrc}，{@code mdoc_stack} / {@code stack_and_filter} 共同产物；</li>
 *     <li>{@code outputTitleFile} —— {@code ${name}_raw_bin.rawtilt}，{@code stack_and_filter} 自动分支产物；</li>
 *     <li>{@code excludeFile} —— {@code ${name}_raw_bin_files.txt}，{@code stack_and_filter} 写入的保留文件清单；</li>
 * </ul>
 */
public record MdocPaths(
        String name,
        String workDir,
        String rawTitleFile,
        String outputMrc,
        String outputTitleFile,
        String excludeFile) {}
