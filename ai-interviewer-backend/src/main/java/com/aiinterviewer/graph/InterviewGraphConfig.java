package com.aiinterviewer.graph;

import com.aiinterviewer.graph.nodes.JudgeNode;
import com.aiinterviewer.graph.nodes.OpeningNode;
import com.aiinterviewer.graph.nodes.QuestionNode;
import com.aiinterviewer.graph.nodes.SummaryNode;
import com.aiinterviewer.graph.state.InterviewState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * 面试流程 Graph 编排。
 * <p>
 * 流程：
 * <pre>
 * START → opening → question → judge → (shouldEnd?)
 *                                    ├─ 是 → summary → END
 *                                    └─ 否 → question → judge → ...（循环）
 * </pre>
 * <p>
 * 说明：question → judge 之间需要用户回答（USER_ANSWER），
 * T11 的 SSE 协调器在每轮 question 后阻塞等待用户 POST answer，塞入 state 后再触发 judge。
 * T10 阶段 graph 结构定义完成，可用预设 USER_ANSWER 的 state 跑通全流程。
 */
@Configuration
public class InterviewGraphConfig {

    public static final String NODE_OPENING = "opening";
    public static final String NODE_QUESTION = "question";
    public static final String NODE_JUDGE = "judge";
    public static final String NODE_SUMMARY = "summary";

    @Bean
    public StateGraph interviewGraph(OpeningNode opening, QuestionNode question,
                                     JudgeNode judge, SummaryNode summary) throws Exception {
        return buildGraph(opening, question, judge, summary);
    }

    /**
     * 构造面试 graph（供 @Bean 和测试复用）。
     */
    public static StateGraph buildGraph(OpeningNode opening, QuestionNode question,
                                        JudgeNode judge, SummaryNode summary) throws Exception {
        StateGraph graph = new StateGraph(InterviewState.stateFactory());

        graph.addNode(NODE_OPENING, AsyncNodeAction.node_async(opening::apply));
        graph.addNode(NODE_QUESTION, AsyncNodeAction.node_async(question::apply));
        graph.addNode(NODE_JUDGE, AsyncNodeAction.node_async(judge::apply));
        graph.addNode(NODE_SUMMARY, AsyncNodeAction.node_async(summary::apply));

        graph.addEdge(StateGraph.START, NODE_OPENING);
        graph.addEdge(NODE_OPENING, NODE_QUESTION);
        graph.addEdge(NODE_QUESTION, NODE_JUDGE);

        // judge 后条件分支：轮次达上限 → summary；否则 → question 循环
        graph.addConditionalEdges(
                NODE_JUDGE,
                AsyncEdgeAction.edge_async(InterviewGraphConfig::decideAfterJudge),
                Map.of("end", NODE_SUMMARY, "loop", NODE_QUESTION)
        );

        graph.addEdge(NODE_SUMMARY, StateGraph.END);
        return graph;
    }

    /**
     * judge 后路由判定：turn_index >= max_turns → end，否则 → loop。
     */
    static String decideAfterJudge(OverAllState state) {
        Integer turn = state.value(InterviewState.TURN_INDEX, 0);
        Integer maxTurns = state.value(InterviewState.MAX_TURNS, 5);
        if (turn == null) turn = 0;
        if (maxTurns == null) maxTurns = 5;
        return turn >= maxTurns ? "end" : "loop";
    }
}
