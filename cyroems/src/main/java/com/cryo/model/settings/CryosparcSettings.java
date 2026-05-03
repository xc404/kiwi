package com.cryo.model.settings;

import lombok.Data;

@Data
public class CryosparcSettings
{

    public static CryosparcSettings defaultCryosparcSettings = new CryosparcSettings();
    private boolean enabled;
    private boolean autoAttach;
    private boolean gainref_flip_x;
    private boolean gainref_flip_y;
    private int gainref_rotate_num;
    private boolean phase_plate_data;

    private int bfactor = 500;
    private Integer frame_end;
    private Integer frame_start = 0;
    private boolean output_f16 = true;
    private int motion_res_max_align = 5;
    private double smooth_lambda_cal = 0.5;
    private boolean variable_dose;

    private double phase_shift_max = 3.141592653589793;
    private double phase_shift_min = 0;
    private int ctf_res_max_align = 4;
    private int ctf_res_min_align = 25;
    private String output_dir;
    private String output_dir_tail;
    private int class2D_K = 200;
    private int box_size_pix = 256;
}
