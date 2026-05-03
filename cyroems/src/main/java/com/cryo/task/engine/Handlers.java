package com.cryo.task.engine;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class Handlers implements InitializingBean {

    @Autowired
    private List<Handler> handlerList;
    private Map<HandlerKey, Handler> handlerMap = new HashMap<>();

    public StepResult handle(Context context,  HandlerKey next) {

        Handler movieHandler = this.handlerMap.get(next);
        if (movieHandler == null) {
            throw new RuntimeException(String.format("handler for step %s not found", next));
        }
        return movieHandler.handle(context);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (this.handlerList != null) {
            handlerList.forEach(h -> {
                handlerMap.put(h.support(), h);
            });
        }
    }

}
