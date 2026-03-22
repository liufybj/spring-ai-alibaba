package com.alibaba.cloud.ai.graph.agent.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;

import java.util.List;
import java.util.Map;

public interface ToolCallExecutor {
    void init(AgentToolNode.Builder builder);

    void setToolCallbacks(List<ToolCallback> toolCallbacks);

    void setToolInterceptors(List<ToolInterceptor> toolInterceptors);

    void setToolCallbackResolver(ToolCallbackResolver toolCallbackResolver);

    /**
     * 异步预执行工具调用，缓存工具调用结果，等executeToolCallWithInterceptors实际调用时直接返回缓存结果
     */
    void preExecuteToolCallWithInterceptors(
            AssistantMessage.ToolCall toolCall,
            OverAllState state,
            RunnableConfig config);

    /**
     * 执行工具调用，先从缓存拿结果，拿不到则直接调用
     */
    ToolCallResponse executeToolCallWithInterceptors(
            AssistantMessage.ToolCall toolCall,
            OverAllState state,
            RunnableConfig config,
            Map<String, Object> extraStateFromToolCall);

    /**
     * 所有的工具调用执行完毕，清理缓存
     */
    void allToolCallExecuteFinish();
}
