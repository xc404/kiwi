package com.kiwi.bpmn.component.slurm;

/**
 * sbatch 脚本内嵌字符串消毒：防止流程变量中的换行注入额外 shell 行或 #SBATCH 行。
 */
final class SlurmScriptSanitizeUtils {

    private SlurmScriptSanitizeUtils() {}

    /**
     * 将 {@code \r}/{@code \n} 替换为空白，避免在写入 .sbatch 时产生额外逻辑行（策略：剥离而非拒绝）。
     */
    static String stripEmbeddedNewlines(String value) {
        if (value == null) {
            return null;
        }
        return value.replace('\r', ' ').replace('\n', ' ');
    }
}
