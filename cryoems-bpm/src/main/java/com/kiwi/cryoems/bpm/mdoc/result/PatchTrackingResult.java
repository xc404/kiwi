package com.kiwi.cryoems.bpm.mdoc.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 与 cyroems {@code com.cryo.task.tilt.patchtracking.PatchTrackingResult} 字段命名严格一致。
 *
 * <ul>
 *     <li>{@code tiltxcorrOutput} —— {@code ${name}_pt.fid}</li>
 *     <li>{@code imodchopcontsOutput} —— {@code ${name}.fid}</li>
 * </ul>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PatchTrackingResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String tiltxcorrOutput;
    private String imodchopcontsOutput;
}
