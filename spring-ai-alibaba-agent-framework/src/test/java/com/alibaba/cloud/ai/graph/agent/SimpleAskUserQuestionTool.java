package com.alibaba.cloud.ai.graph.agent;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.function.BiFunction;

public class SimpleAskUserQuestionTool implements BiFunction<SimpleAskUserQuestionTool.AskUserQuestionInput, ToolContext, String> {
    @Override
    public String apply(AskUserQuestionInput askUserQuestionInput, ToolContext toolContext) {
        return askUserQuestionInput.answers;
    }

    public record AskUserQuestionInput(
            @ToolParam(description = "问用户的问题")
            String question,
            String answers
    ) {
    }

}
