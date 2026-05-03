//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.cryo.common.utils;

import java.lang.reflect.Array;
import java.util.Collection;

public final class ArrayUtils
{
    public ArrayUtils() {
    }

    public static Object[] toObjectArray(Object source) {
        if( source instanceof Object[] ) {
            return (Object[]) source;
        }
        if( source == null ) {
            return new Object[0];
        }
        if( source instanceof Collection ) {
            return ((Collection<?>) source).toArray();
        }

        if( source.getClass().isArray() ) {
            int length = Array.getLength(source);
            if( length == 0 ) {
                return new Object[0];
            } else {
                Class wrapperType = Array.get(source, 0).getClass();
                Object[] newArray = (Object[]) Array.newInstance(wrapperType, length);

                for( int i = 0; i < length; ++i ) {
                    newArray[i] = Array.get(source, i);
                }

                return newArray;
            }
        }

        throw new IllegalArgumentException("Source is not an array: " + source);
    }

    public static boolean isArrayOrCollection(Object obj) {
        return obj != null && (obj.getClass().isArray() || obj instanceof Collection);
    }
}
