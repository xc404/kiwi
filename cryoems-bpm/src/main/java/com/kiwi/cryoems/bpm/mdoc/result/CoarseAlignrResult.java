package com.kiwi.cryoems.bpm.mdoc.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 与 cyroems {@code com.cryo.task.tilt.align.CoarseAlignrResult} 字段命名严格一致。
 *
 * <ul>
 *     <li>{@code tiltxcorrOutput} —— {@code ${name}.prexf}</li>
 *     <li>{@code xftoxgOutput} —— {@code ${name}.prexg}</li>
 *     <li>{@code newstackOutput} —— {@code ${name}_preali.mrc}</li>
 * </ul>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CoarseAlignrResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String tiltxcorrOutput;
    private String xftoxgOutput;
    private String newstackOutput;
}
