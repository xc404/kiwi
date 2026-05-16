package com.kiwi.cryoems.bpm.model.motion;

import com.kiwi.cryoems.bpm.model.MrcMetadata;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MrcFile {

    private String path;
    private MrcMetadata metadata;

    public MrcFile(String path) {
        this(path, null);
    }
}
