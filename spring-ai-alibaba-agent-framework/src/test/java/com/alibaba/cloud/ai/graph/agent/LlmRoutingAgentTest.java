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
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;

import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.ToolConfig;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.tool.function.FunctionToolCallback;

import static com.alibaba.cloud.ai.graph.agent.tools.PoetTool.createPoetToolCallback;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@EnabledIfEnvironmentVariable(named = "AI_DASHSCOPE_API_KEY", matches = ".+")
class LlmRoutingAgentTest {

	private ChatModel chatModel;

	@BeforeEach
	void setUp() {
		// Create DashScopeApi instance using the API key from environment variable
		DashScopeApi dashScopeApi = DashScopeApi.builder().apiKey(System.getenv("AI_DASHSCOPE_API_KEY")).build();

		// Create DashScope ChatModel instance
		this.chatModel = DashScopeChatModel.builder().dashScopeApi(dashScopeApi).build();
	}

	@Test
	public void testGraphWithReactAgentSubNode() {
		AskUserQuestionTool collectTool = new AskUserQuestionTool();
		FunctionToolCallback<AskUserQuestionTool.AskUserQuestionInput, String> toolCallback = FunctionToolCallback.builder("AskUserQuestion", collectTool)
				.description("询问用户问题")
				.inputType(AskUserQuestionTool.AskUserQuestionInput.class)
				.inputSchema(ai_code_tool_generate_form)
				.build();

		HumanInTheLoopHook humanInTheLoopHook = HumanInTheLoopHook.builder()
				.approvalOn("AskUserQuestion", ToolConfig.builder()
						.description("请填写需求信息表单")
						.build())
				.build();

		ReactAgent proseWriterAgent = ReactAgent.builder()
			.name("react_agent_1")
			.model(chatModel)
			.description("可以写散文文章。")
			.instruction("你是一个知名的作家，擅长写散文。请根据用户的提问进行回答。")
			.outputKey("prose_article")
			.build();
	}

	@Test
	public void testLlmRoutingAgent() throws Exception {
		ReactAgent proseWriterAgent = ReactAgent.builder()
			.name("prose_writer_agent")
			.model(chatModel)
			.description("可以写散文文章。")
			.instruction("你是一个知名的作家，擅长写散文。请根据用户的提问进行回答。")
			.outputKey("prose_article")
			.build();

		ReactAgent poemWriterAgent = ReactAgent.builder()
			.name("poem_writer_agent")
			.model(chatModel)
			.description("可以写现代诗。")
			.instruction("你是一个知名的诗人，擅长写现代诗。请根据用户的提问，调用工具进行回复。")
			.outputKey("poem_article")
			.tools(List.of(createPoetToolCallback()))
			.build();

		LlmRoutingAgent blogAgent = LlmRoutingAgent.builder()
			.name("blog_agent")
			.model(chatModel)
			.description("可以根据用户给定的主题写文章或作诗。")
			.subAgents(List.of(proseWriterAgent, poemWriterAgent))
			.build();

		try {

			GraphRepresentation representation = blogAgent.getGraph().getGraph(GraphRepresentation.Type.PLANTUML);
			System.out.println(representation.content());

			Optional<OverAllState> result = blogAgent.invoke("帮我写一个100字左右的现代诗");
			blogAgent.invoke("帮我写一个100字左右的现代诗");
			Optional<OverAllState> result3 = blogAgent.invoke("帮我写一个100字左右的现代诗");

			// 验证结果不为空
			assertTrue(result.isPresent(), "Result should be present");
			assertTrue(result3.isPresent(), "Third result should be present");

			OverAllState state = result.get();
			OverAllState state3 = result3.get();

			assertTrue(state.value("input").isPresent(), "Input should be present in state");
			assertEquals("帮我写一个100字左右的现代诗", state.value("input").get(), "Input should match the request");

			assertTrue(state.value("poem_article").isPresent(), "Poem article should be present");
			AssistantMessage poemContent = (AssistantMessage) state.value("poem_article").get();
			assertNotNull(poemContent.getText(), "Poem content should not be null");

			assertTrue(state3.value("poem_article").isPresent(), "Poem article should be present");
			AssistantMessage poemContent3 = (AssistantMessage) state3.value("poem_article").get();
			assertNotNull(poemContent3.getText(), "Poem content should not be null");

			System.out.println(result.get());
			System.out.println("------------------");
			System.out.println(result3.get());
		}
		catch (java.util.concurrent.CompletionException e) {
			e.printStackTrace();
			fail("LlmRoutingAgent execution failed: " + e.getMessage());
		}

		// Verify all hooks were executed
	}

	private final String ai_code_tool_generate_form = """
			{
			    "type": "function",
			    "function": {
			        "name": "AskUserQuestion",
			        "description": "Generates a clarification form based on user requirements analysis. This form is used to collect detailed information needed for website building or project development.\\n\\nUsage:\\n- Use this tool when you need to gather specific information from the user before proceeding\\n- The form should contain clear, well-structured questions that help clarify ambiguous requirements\\n- Support multiple question types: single-select, multi-select, text-area, and file upload\\n- Each question must have a short header (max 12 chars) and a clear, complete question\\n- For select-type questions, provide 2-10 meaningful options with descriptions\\n- At least one option must be selected by default in select-type questions\\n- Consider enabling extendable option for select questions to allow user customization\\n- Use appropriate input types based on the information you need to collect\\n- Group related questions logically to create a coherent user experience\\n",
			        "parameters": {
			            "type": "object",
			            "required": [
			                "title",
			                "questions"
			            ],
			            "properties": {
			                "title": {
			                    "type": "string",
			                    "description": "The title of the questionnaire form"
			                },
			                "questions": {
			                    "type": "array",
			                    "description": "List of questions in the form",
			                    "minItems": 1,
			                    "items": {
			                        "oneOf": [
			                            {
			                                "type": "object",
			                                "description": "Single-select question type",
			                                "required": [
			                                    "header",
			                                    "question",
			                                    "inputType",
			                                    "options",
			                                    "extendable"
			                                ],
			                                "properties": {
			                                    "header": {
			                                        "type": "string",
			                                        "description": "Very short label displayed as a chip/tag (max 12 chars). Examples: \\"Auth method\\", \\"Library\\", \\"Approach\\".",
			                                        "maxLength": 12
			                                    },
			                                    "question": {
			                                        "type": "string",
			                                        "description": "The complete question to ask the user. Should be clear, specific, and end with a question mark. Example: \\"Which library should we use for date formatting?\\""
			                                    },
			                                    "inputType": {
			                                        "type": "string",
			                                        "const": "single-select",
			                                        "description": "Single selection type"
			                                    },
			                                    "options": {
			                                        "type": "array",
			                                        "description": "The available choices for this question. Must have 2-10 options. Each option should be a distinct, mutually exclusive choice",
			                                        "minItems": 2,
			                                        "maxItems": 10,
			                                        "items": {
			                                            "type": "object",
			                                            "required": [
			                                                "label",
			                                                "description"
			                                            ],
			                                            "properties": {
			                                                "label": {
			                                                    "type": "string",
			                                                    "description": "The display text for this option that the user will see and select. Should be concise (1-5 words) and clearly describe the choice."
			                                                },
			                                                "description": {
			                                                    "type": "string",
			                                                    "description": "Explanation of what this option means or what will happen if chosen. Useful for providing context about trade-offs or implications."
			                                                },
			                                                "selected": {
			                                                    "type": "boolean",
			                                                    "description": "Whether this option is selected by default",
			                                                    "default": false
			                                                }
			                                            },
			                                            "additionalProperties": false
			                                        },
			                                        "contains": {
			                                            "type": "object",
			                                            "required": [
			                                                "selected"
			                                            ],
			                                            "properties": {
			                                                "selected": {
			                                                    "const": true
			                                                }
			                                            }
			                                        },
			                                        "maxContains": 1,
			                                        "minContains": 1
			                                    },
			                                    "extendable": {
			                                        "type": "boolean",
			                                        "description": "Whether to allow users to add custom options",
			                                        "default": true
			                                    }
			                                },
			                                "additionalProperties": false
			                            },
			                            {
			                                "type": "object",
			                                "description": "Multi-select question type",
			                                "required": [
			                                    "header",
			                                    "question",
			                                    "inputType",
			                                    "options",
			                                    "extendable"
			                                ],
			                                "properties": {
			                                    "header": {
			                                        "type": "string",
			                                        "description": "Very short label displayed as a chip/tag (max 12 chars). Examples: \\"Features\\", \\"Plugins\\", \\"Integrations\\".",
			                                        "maxLength": 12
			                                    },
			                                    "question": {
			                                        "type": "string",
			                                        "description": "The complete question to ask the user. Should be clear, specific, and end with a question mark. Example: \\"Which features do you want to enable?\\""
			                                    },
			                                    "inputType": {
			                                        "type": "string",
			                                        "const": "multi-select",
			                                        "description": "Multiple selection type"
			                                    },
			                                    "options": {
			                                        "type": "array",
			                                        "description": "The available choices for this question. Must have 2-10 options.",
			                                        "minItems": 2,
			                                        "maxItems": 10,
			                                        "items": {
			                                            "type": "object",
			                                            "required": [
			                                                "label",
			                                                "description"
			                                            ],
			                                            "properties": {
			                                                "label": {
			                                                    "type": "string",
			                                                    "description": "The display text for this option that the user will see and select. Should be concise (1-5 words) and clearly describe the choice."
			                                                },
			                                                "description": {
			                                                    "type": "string",
			                                                    "description": "Explanation of what this option means or what will happen if chosen. Useful for providing context about trade-offs or implications."
			                                                },
			                                                "selected": {
			                                                    "type": "boolean",
			                                                    "description": "Whether this option is selected by default",
			                                                    "default": false
			                                                }
			                                            },
			                                            "additionalProperties": false
			                                        },
			                                        "contains": {
			                                            "type": "object",
			                                            "required": [
			                                                "selected"
			                                            ],
			                                            "properties": {
			                                                "selected": {
			                                                    "const": true
			                                                }
			                                            }
			                                        },
			                                        "minContains": 1
			                                    },
			                                    "extendable": {
			                                        "type": "boolean",
			                                        "description": "Whether to allow users to add custom options",
			                                        "default": true
			                                    }
			                                },
			                                "additionalProperties": false
			                            },
			                            {
			                                "type": "object",
			                                "description": "Text area question type",
			                                "required": [
			                                    "header",
			                                    "question",
			                                    "inputType"
			                                ],
			                                "properties": {
			                                    "header": {
			                                        "type": "string",
			                                        "description": "Very short label displayed as a chip/tag (max 12 chars). Examples: \\"Description\\", \\"Details\\", \\"Notes\\".",
			                                        "maxLength": 12
			                                    },
			                                    "question": {
			                                        "type": "string",
			                                        "description": "The complete question to ask the user. Should be clear, specific, and end with a question mark."
			                                    },
			                                    "inputType": {
			                                        "type": "string",
			                                        "const": "text-area",
			                                        "description": "Text area input type"
			                                    },
			                                    "placeholder": {
			                                        "type": "string",
			                                        "description": "Placeholder text shown in the input field"
			                                    },
			                                    "maxLength": {
			                                        "type": "integer",
			                                        "description": "Maximum character length limit for the input"
			                                    },
			                                    "rows": {
			                                        "type": "integer",
			                                        "description": "Number of rows for the text area. Should be used with maxLength, with each row supporting up to 100 characters",
			                                        "minimum": 1,
			                                        "maximum": 3
			                                    }
			                                },
			                                "additionalProperties": false
			                            },
			                            {
			                                "type": "object",
			                                "description": "File upload question type",
			                                "required": [
			                                    "header",
			                                    "question",
			                                    "inputType",
			                                    "multiple",
			                                    "accept"
			                                ],
			                                "properties": {
			                                    "header": {
			                                        "type": "string",
			                                        "description": "Very short label displayed as a chip/tag (max 12 chars). Examples: \\"Logo\\", \\"Documents\\", \\"Assets\\".",
			                                        "maxLength": 12
			                                    },
			                                    "question": {
			                                        "type": "string",
			                                        "description": "The complete question to ask the user. Should be clear, specific, and end with a question mark."
			                                    },
			                                    "inputType": {
			                                        "type": "string",
			                                        "const": "upload",
			                                        "description": "File upload type"
			                                    },
			                                    "multiple": {
			                                        "type": "boolean",
			                                        "description": "Whether multiple file uploads are supported"
			                                    },
			                                    "accept": {
			                                        "type": "string",
			                                        "description": "Accepted file types, e.g., .doc,.docx,.xml",
			                                        "pattern": "^(\\\\.[a-zA-Z0-9]+)(,\\\\.[a-zA-Z0-9]+)*$"
			                                    }
			                                },
			                                "additionalProperties": false
			                            }
			                        ]
			                    }
			                }
			            }
			        }
			    }
			}
			
			""";
}
