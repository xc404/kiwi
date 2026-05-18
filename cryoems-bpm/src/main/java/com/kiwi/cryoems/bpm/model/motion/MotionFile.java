package com.kiwi.cryoems.bpm.model.motion;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MotionFile implements Serializable
{

    private String patch_log_file;
    private String path;
    private String type;
    private Meta meta;

    public MotionFile(String patch_log_file) {
        this.patch_log_file = patch_log_file;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Meta {
        private int frame_start;
        private int frame_end;
        private int zero_shift_frame;
        private double psize_A;
    }
}
