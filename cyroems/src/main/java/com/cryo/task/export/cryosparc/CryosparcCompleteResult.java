package com.cryo.task.export.cryosparc;

import lombok.Data;

@Data
public class CryosparcCompleteResult
{
    private final boolean success;
    private final String message;
    private final String projectPath;

    public CryosparcCompleteResult(boolean success, String message, String projectPath)
    {
        this.success = success;
        this.message = message;
        this.projectPath = projectPath;
    }

    public static CryosparcCompleteResult success(String projectPath)
    {
        return new CryosparcCompleteResult(true, null, projectPath);
    }

    public static CryosparcCompleteResult error(String message)
    {
        return new CryosparcCompleteResult(false, message, null);
    }
}
