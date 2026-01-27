package com.alibaba.cloud.ai.graph.agent;

import com.aliyun.domain.monitor.bizlog.BizLog;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.springframework.ai.chat.model.ToolContext;

import java.util.List;
import java.util.function.BiFunction;

public class AskUserQuestionTool implements BiFunction<AskUserQuestionTool.AskUserQuestionInput, ToolContext, String> {
    @Override
    @BizLog(bizDomain = "Tool", opName = "询问用户问题", printResult = true, ignoreParamIndexes = {1})
    public String apply(AskUserQuestionInput askUserQuestionInput, ToolContext toolContext) {
        return askUserQuestionInput.answers;
    }

    public record AskUserQuestionInput(
            String title,
            List<Question> questions,
            String answers
    ) {
    }

    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,      // 使用名称匹配
            include = JsonTypeInfo.As.EXISTING_PROPERTY, // 逻辑变量 inputType 已经在 JSON 属性中了
            property = "inputType",          // 依据哪个字段来区分
            visible = true                   // 设置为 true，反序列化时 inputType 才会赋值给 Record 的参数
    )
    @JsonSubTypes({
            @JsonSubTypes.Type(value = SingleSelectQuestion.class, name = "single-select"),
            @JsonSubTypes.Type(value = MultiSelectQuestion.class, name = "multi-select"),
            @JsonSubTypes.Type(value = TextAreaQuestion.class, name = "text-area"),
            @JsonSubTypes.Type(value = UploadQuestion.class, name = "upload")
    })
    public sealed interface Question permits SingleSelectQuestion, MultiSelectQuestion, TextAreaQuestion, UploadQuestion {
        String header();

        String question();

        String inputType();
    }

    /**
     * 单选题
     */
    public record SingleSelectQuestion(
            String header,

            String question,

            String inputType,

            List<Option> options,

            Boolean extendable
    ) implements Question {
        public SingleSelectQuestion {
            if (inputType == null) {
                inputType = "single-select";
            }
        }
    }

    /**
     * 多选题
     */
    public record MultiSelectQuestion(
            String header,

            String question,

            @JsonProperty("inputType")
            String inputType,

            List<Option> options,

            Boolean extendable
    ) implements Question {
        public MultiSelectQuestion {
            if (inputType == null) {
                inputType = "multi-select";
            }
        }
    }

    /**
     * 文本区域题
     */
    public record TextAreaQuestion(
            String header,

            String question,

            @JsonProperty("inputType")
            String inputType,

            String placeholder,

            Integer maxLength,

            Integer rows
    ) implements Question {
        public TextAreaQuestion {
            if (inputType == null) {
                inputType = "text-area";
            }
        }
    }

    /**
     * 文件上传题
     */
    public record UploadQuestion(
            String header,

            String question,

            @JsonProperty("inputType")
            String inputType,

            Boolean multiple,

            String accept
    ) implements Question {
        public UploadQuestion {
            if (inputType == null) {
                inputType = "upload";
            }
        }
    }

    /**
     * 选项对象
     */
    public record Option(
            String label,

            String description,

            Boolean selected
    ) {
        public Option {
            if (selected == null) {
                selected = false;
            }
        }
    }
}
