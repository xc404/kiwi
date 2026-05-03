package com.cryo.task.utils;

import com.cryo.model.dataset.MDoc;

public class MdocUtils
{

    public static double getScale(MDoc mDoc) {
        double[] imageSize = mDoc.getMeta().getImageSize();
        int l = (int) (Math.min(imageSize[0], imageSize[1]) / 4);
        double log = Math.log(l) / Math.log(2);
        long closest_power = Math.round(log);
        return Math.pow(0.5, 10 - closest_power);
    }
}
