package com.cryo.task.dataset;

import com.cryo.model.Microscope;
import lombok.Data;

import java.util.List;

@Data
public class DataSetConfig
{
    private String microscope;
    private String source_dir;
    private List<String> directory_prefixes;
    private String target;
    private List<String> file_types;
    private int  scan_days;
    private boolean delete_movie;
}
