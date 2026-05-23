/**
 * Movie 流水线相关组件。
 *
 * <p>与 {@link com.kiwi.cryoems.bpm.mdoc} 包按数据集类型拆分，互不直接依赖；
 * 共享的模型放在 {@link com.kiwi.cryoems.bpm.model} 下。当前 movie 处理逻辑分布在
 * {@code activity} / {@code movieresult} 等既有包内，本包用于沉淀后续 movie-only 的
 * 解析、装配类组件。</p>
 */
package com.kiwi.cryoems.bpm.movie;
