package com.kiwi.cryoems.bpm.mdoc.support;

import com.kiwi.cryoems.bpm.mdoc.dao.MDocInstanceRepository;
import com.kiwi.cryoems.bpm.mdoc.dao.MDocRepository;
import com.kiwi.cryoems.bpm.mdoc.model.MDoc;
import com.kiwi.cryoems.bpm.mdoc.model.MDocInstance;
import com.kiwi.cryoems.bpm.mdoc.model.MdocMeta;
import com.kiwi.cryoems.bpm.mdoc.model.MdocTiltMeta;
import com.kiwi.cryoems.bpm.movie.dao.MovieResultRepository;
import com.kiwi.cryoems.bpm.movie.model.MovieResult;
import com.kiwi.cryoems.bpm.movie.model.motion.MotionResult;
import com.kiwi.cryoems.bpm.movie.model.motion.MrcFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * mdoc stack 阶段两个 Activity 的共用装配服务，等价于 cyroems
 * {@code com.cryo.task.tilt.stack.MdocStackHandler#handle} 中的：
 * <ol>
 *     <li>按 {@code TiltAngle} 升序的 tilts 列表，并按 {@code MDoc.movie_data_ids} 过滤；</li>
 *     <li>用 {@code motionResultId} 批量回查 {@link MovieResult}，取 {@code motion.dw.path} 拼出文件列表；</li>
 *     <li>派生工作目录 {@code ${workDirRoot}/mdoc/${instance.name}}、输出 {@code _raw_bin.mrc} 路径；</li>
 *     <li>调用 {@link #createTitleFile} 写出 {@code ${name}.rawtlt}（以中位 tilt 角归零）。</li>
 * </ol>
 *
 * <p>纯计算 + 文件 IO，不调用 SLURM / 子进程；由 {@code CryoemsMdocStackActivity} 和
 * {@code CryoemsMdocStackAndFilterActivity} 共享。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MdocStackInputBuilder {

    /** 与 cyroems {@code NumberUtils#df} 完全一致，保证 .rawtlt 文本逐字节可比。 */
    private static final ThreadLocal<DecimalFormat> ANGLE_FORMAT =
            ThreadLocal.withInitial(() -> new DecimalFormat("#.#####"));

    private final MDocRepository mDocRepository;
    private final MDocInstanceRepository mDocInstanceRepository;
    private final MovieResultRepository movieResultRepository;
    private final MdocPathResolver pathResolver;

    /**
     * 装配 stack 阶段的输入产物。
     *
     * @param mdocDataId   {@link MDoc#getId()}（来自 Kiwi 瘦契约 {@code mdoc.dataId}）
     * @param instanceId   {@link MDocInstance#getId()}（来自 {@code mdoc.id}）
     * @param workDirRoot  cryoEMS 端 {@code task} 工作根目录（即流程变量 {@code work_dir}）
     * @return {@link MdocStackInputs}；{@link MdocStackInputs#rawTitleFile()} 已写盘
     */
    public MdocStackInputs build(String instanceId, String workDirRoot) {
        if (!StringUtils.hasText(instanceId)) {
            throw new IllegalArgumentException("mdoc.id 不能为空");
        }
        if (!StringUtils.hasText(workDirRoot)) {
            throw new IllegalArgumentException("work_dir 不能为空");
        }


        MDocInstance instance = mDocInstanceRepository
                .findById(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("MDocInstance 不存在: " + instanceId));
        if (!StringUtils.hasText(instance.getName())) {
            throw new IllegalArgumentException("MDocInstance.name 不能为空: " + instanceId);
        }
        MDoc mDoc = mDocRepository
                .findById(instance.getData_id())
                .orElseThrow(() -> new IllegalArgumentException("MDoc 不存在: " + instance.getData_id()));
        MdocMeta meta = mDoc.getMeta();
        if (meta == null || meta.getTilts() == null || meta.getTilts().isEmpty()) {
            throw new IllegalArgumentException("MDoc.meta.tilts 为空: " + mDoc.getName());
        }

        List<MdocTiltMeta> sortedTilts = sortAndFilter(meta.getTilts(), mDoc.getMovie_data_ids());
        if (sortedTilts.isEmpty()) {
            throw new IllegalArgumentException(
                    "按 movie_data_ids 过滤后 tilts 为空: " + mDoc.getMovie_data_ids());
        }

        List<String> files = resolveMotionFiles(sortedTilts);

        MdocPaths paths = pathResolver.resolve(workDirRoot, instance.getName());
        File workDir = new File(paths.workDir());
        try {
            Files.createDirectories(workDir.toPath());
        } catch (IOException e) {
            throw new IllegalStateException("创建 mdoc 工作目录失败: " + workDir, e);
        }
        createTitleFile(paths, sortedTilts);

        return new MdocStackInputs(mDoc, instance, sortedTilts, files, paths);
    }

    /**
     * 按 {@code TiltAngle} 升序，再按 {@code movie_data_ids} 过滤（若非空）。
     * 等价 {@code MdocStackHandler.handle} 第 59-64 行。
     */
    private static List<MdocTiltMeta> sortAndFilter(List<MdocTiltMeta> input, List<String> movieDataIds) {
        List<MdocTiltMeta> sorted = new ArrayList<>(input);
        sorted.sort(Comparator.comparing(MdocTiltMeta::getTiltAngle));
        if (movieDataIds == null || movieDataIds.isEmpty()) {
            return sorted;
        }
        return sorted.stream().filter(t -> movieDataIds.contains(t.getDataId())).toList();
    }

    /**
     * 用 {@code motionResultId} 批量查 {@link MovieResult}，按原 tilts 顺序取 {@code motion.dw.path}。
     * 等价 {@code MdocStackHandler.handle} 第 67-72 行。
     */
    private List<String> resolveMotionFiles(List<MdocTiltMeta> sortedTilts) {
        List<String> motionResultIds = sortedTilts.stream()
                .map(MdocTiltMeta::getMotionResultId)
                .filter(Objects::nonNull)
                .toList();
        if (motionResultIds.isEmpty()) {
            throw new IllegalStateException("tilts 中缺少 motionResultId");
        }
        Iterable<MovieResult> loaded = movieResultRepository.findAllById(motionResultIds);
        Map<String, MovieResult> byId = new LinkedHashMap<>();
        loaded.forEach(m -> byId.put(m.getId(), m));

        return sortedTilts.stream()
                .map(t -> {
                    MovieResult m = byId.get(t.getMotionResultId());
                    if (m == null) {
                        throw new IllegalStateException(
                                "MovieResult 不存在: motionResultId=" + t.getMotionResultId());
                    }
                    MotionResult motion = m.getMotion();
                    MrcFile dw = motion == null ? null : motion.getDw();
                    String path = dw == null ? null : dw.getPath();
                    if (!StringUtils.hasText(path)) {
                        throw new IllegalStateException(
                                "MovieResult.motion.dw.path 为空: id=" + m.getId());
                    }
                    return path;
                })
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * 以中位 tilt 角为零点写出 {@code ${name}.rawtlt}（路径由 {@link MdocPathResolver} 给出），
     * 等价 {@code MdocStackHandler#createTitleFile}。
     * 与 cyroems 行为对齐，但**不**再调用 {@code exportSupport.toSelf}（BPM 端 ACL 子系统迁移时再补）。
     */
    File createTitleFile(MdocPaths paths, List<MdocTiltMeta> sortedTilts) {
        List<Double> angles = sortedTilts.stream().map(MdocTiltMeta::getTiltAngle).toList();
        double middle;
        if (angles.size() % 2 == 0) {
            middle = (angles.get(angles.size() / 2) + angles.get(angles.size() / 2 - 1)) / 2.0;
        } else {
            middle = angles.get(angles.size() / 2);
        }
        DecimalFormat df = ANGLE_FORMAT.get();
        List<String> lines = angles.stream().map(a -> df.format(a - middle)).toList();
        File file = new File(paths.rawTitleFile());
        try {
            Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("写入 rawtlt 失败: " + file, e);
        }
        // TODO: cryoems 端 exportSupport.toSelf(file) 在 ACL 子系统迁到 BPM 后再补。
        return file;
    }

    /**
     * 反查 {@code MovieResult.movie_data_id}：当 stack_and_filter 输出的 files 数量与输入不一致时，
     * 据此回写 {@link MDoc#getMovie_data_ids()}。
     * 等价 {@code MdocStackHandler.handle} 第 100-107 行。
     */
    public List<String> mapPathsToMovieDataIds(Iterable<MovieResult> source, List<String> keptPaths) {
        return java.util.stream.StreamSupport.stream(source.spliterator(), false)
                .filter(m -> {
                    MotionResult motion = m.getMotion();
                    MrcFile dw = motion == null ? null : motion.getDw();
                    String path = dw == null ? null : dw.getPath();
                    return path != null && keptPaths.contains(path);
                })
                .map(MovieResult::getMovie_data_id)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 暴露给 Activity B 在过滤后回查 MovieResult 的便利方法，避免 Activity 重复 wire 仓库。
     */
    public Iterable<MovieResult> findMovieResultsByMotionIds(List<String> motionResultIds) {
        return movieResultRepository.findAllById(motionResultIds);
    }

    /**
     * mdoc stack 装配产物。{@code paths.rawTitleFile} 在 {@link #build} 内已写盘；
     * 其它路径（{@code outputMrc} / {@code outputTitleFile} / {@code excludeFile} /
     * {@code stackScript} / {@code stackLog} / {@code stackAndFilterLog}）由
     * {@link MdocPathResolver} 一次性解析，对外只暴露 {@link MdocPaths} 单一字面来源。
     *
     * <p>为兼容历史调用方与单测断言（例如
     * {@code inputs.workDir().getAbsolutePath()}），同时提供 {@link #workDir()} /
     * {@link #rawTitleFile()} / {@link #outputMrc()} 三个 {@link File} 便利访问器；
     * 新代码请直接使用 {@link #paths()}。</p>
     *
     * @param mDoc        回查得到的 {@link MDoc} 实体（保留供 Activity 内回写 {@code movie_data_ids}）
     * @param instance    回查得到的 {@link MDocInstance}
     * @param sortedTilts 已排序 / 过滤后的 tilts
     * @param files       每个 tilt 对应的 {@code motion.dw.path}（顺序与 {@code sortedTilts} 对齐）
     * @param paths       mdoc stack 阶段的全部产物路径
     */
    public record MdocStackInputs(
            MDoc mDoc,
            MDocInstance instance,
            List<MdocTiltMeta> sortedTilts,
            List<String> files,
            MdocPaths paths) {

        /** 便利访问器：mdoc 工作目录（{@code paths.workDir} 的 {@link File} 包装）。 */
        public File workDir() {
            return new File(paths.workDir());
        }

        /** 便利访问器：手动重建分支输入的 {@code ${name}.rawtlt}。 */
        public File rawTitleFile() {
            return new File(paths.rawTitleFile());
        }

        /** 便利访问器：stack 阶段输出的 {@code ${name}_raw_bin.mrc}。 */
        public File outputMrc() {
            return new File(paths.outputMrc());
        }
    }
}
