package com.cryo.model;

import com.cryo.common.model.DataEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@EqualsAndHashCode(callSuper = true)
@Data
@Document("microscope")
public class Microscope extends DataEntity
{
    private String display_name;

    @Indexed(unique = true)
    private String microscope_key;  // 唯一标识，如 "Titan1_k3"

    private String managed_by;      // 关联 User._id，nullable

    private MicroscopeConfig config;


    /** 返回简短显示名，兼容旧 enum 的 toMicroscopeString() */
    public String toMicroscopeString() {
        if( microscope_key == null ) return display_name;
        return switch( microscope_key ) {
            case "Titan1_k3" -> "Titan1";
            case "Titan2_k3" -> "Titan2";
            case "Titan3_falcon" -> "Titan3";
            default -> microscope_key;
        };
    }

    /** 根据简短名返回 microscope_key，兼容旧 enum 的 fromString()，用于 DB 查询 */
    public static String keyFromString(String microscope) {
        return switch( microscope.toLowerCase() ) {
            case "titan1" -> "Titan1_k3";
            case "titan2" -> "Titan2_k3";
            case "titan3" -> "Titan3_falcon";
            default -> throw new IllegalArgumentException("unsupported microscope: " + microscope);
        };
    }
}
