package com.cryo.model;

import com.cryo.task.tilt.MDocMeta;
import com.cryo.task.tilt.align.CoarseAlignrResult;
import com.cryo.task.tilt.filter.ExcludeResult;
import com.cryo.task.tilt.patchtracking.PatchTrackingResult;
import com.cryo.task.tilt.recon.AlignReconResult;
import com.cryo.task.tilt.seriesalign.SeriesAlignResult;
import com.cryo.task.tilt.stack.StackResult;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
@Document
//@CompoundIndex(name = "result_data_and_config_index", def = "{'data_id': 1, 'config_id': 1}", unique = true)
public class MDocResult extends InstanceResult
{


    private MDocMeta meta;
    private StackResult stackResult;
    private CoarseAlignrResult coarseAlignrResult;
    private PatchTrackingResult patchTrackingResult;
    private SeriesAlignResult seriesAlignResult;
    private AlignReconResult alignReconResult;
    private Map<MovieImage.Type, MovieImage> images;
    private int rate = 0;
    private StackResult orgStackResult;
    private ExcludeResult excudedResult;

    public Map<MovieImage.Type, MovieImage> getImages() {
        if( this.images == null ) {
            this.images = new HashMap<>();
        }
        return this.images;
    }

    public void addImage(MovieImage movieImage) {
        getImages().put(movieImage.getType(), movieImage);
    }

}
