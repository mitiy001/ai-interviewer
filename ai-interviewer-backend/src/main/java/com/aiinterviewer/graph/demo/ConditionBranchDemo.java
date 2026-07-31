package com.aiinterviewer.graph.demo;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.OverAllStateFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 条件分支 demo graph：验证 Spring AI Alibaba graph API 形态。
 * <p>
 * 流程：START → score_node → (score>5 ? high_path : low_path) → END
 * <p>
 * 该 demo 不调用真实 LLM，纯状态流转，用于在 T8 阶段验证 graph API 可用。
 * T9/T10 会基于此模式实现真实的面试节点和编排。
 */
@Slf4j
@Component
public class ConditionBranchDemo {

    /** 高分路径节点 id */
    public static final String NODE_SCORE = "score_node";
    public static final String NODE_HIGH = "high_node";
    public static final String NODE_LOW = "low_node";
    /** demo 专用输入 key（注意：必须注册 KeyStrategy 才会被引擎合并进 state） */
    public static final String KEY_SCORE = "score";

    /**
     * demo 专用 stateFactory：注册 score + ai_output 两个 key。
     * 不复用 {@link com.aiinterviewer.graph.state.InterviewState}，避免污染生产状态定义。
     */
    private static OverAllStateFactory demoStateFactory() {
        return () -> {
            OverAllState state = new OverAllState();
            Map<String, KeyStrategy> strategies = new HashMap<>();
            strategies.put(KEY_SCORE, new ReplaceStrategy());
            strategies.put("ai_output", new ReplaceStrategy());
            state.registerKeyAndStrategy(strategies);
            return state;
        };
    }

    /**
     * 构造 demo graph
     */
    public StateGraph buildGraph() throws Exception {
        StateGraph graph = new StateGraph(demoStateFactory());

        // 节点 1：读取输入的 score，记录日志（验证状态读取）
        graph.addNode(NODE_SCORE, AsyncNodeAction.node_async(state -> {
            Integer score = state.value(KEY_SCORE, 0);
            log.info("[demo] score_node 收到 score={}", score);
            return Map.of(); // 不修改状态，仅验证读取
        }));

        // 节点 2：高分路径
        graph.addNode(NODE_HIGH, AsyncNodeAction.node_async(state -> {
            log.info("[demo] high_node 执行");
            return Map.of("ai_output", "高分路径");
        }));

        // 节点 3：低分路径
        graph.addNode(NODE_LOW, AsyncNodeAction.node_async(state -> {
            log.info("[demo] low_node 执行");
            return Map.of("ai_output", "低分路径");
        }));

        // 入口边
        graph.addEdge(StateGraph.START, NODE_SCORE);

        // 条件分支：score > 5 → high_node，否则 → low_node
        graph.addConditionalEdges(
                NODE_SCORE,
                AsyncEdgeAction.edge_async(state -> {
                    Integer score = state.value(KEY_SCORE, 0);
                    return score > 5 ? "high_path" : "low_path";
                }),
                Map.of("high_path", NODE_HIGH, "low_path", NODE_LOW)
        );

        // 两条路径都到 END
        graph.addEdge(NODE_HIGH, StateGraph.END);
        graph.addEdge(NODE_LOW, StateGraph.END);

        return graph;
    }

    /**
     * 运行 demo：传入 score，返回最终 ai_output
     */
    public String run(int score) throws Exception {
        StateGraph graph = buildGraph();
        CompiledGraph compiled = graph.compile();
        Optional<OverAllState> result =
                compiled.invoke(Map.of(KEY_SCORE, score));
        if (result.isEmpty()) {
            return "(空)";
        }
        Object out = result.get().value("ai_output", (Object) null);
        return out == null ? "(null)" : String.valueOf(out);
    }
}
