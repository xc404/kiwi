package com.cryo.task.tilt.filter;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ExcludeResult
{
    private String plotImg;
    private String exclude_thumbnails;
    private List<String> exclude_list;
}
