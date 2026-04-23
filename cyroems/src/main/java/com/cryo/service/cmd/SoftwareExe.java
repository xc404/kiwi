package com.cryo.service.cmd;

public enum SoftwareExe
{

    groups(),
    check_password("check_password.sh"),
    dm2mrc("dm2mrc"),
    e2proc2d("conda run -n eman2 e2proc2d.py"),
    tif2mrc("tif2mrc"),
    MotionCor2("MotionCor2"),
    header("header"),
    ctffind5(),
    vfm(),
    cryosparc_vfm("conda run -n vfm python /home/cryoems/vfm/draco_platform/picking2cryosparc.py"),
    subtarction(),
    ruijingDefect("conda run -n vfm python /home/stu/vfm-inference/defect_ruijin.py"),

    motion_patch_png("motion_patch_png.sh"),
    mrc_png("mrc_png.sh"),
    ctf_png("ctf_png.sh"),
    titan3_mrc_png("python /home/cryoems/bin/eer_defect/defect.py"),
    chmod("chmod"),
    chown("chown"),

    cryosparc_motion_job(),
    cryosparc_construct(),
    cryosparc_create_project("run_with_cryoems.sh cryosparc_create_project.sh"),
    cryosparc_write_star("run_with_cryoems.sh conda run -n vfm python /home/cryoems/vfm/draco_platform-main/write_star.py"),
    setfacl,
    Titan1Mean,
    Titan2Mean,
    Titan3Mean,
    copy_and_change_own("sudo /usr/sbin/cryoems_rsync_and_chown.sh"),
    squeue("squeue"),
    cp("cp"),
    scancel,
    user_df_space("/home/cryoems/bin/get_used_quota.py"),
    slurm_export,
    vfm_slurm,
    slurm,
    sacct,
    sh("/bin/bash"),
    mdoc_stack("python /home/cryoems/bin/py/mdoc/mrc_stack.py"),
    tiltxcorr,
    xftoxg,
    newstack,
    imodchopconts,
    tilt_series_align("python /home/cryoems/bin/py/mdoc/tilt_series_align.py"),
    stack_and_filter("run_with_cryoems.sh python /home/cryoems/ET_test/stack_exclude.py"),
    xfproduct,
    patch2imod,
    tilt,
    binvol,

    align_recon("python /home/cryoems/bin/py/mdoc/align_recon_v2.py"),

    et_slurm,
    sudo;
    private final String software;

    SoftwareExe() {
        this(null);
    }

    SoftwareExe(String value) {
        this.software = value;
    }

    public String software() {
        if( software != null ) {
            return software;
        }
        return name();
    }


}
