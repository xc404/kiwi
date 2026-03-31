package com.kiwi.project.bpm.service;


import com.kiwi.project.bpm.model.BpmComponent;

import java.util.List;

public interface BpmComponentProvider
{
    List<BpmComponent> getComponents();

    String getSource();
}
