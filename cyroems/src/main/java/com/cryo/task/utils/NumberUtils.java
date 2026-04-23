package com.cryo.task.utils;

import cn.hutool.core.util.NumberUtil;

import java.text.DecimalFormat;

public class NumberUtils
{

    public static  DecimalFormat df = new DecimalFormat("#.#####");
    public static String toString(Double d)

    {
        if(d == null){
            return "";
        }
        return df.format(d);
    }
    public static String toString(Integer d)

    {
        if(d == null){
            return "";
        }
        return df.format(d);
    }

    public static void main(String[] args) {
        System.out.println(toString(123124.1231242353D));
    }
}
