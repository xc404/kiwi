package com.kiwi.project.bpm.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 流程级输入/输出汇总（不展开到每个服务任务）。
 */
@Data
public class BpmProcessIoGapAnalysis
{
    /**
     * 须由流程启动时提供（或启动表单等）的输入项描述，按首次出现的变量名去重并保持顺序。
     * 含义：某组件输入值中引用了 {@code ${name}}，且不存在任意上游组件在元数据 output 中声明同名 key；
     * 若能对应到该任务的输入参数元数据则使用该 {@link BpmComponentParameter}，否则仅填充 key/name。
     */
    private List<BpmComponentParameter> processInputs = new ArrayList<>();

    /**
     * 全流程所有已解析组件的 output 元数据之并集，按控制流拓扑顺序合并；
     * 同名 {@link BpmComponentParameter#getKey()} 以后出现的组件为准（覆盖先前的定义）。
     */
    private List<BpmComponentParameter> processOutputs = new ArrayList<>();
}
