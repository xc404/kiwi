package com.kiwi.project.bpm.service;

import com.kiwi.project.bpm.model.BpmComponent;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PluginBpmComponentProvider implements BpmComponentProvider {

    private volatile List<BpmComponent> components = List.of();

    @Override
    public List<BpmComponent> getComponents() {
        return components;
    }

    void setComponents(List<BpmComponent> components) {
        this.components = List.copyOf(components);
    }

    @Override
    public String getSource() {
        return "plugin";
    }
}
