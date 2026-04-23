package com.cryo.model.settings;

import lombok.Data;

@Data
public class ExportSettings
{
    private boolean exportVfm;
    private boolean exportMotion;
    private boolean exportCTF;

    private boolean exportGain;
    private boolean exportMotionDw;
    private boolean exportRawMovie;

    private String output_dir;
    private String output_dir_tail;
    private boolean exportMdoc;


}
