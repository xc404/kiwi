package com.kiwi.project.bpm.model;

import lombok.Data;

import java.util.Map;

@Data
public class BpmComponentParameter
{
    private String key;

    private String name;

    private String description;

    private String defaultValue;

    private boolean array;

    private boolean required;

    private boolean readonly;

    private boolean hidden;

    private String htmlType;

    private String type;

    private String example;

    private String dictKey;


    private String group;

    private boolean important;

    /**
     * 扩展元数据；当前主要用于 CLI 组件「重新生成 command」时携带每个参数的标志位信息。
     * <p>约定的子键：</p>
     * <ul>
     *   <li>{@code primaryLongFlag}：String，命令行中拼装到 command 的 flag 前缀，如 {@code --foo}、{@code -FlipGain}、{@code --out=}</li>
     *   <li>{@code expectsValue}：Boolean，true 表示带值参数（{@code flag value}）；false 表示纯开关</li>
     *   <li>{@code shortFlag}：String（可选）；{@code longId}：String（可选）；{@code fromCliHelp}：Boolean（可选）</li>
     * </ul>
     */
    private Map<String, Object> additionalOption;
}
