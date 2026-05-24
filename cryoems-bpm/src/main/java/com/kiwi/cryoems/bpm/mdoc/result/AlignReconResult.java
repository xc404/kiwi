package com.kiwi.cryoems.bpm.mdoc.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 与 cyroems {@code com.cryo.task.tilt.recon.AlignReconResult} 字段命名严格一致。
 *
 * <p>{@code tiltOutput} / {@code binvolOutput} 同指 {@code ${name}_full_rec_bin4.mrc}
 * （binvol 原位覆盖 tilt 输出，cyroems 当前生产实现亦为同路径）。</p>
 *
 * <ul>
 *     <li>{@code xfproductOutput} —— {@code ${name}_fid.xf}</li>
 *     <li>{@code patch2imodOutput} —— {@code ${name}.resmod}</li>
 *     <li>{@code stack1Output} —— {@code ${name}_ali.mrc}（recon_bin=4）</li>
 *     <li>{@code stack2Output} —— {@code ${name}_ali_bin1.mrc}（recon_bin1=1）</li>
 *     <li>{@code tiltOutput} / {@code binvolOutput} —— {@code ${name}_full_rec_bin4.mrc}</li>
 *     <li>{@code align_reconOutput} —— {@code ${thumbDir}/${name}_full_rec_bin8_unit8.mrc}</li>
 *     <li>{@code align_recon_x_y_view} / {@code _y_z_view} / {@code _x_z_view}
 *         —— align_recon_v2.py 隐式产出的 .png 缩略图三视图</li>
 * </ul>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuppressWarnings("checkstyle:MemberName")
public class AlignReconResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String xfproductOutput;
    private String patch2imodOutput;
    private String stack1Output;
    private String stack2Output;
    private String tiltOutput;
    private String binvolOutput;
    private String align_reconOutput;
    private String align_recon_x_y_view;
    private String align_recon_y_z_view;
    private String align_recon_x_z_view;
}
