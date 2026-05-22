package com.kiwi.cryoems.bpm.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MovieImage implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Type {
        patch_log,
        motion_mrc,
        ctf,
        vfm,
        mdoc_recon,
        mdoc_recon_xy,
        mdoc_recon_yz,
        mdoc_recon_xz,
        EXCLUDED_PLOT,
        EXCLUDED_THUMBNAIL
    }

    private Type type;
    private String path;
}
