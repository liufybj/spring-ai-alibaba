/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.graph.agent;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.ToolConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.fail;

@EnabledIfEnvironmentVariable(named = "AI_DASHSCOPE_API_KEY", matches = ".+")
class AiCodeGenerateAgentTest {

    private static final Logger log = LoggerFactory.getLogger(AiCodeGenerateAgentTest.class);
    private ChatModel chatModel;

	@BeforeEach
	void setUp() {
		// Create DashScopeApi instance using the API key from environment variable
		DashScopeApi dashScopeApi = DashScopeApi.builder().apiKey(System.getenv("AI_DASHSCOPE_API_KEY")).build();

		// Create DashScope ChatModel instance
		this.chatModel = DashScopeChatModel.builder().dashScopeApi(dashScopeApi).build();
	}

	private final ToolCallback askUserQuestionTool = FunctionToolCallback.builder("AskUserQuestion", new SimpleAskUserQuestionTool())
			.description("询问用户问题的工具")
			.inputType(SimpleAskUserQuestionTool.AskUserQuestionInput.class)
			.build();

	// 创建人工介入Hook
	HumanInTheLoopHook humanInTheLoopHook = HumanInTheLoopHook.builder()
			.approvalOn("AskUserQuestion", ToolConfig.builder()
					.description("请补充信息")
					.build())
			.build();

	MemorySaver saver = new MemorySaver();

	@Test
	public void printUml() {
		Agent codeGenerateAgent = buildAgent();
		GraphRepresentation representation = codeGenerateAgent.getGraph().getGraph(GraphRepresentation.Type.PLANTUML);
		System.out.println(representation.content());
	}

	@Test
	public void testSequentialAgent() throws Exception {
		Agent codeGenerateAgent = buildAgent();

		String threadId = "a";
		try {
			RunnableConfig config = RunnableConfig.builder()
					.threadId(threadId)
					.build();

			// 用户需求
			Optional<NodeOutput> output = codeGenerateAgent.invokeAndGetOutput("生成个网站", config);

			// 恢复运行
			InterruptionMetadata feedback = buildUserFeedBack(output.orElseThrow(), "凤铭科技官网");
			RunnableConfig resumeRunnableConfig = RunnableConfig.builder().threadId(threadId)
					.addMetadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY, feedback)
					.build();

			output = codeGenerateAgent.invokeAndGetOutput("", resumeRunnableConfig);

			// 恢复运行
			feedback = buildUserFeedBack(output.orElseThrow(), "展示公司新闻，联系方式等");
			resumeRunnableConfig = RunnableConfig.builder().threadId(threadId)
					.addMetadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY, feedback)
					.build();

			output = codeGenerateAgent.invokeAndGetOutput("", resumeRunnableConfig);

			// 生成PRD，生成代码
			feedback = buildUserFeedBack(output.orElseThrow(), "请生成PRD");
			resumeRunnableConfig = RunnableConfig.builder().threadId(threadId)
					.addMetadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY, feedback)
					.build();

			output = codeGenerateAgent.invokeAndGetOutput("", resumeRunnableConfig);

			// 输出代码
			System.out.println(output.toString());
		} catch (java.util.concurrent.CompletionException e) {
			e.printStackTrace();
			fail("SequentialAgent execution failed: " + e.getMessage());
		}
	}

	private InterruptionMetadata buildUserFeedBack(NodeOutput output, String userFeedback) {
		Assertions.assertEquals(InterruptionMetadata.class, output.getClass());

		// 用户确认
		InterruptionMetadata interruptionMetadata = (InterruptionMetadata) output;
		InterruptionMetadata.Builder newBuilder = InterruptionMetadata.builder()
				.nodeId(interruptionMetadata.node());

		interruptionMetadata.toolFeedbacks().forEach(toolFeedback -> {
			SimpleAskUserQuestionTool.AskUserQuestionInput args = com.alibaba.fastjson.JSON.parseObject(toolFeedback.getArguments(), SimpleAskUserQuestionTool.AskUserQuestionInput.class);

			InterruptionMetadata.ToolFeedback approve = InterruptionMetadata.ToolFeedback
					.builder(toolFeedback)
					.result(InterruptionMetadata.ToolFeedback.FeedbackResult.EDITED)
					.arguments(com.alibaba.fastjson.JSON.toJSONString(new SimpleAskUserQuestionTool.AskUserQuestionInput(args.question(), userFeedback)))
					.build();
			newBuilder.addToolFeedback(approve);
		});

		return newBuilder.build();
	}

	private Agent buildAgent() {
		ReactAgent requirementAgent = ReactAgent.builder()
				.name("requirement_collect")
				.model(chatModel)
				.description("负责跟用户收集网站创建需求。")
				.instruction("你是一个用户需求沟通专家，负责通过AskUserQuestion工具向应用沟通需求。你需要循环询问用户不同的问题，第一个问题先问网站名称，第二个问题问网站功能说明，后面的问题请自我发挥，如果用户回答请生成PRD，则结束需求沟通，汇总用户的回答输出最终需求沟通结果。")
				.tools(List.of(askUserQuestionTool))
				.outputKey("requirement")
				.enableLogging(false)
				.hooks(List.of(humanInTheLoopHook))
				.saver(saver)
				.includeContents(false)
				.build();

		ReactAgent prdAgent = ReactAgent.builder()
				.name("prd_agent")
				.model(chatModel)
				.description("负责生成PRD。")
				.instruction("你是一个专业的产品经理，负责根据用户的需求生成PRD")
				.outputKey("prd")
				.saver(saver)
				.includeContents(false)
				.build();

		ReactAgent codeAgent = ReactAgent.builder()
				.name("code_agent")
				.model(chatModel)
				.description("负责根据PRD生成代码。")
				.instruction("你是一个专业的程序员，负责根据PRD生成代码")
				.outputKey("code")
				.saver(saver)
				.includeContents(false)
				.build();

		return SequentialAgent.builder()
				.name("code_generate_agent")
				.description("跟用户沟通需求，生成PRD，最后生成代码")
				.saver(saver)
				.subAgents(List.of(requirementAgent, prdAgent, codeAgent))
				.build();
	}

}
