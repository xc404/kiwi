package com.cryo.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MovieImage
{
    public enum Type
    {
        patch_log,
        motion_mrc,
        ctf,
        vfm,
        mdoc_recon,
        mdoc_recon_xy,
        mdoc_recon_yz,
        mdoc_recon_xz,
        EXCLUDED_PLOT,
        EXCLUDED_THUMBNAIL;
    }

    private Type type;
    private String path;
}
