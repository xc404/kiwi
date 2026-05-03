package com.cryo.model.settings;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class ETSettings
{
    public static final ETSettings defaultETSettings = new ETSettings("IMOD", new ImodSetting());

    private String software;
    private ImodSetting imod;
}
