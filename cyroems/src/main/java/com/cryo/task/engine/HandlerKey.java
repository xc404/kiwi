package com.cryo.task.engine;

import lombok.Getter;

public enum HandlerKey
{
    // Movie
    INIT,
    HEADER,
    MOTION_CORRECTION,
    CTF_ESTIMATION,
    VFM,
    VFM_SLURM,
    DefaultSlurm,
    Slurm,
    WriteBack(),
    FINISHED,
    ERROR,

    Result,
    SwitchConfig,
    // Mdoc
    MDocInit,
    MdodParser,
    MovieConnect,
    MdocMotionWait,
    MdocStack,
    MdocExclude,
    MdocCoarseAlign,
    MdocPatchTracking,
    MdocSeriesAlign,
    AlignRecon,
    MDOC_SLURM,
    MDOC_EXPORT,


    //    ExportInit,
    RawMovieExport,
    MotionAndCtfExport(Priority.Low, true),

    CryosparcWait(Priority.Low, true),
    CryosparcVFM;


    @Getter
    private final Priority priority;
    @Getter
    private final boolean async;


    HandlerKey() {
        this(Priority.High, false);
    }

    HandlerKey(Priority priority, boolean async) {
        this.priority = priority;
        this.async = async;
    }

    public enum Priority
    {
        High,
        Low
    }
}
