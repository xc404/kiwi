package com.cryo.task.export.cryosparc;

import lombok.Data;

@Data
public class CryosparcConstructParams
{
    private String project_uid;
    private String workspace_uid;
    private String raw_movie_paths;
    private String gain_path;
    private String job_list_file;
    //    private String param_file;
    private String output_dir;
    private String task_name;
    private String tmp_output_file;
    //    private String exported_movie_dir;
    private String owner;
    private String group;
    private String acl_users;
//    private String denoise_res_dir;
}
