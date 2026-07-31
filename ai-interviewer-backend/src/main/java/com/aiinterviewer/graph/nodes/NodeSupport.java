package com.aiinterviewer.graph.nodes;

import com.aiinterviewer.entity.ModelConfig;
import com.aiinterviewer.graph.ChatClientFactory;
import com.aiinterviewer.graph.state.InterviewState;
import com.aiinterviewer.mapper.ModelConfigMapper;
import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * Graph 节点共用支持：从 state 读取 model_config_id 并构造 ChatClient。
 * <p>
 * 避免每个节点重复 "查 DB + 构造 client" 的逻辑。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NodeSupport {

    private final ModelConfigMapper modelConfigMapper;
    private final ChatClientFactory chatClientFactory;

    /**
     * 从 state 读取 model_config_id，查 DB 构造 ChatClient。
     */
    public ChatClient getChatClient(OverAllState state) {
        return getChatClientByConfigId(state.value(InterviewState.MODEL_CONFIG_ID, (Long) null));
    }

    public ChatClient getChatClientByConfigId(Long configId) {
        if (configId == null) {
            throw new IllegalStateException("state 中缺少 model_config_id");
        }
        ModelConfig config = modelConfigMapper.selectById(configId);
        if (config == null) {
            throw new IllegalStateException("model_config 不存在, id=" + configId);
        }
        return chatClientFactory.createChatClient(config);
    }

    /**
     * 读取 state 中的字符串值，null 返回空串（便于 prompt 填充）。
     */
    public String text(OverAllState state, String key) {
        Object v = state.value(key, "");
        return v == null ? "" : String.valueOf(v);
    }
}
