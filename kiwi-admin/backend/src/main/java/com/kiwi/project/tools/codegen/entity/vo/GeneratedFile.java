package com.kiwi.project.tools.codegen.entity.vo;

/**
 * 单次 Velocity 渲染产物：相对路径 + 文本内容。
 */
public record GeneratedFile(String path, String content) {
}
