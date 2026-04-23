package com.cryo.task.utils;

import org.apache.commons.lang3.math.NumberUtils;

public class MovieUtils
{
    public static String getFileNameIndex(String fileName){
        String[] split = fileName.split("_");
        for( int i = split.length-1; i>=0; i-- ){
            String s = split[i];
            if( NumberUtils.isDigits(s)){
                return s;
            }
        }
        return fileName;
    }


}
