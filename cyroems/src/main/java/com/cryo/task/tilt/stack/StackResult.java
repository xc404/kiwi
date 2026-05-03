package com.cryo.task.tilt.stack;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StackResult
{
    private List<String> files;
    private String outputFile;
    private String titlFile;
    private String excludeFile;
    private List<String> rawFiles;

}
