package com.kiwi.cryoems.bpm.mdoc.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 与 cyroems {@code com.cryo.task.tilt.stack.StackResult} 字段命名严格一致；
 * 写入 {@link com.kiwi.cryoems.bpm.mdoc.model.MdocResult#getStackResult()}。
 *
 * <p>注意 {@code titlFile} 为 cyroems 历史拼写（缺一个 e），保持兼容不修。</p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StackResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<String> files;
    private String outputFile;
    private String titlFile;
    private String excludeFile;
    private List<String> rawFiles;
}
