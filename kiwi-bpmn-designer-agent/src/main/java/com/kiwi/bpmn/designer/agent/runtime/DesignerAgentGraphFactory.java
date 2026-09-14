package com.kiwi.bpmn.designer.agent.runtime;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.serializer.std.ObjectStreamStateSerializer;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig.node_async;

/**
 * 构建并编译 Designer Agent {@link StateGraph}。
 */
@Component
public class DesignerAgentGraphFactory {

    private final DesignerAgentGraphNodes nodes;
    private final BaseCheckpointSaver checkpointSaver;

    public DesignerAgentGraphFactory(
            DesignerAgentGraphNodes nodes,
            Optional<BaseCheckpointSaver> checkpointSaver) {
        this.nodes = nodes;
        this.checkpointSaver = checkpointSaver.orElseGet(() -> MemorySaver.builder().build());
    }

    public CompiledGraph compile() throws GraphStateException {
        StateGraph graph = new StateGraph(
                "designer-agent",
                keyStrategies(),
                new ObjectStreamStateSerializer(OverAllState::new));

        graph.addNode(DesignerAgentGraphNodes.Ingest, node_async(nodes::ingest));
        graph.addNode(DesignerAgentGraphNodes.Explain, node_async(nodes::explain));
        graph.addNode(DesignerAgentGraphNodes.PrepareClarify, node_async(nodes::prepareClarify));
        graph.addNode(DesignerAgentGraphNodes.HumanClarify, node_async(nodes::humanClarify));
        graph.addNode(DesignerAgentGraphNodes.Generate, node_async(nodes::generate));
        graph.addNode(DesignerAgentGraphNodes.HumanPlan, node_async(nodes::humanPlan));
        graph.addNode(DesignerAgentGraphNodes.Apply, node_async(nodes::apply));
        graph.addNode(DesignerAgentGraphNodes.Validate, node_async(nodes::validate));
        graph.addNode(DesignerAgentGraphNodes.HumanPreview, node_async(nodes::humanPreview));
        graph.addNode(DesignerAgentGraphNodes.HumanAsk, node_async(nodes::humanAsk));
        graph.addNode(DesignerAgentGraphNodes.HumanInstall, node_async(nodes::humanInstall));
        graph.addNode(DesignerAgentGraphNodes.HumanFollowUp, node_async(nodes::humanFollowUp));
        graph.addNode(DesignerAgentGraphNodes.Fail, node_async(nodes::fail));
        graph.addNode(DesignerAgentGraphNodes.Finish, node_async(nodes::finish));

        graph.addEdge(START, DesignerAgentGraphNodes.Ingest);
        graph.addConditionalEdges(DesignerAgentGraphNodes.Ingest, routeEdge(), Map.of(
                DesignerAgentStateKeys.RouteExplain, DesignerAgentGraphNodes.Explain,
                DesignerAgentStateKeys.RoutePrepareClarify, DesignerAgentGraphNodes.PrepareClarify));
        graph.addEdge(DesignerAgentGraphNodes.Explain, DesignerAgentGraphNodes.Finish);
        graph.addConditionalEdges(DesignerAgentGraphNodes.PrepareClarify, routeEdge(), Map.of(
                DesignerAgentStateKeys.RouteHumanClarify, DesignerAgentGraphNodes.HumanClarify,
                DesignerAgentStateKeys.RouteGenerate, DesignerAgentGraphNodes.Generate));
        graph.addEdge(DesignerAgentGraphNodes.HumanClarify, DesignerAgentGraphNodes.Generate);
        graph.addConditionalEdges(DesignerAgentGraphNodes.Generate, routeEdge(), Map.of(
                DesignerAgentStateKeys.RouteHumanPlan, DesignerAgentGraphNodes.HumanPlan,
                DesignerAgentStateKeys.RouteApply, DesignerAgentGraphNodes.Apply,
                DesignerAgentStateKeys.RouteHumanAsk, DesignerAgentGraphNodes.HumanAsk,
                DesignerAgentStateKeys.RouteFail, DesignerAgentGraphNodes.Fail));
        graph.addConditionalEdges(DesignerAgentGraphNodes.HumanPlan, routeEdge(), Map.of(
                DesignerAgentStateKeys.RouteApply, DesignerAgentGraphNodes.Apply,
                DesignerAgentStateKeys.RouteGenerate, DesignerAgentGraphNodes.Generate));
        graph.addEdge(DesignerAgentGraphNodes.Apply, DesignerAgentGraphNodes.Validate);
        graph.addConditionalEdges(DesignerAgentGraphNodes.Validate, routeEdge(), Map.of(
                DesignerAgentStateKeys.RouteGenerate, DesignerAgentGraphNodes.Generate,
                DesignerAgentStateKeys.RouteHumanInstall, DesignerAgentGraphNodes.HumanInstall,
                DesignerAgentStateKeys.RouteHumanAsk, DesignerAgentGraphNodes.HumanAsk,
                DesignerAgentStateKeys.RouteHumanPreview, DesignerAgentGraphNodes.HumanPreview));
        graph.addConditionalEdges(DesignerAgentGraphNodes.HumanPreview, routeEdge(), Map.of(
                DesignerAgentStateKeys.RoutePersistPreview, DesignerAgentGraphNodes.Finish,
                DesignerAgentStateKeys.RouteGenerate, DesignerAgentGraphNodes.Generate,
                DesignerAgentStateKeys.RouteHumanAsk, DesignerAgentGraphNodes.HumanAsk,
                DesignerAgentStateKeys.RouteHumanPreview, DesignerAgentGraphNodes.HumanPreview));
        graph.addEdge(DesignerAgentGraphNodes.HumanAsk, DesignerAgentGraphNodes.Generate);
        graph.addConditionalEdges(DesignerAgentGraphNodes.HumanInstall, routeEdge(), Map.of(
                DesignerAgentStateKeys.RouteValidate, DesignerAgentGraphNodes.Validate,
                DesignerAgentStateKeys.RouteHumanFollowUp, DesignerAgentGraphNodes.Finish,
                DesignerAgentStateKeys.RouteHumanInstall, DesignerAgentGraphNodes.HumanInstall));
        graph.addEdge(DesignerAgentGraphNodes.Fail, DesignerAgentGraphNodes.Finish);
        graph.addEdge(DesignerAgentGraphNodes.Finish, DesignerAgentGraphNodes.HumanFollowUp);
        graph.addConditionalEdges(DesignerAgentGraphNodes.HumanFollowUp, routeEdge(), Map.of(
                DesignerAgentStateKeys.RouteIngest, DesignerAgentGraphNodes.Ingest,
                DesignerAgentStateKeys.RouteHumanFollowUp, DesignerAgentGraphNodes.HumanFollowUp));

        CompileConfig compileConfig = CompileConfig.builder()
                .saverConfig(SaverConfig.builder().register(checkpointSaver).build())
                .interruptBefore(
                        DesignerAgentGraphNodes.HumanClarify,
                        DesignerAgentGraphNodes.HumanPlan,
                        DesignerAgentGraphNodes.HumanPreview,
                        DesignerAgentGraphNodes.HumanAsk,
                        DesignerAgentGraphNodes.HumanInstall,
                        DesignerAgentGraphNodes.HumanFollowUp)
                .build();
        return graph.compile(compileConfig);
    }

    private KeyStrategyFactory keyStrategies() {
        return () -> {
            Map<String, KeyStrategy> strategies = new HashMap<>();
            for (String key : DesignerAgentStateKeys.AllKeys) {
                strategies.put(key, new ReplaceStrategy());
            }
            return strategies;
        };
    }

    private AsyncEdgeAction routeEdge() {
        EdgeAction route = state -> state.value(
                DesignerAgentStateKeys.Route, DesignerAgentStateKeys.RouteGenerate);
        return AsyncEdgeAction.edge_async(route);
    }
}
