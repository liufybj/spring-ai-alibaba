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

package com.alibaba.cloud.ai.graph.serializer.plain_text.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link UserMessageHandler}.
 *
 * @author liufengyu
 */
class UserMessageHandlerTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(UserMessage.class, new UserMessageHandler.Serializer());
        module.addDeserializer(UserMessage.class, new UserMessageHandler.Deserializer());
        module.addSerializer(Media.class, new MediaHandler.Serializer());
        module.addDeserializer(Media.class, new MediaHandler.Deserializer());
        objectMapper.registerModule(module);
    }

    @Test
    void serializeDeserializeWithoutMetadata() throws JsonProcessingException {
        String content = "Hello, world!";
        UserMessage originalMessage = new UserMessage(content);

        // Serialize
        String serialized = objectMapper.writeValueAsString(originalMessage);
        
        // Deserialize
        UserMessage deserializedMessage = objectMapper.readValue(serialized, UserMessage.class);

        assertThat(deserializedMessage.getText()).isEqualTo(content);
        // UserMessage builder automatically adds messageType to metadata
        assertThat(deserializedMessage.getMetadata()).containsOnlyKeys("messageType");
        assertThat(deserializedMessage.getMetadata().get("messageType")).isEqualTo(MessageType.USER);
        assertThat(deserializedMessage.getMedia()).isEmpty();
    }

    @Test
    void serializeDeserializeWithMetadata() throws JsonProcessingException {
        String content = "Hello with metadata!";
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("key1", "value1");
        metadata.put("key2", 123);
        metadata.put("key3", true);

        UserMessage originalMessage = new UserMessage(content);
        originalMessage.getMetadata().putAll(metadata);

        // Serialize
        String serialized = objectMapper.writeValueAsString(originalMessage);

        // Deserialize
        UserMessage deserializedMessage = objectMapper.readValue(serialized, UserMessage.class);

        assertThat(deserializedMessage.getText()).isEqualTo(content);
        assertThat(deserializedMessage.getMetadata()).containsAllEntriesOf(metadata);
        assertThat(deserializedMessage.getMedia()).isEmpty();
    }

    @Test
    void serializeDeserializeWithEmptyMetadata() throws JsonProcessingException {
        String content = "Hello with empty metadata!";
        UserMessage originalMessage = new UserMessage(content);

        // Serialize
        String serialized = objectMapper.writeValueAsString(originalMessage);

        // Deserialize
        UserMessage deserializedMessage = objectMapper.readValue(serialized, UserMessage.class);

        assertThat(deserializedMessage.getText()).isEqualTo(content);
        // UserMessage builder automatically adds messageType to metadata
        assertThat(deserializedMessage.getMetadata()).containsOnlyKeys("messageType");
        assertThat(deserializedMessage.getMedia()).isEmpty();
    }

    @Test
    void serializeDeserializeWithMedia() throws JsonProcessingException {
        String content = "Hello with media!";
        Media media = Media.builder().mimeType(MimeTypeUtils.TEXT_PLAIN).data("test string data").build();
        List<Media> medias = List.of(media);

        UserMessage originalMessage = UserMessage.builder()
                .text(content)
                .media(medias)
                .build();

        // Serialize
        String serialized = objectMapper.writeValueAsString(originalMessage);

        // Deserialize
        UserMessage deserializedMessage = objectMapper.readValue(serialized, UserMessage.class);

        assertThat(deserializedMessage.getText()).isEqualTo(content);
        // UserMessage builder automatically adds messageType to metadata
        assertThat(deserializedMessage.getMetadata()).containsOnlyKeys("messageType");
        assertThat(deserializedMessage.getMedia()).hasSize(1);
        assertThat(deserializedMessage.getMedia().get(0).getMimeType()).isEqualTo(media.getMimeType());
    }

    @Test
    void serializeDeserializeWithMetadataAndMedia() throws JsonProcessingException {
        String content = "Hello with metadata and media!";
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "test");
        metadata.put("priority", 1);

        Media media = Media.builder().mimeType(MimeTypeUtils.TEXT_PLAIN).data("test string data").build();
        List<Media> medias = List.of(media);

        UserMessage originalMessage = UserMessage.builder()
                .text(content)
                .metadata(metadata)
                .media(medias)
                .build();

        // Serialize
        String serialized = objectMapper.writeValueAsString(originalMessage);

        // Deserialize
        UserMessage deserializedMessage = objectMapper.readValue(serialized, UserMessage.class);

        assertThat(deserializedMessage.getText()).isEqualTo(content);
        assertThat(deserializedMessage.getMetadata()).containsAllEntriesOf(metadata);
        assertThat(deserializedMessage.getMedia()).hasSize(1);
        assertThat(deserializedMessage.getMedia().get(0).getMimeType()).isEqualTo(media.getMimeType());
    }

    @Test
    void serializeDeserializeSpecialCharacters() throws JsonProcessingException {
        String content = "Hello with special characters: \n \t \" ' & < >";
        UserMessage originalMessage = new UserMessage(content);

        // Serialize
        String serialized = objectMapper.writeValueAsString(originalMessage);

        // Deserialize
        UserMessage deserializedMessage = objectMapper.readValue(serialized, UserMessage.class);

        assertThat(deserializedMessage.getText()).isEqualTo(content);
        // UserMessage builder automatically adds messageType to metadata
        assertThat(deserializedMessage.getMetadata()).containsOnlyKeys("messageType");
        assertThat(deserializedMessage.getMedia()).isEmpty();
    }

    @Test
    void serializeDeserializeChineseCharacters() throws JsonProcessingException {
        String content = "你好，世界！";
        UserMessage originalMessage = new UserMessage(content);

        // Serialize
        String serialized = objectMapper.writeValueAsString(originalMessage);

        // Deserialize
        UserMessage deserializedMessage = objectMapper.readValue(serialized, UserMessage.class);

        assertThat(deserializedMessage.getText()).isEqualTo(content);
        // UserMessage builder automatically adds messageType to metadata
        assertThat(deserializedMessage.getMetadata()).containsOnlyKeys("messageType");
        assertThat(deserializedMessage.getMedia()).isEmpty();
    }

    @Test
    void deserializeWithoutMediaField() throws JsonProcessingException {
        // Simulate a JSON string without the media field
        String jsonString = """
            {
              "@class": "org.springframework.ai.chat.messages.UserMessage",
              "text": "Test message without media",
              "metadata": {}
            }
            """;

        UserMessage deserializedMessage = objectMapper.readValue(jsonString, UserMessage.class);

        assertThat(deserializedMessage.getText()).isEqualTo("Test message without media");
        assertThat(deserializedMessage.getMetadata()).isNotNull();
        assertThat(deserializedMessage.getMedia()).isEmpty();
    }

    @Test
    void deserializeWithoutMetadataField() throws JsonProcessingException {
        // Simulate a JSON string without the metadata field
        String jsonString = """
            {
              "@class": "org.springframework.ai.chat.messages.UserMessage",
              "text": "Test message without metadata"
            }
            """;

        UserMessage deserializedMessage = objectMapper.readValue(jsonString, UserMessage.class);

        assertThat(deserializedMessage.getText()).isEqualTo("Test message without metadata");
        assertThat(deserializedMessage.getMetadata()).isNotNull();
        assertThat(deserializedMessage.getMedia()).isEmpty();
    }
}