package com.kiwi.bpmn.designer.agent.runtime;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 兼容层：委托 {@link DesignerAgentGraphRuntime}（Graph 编排真相源）。
 */
@Component
@RequiredArgsConstructor
@Deprecated
public class DesignerAgentOrchestrator {

    private final DesignerAgentGraphRuntime graphRuntime;

    public void runTurn(DesignerAgentRun run) {
        graphRuntime.start(run);
    }

    public void processPlanConfirmation(DesignerAgentRun run, boolean confirmed, String editedPlanJson) {
        graphRuntime.resumeAfterPlan(run, confirmed, editedPlanJson);
    }

    public void confirmPreview(DesignerAgentRun run, boolean confirmed) {
        if (Boolean.TRUE.equals(confirmed)) {
            graphRuntime.finishPreviewAccepted(run);
        } else {
            graphRuntime.resumeAfterPreviewReject(run);
        }
    }

    public void prepareAfterAsk(DesignerAgentRun run, String answer) {
        graphRuntime.resumeAfterAsk(run, answer);
    }
}
