/**
 * Mdoc 流水线相关组件，按职责拆分为以下子包（结构与 {@link com.kiwi.cryoems.bpm.movie} 同形）：
 *
 * <ul>
 *     <li>{@link com.kiwi.cryoems.bpm.mdoc.activity} — Camunda {@code JavaDelegate} 活动；</li>
 *     <li>{@link com.kiwi.cryoems.bpm.mdoc.model} — mdoc 元数据领域模型（{@code MdocMeta} 等）；</li>
 *     <li>{@link com.kiwi.cryoems.bpm.mdoc.support} — 解析/工具类组件（如 {@code MdocFileParser}）。</li>
 * </ul>
 *
 * <p>与 {@link com.kiwi.cryoems.bpm.movie} 包按数据集类型解耦，互不直接依赖；
 * 跨数据集复用的基础设施（{@code CryoDataEntity} / {@code MrcMetadata} /
 * {@code WorkflowVariableReader} 等）继续放在 {@link com.kiwi.cryoems.bpm.model} 与
 * {@link com.kiwi.cryoems.bpm.support} 共享层。</p>
 */
package com.kiwi.cryoems.bpm.mdoc;
