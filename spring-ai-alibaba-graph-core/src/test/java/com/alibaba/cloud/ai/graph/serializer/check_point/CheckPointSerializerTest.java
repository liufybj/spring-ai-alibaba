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
package com.alibaba.cloud.ai.graph.serializer.check_point;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.serializer.plain_text.jackson.SpringAIJacksonStateSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CheckPointSerializer}.
 * Tests serialization and deserialization of Checkpoint objects with various state types,
 * including Media with byte[] data (fix for "Media data is not a byte[]" issue).
 */
class CheckPointSerializerTest {

	private CheckPointSerializer serializer;

	private SpringAIJacksonStateSerializer stateSerializer;

	@BeforeEach
	void setUp() {
		stateSerializer = new SpringAIJacksonStateSerializer(OverAllState::new);
		serializer = new CheckPointSerializer(stateSerializer);
	}

	@Test
	void testSerializeAndDeserializeBasicCheckpoint() throws Exception {
		// Given - Basic checkpoint with simple state
		Map<String, Object> state = new HashMap<>();
		state.put("key1", "value1");
		state.put("key2", 42);
		state.put("key3", true);

		Checkpoint original = Checkpoint.builder()
				.id("test-id")
				.nodeId("node1")
				.nextNodeId("node2")
				.state(state)
				.build();

		// When - Serialize and deserialize
		Checkpoint deserialized = serializeAndDeserialize(original);

		// Then - Verify
		assertEquals(original.getId(), deserialized.getId());
		assertEquals(original.getNodeId(), deserialized.getNodeId());
		assertEquals(original.getNextNodeId(), deserialized.getNextNodeId());
		assertEquals("value1", deserialized.getState().get("key1"));
		assertEquals(42, deserialized.getState().get("key2"));
		assertEquals(true, deserialized.getState().get("key3"));
	}

	@Test
	void testSerializeAndDeserializeCheckpointWithMessages() throws Exception {
		// Given - Checkpoint with various Message types
		List<Message> messages = List.of(
				SystemMessage.builder().text("System prompt").build(),
				UserMessage.builder().text("User question").build(),
				AssistantMessage.builder().content("Assistant response").build()
		);

		Map<String, Object> state = Map.of("messages", messages, "context", "test-context");

		Checkpoint original = Checkpoint.builder()
				.id("msg-checkpoint")
				.nodeId("chat-node")
				.nextNodeId("end")
				.state(state)
				.build();

		// When - Serialize and deserialize
		Checkpoint deserialized = serializeAndDeserialize(original);

		// Then - Verify messages are correctly preserved
		assertEquals(original.getId(), deserialized.getId());
		Object messagesObj = deserialized.getState().get("messages");
		assertInstanceOf(List.class, messagesObj);

		@SuppressWarnings("unchecked")
		List<Object> deserializedMessages = (List<Object>) messagesObj;
		assertEquals(3, deserializedMessages.size());
		assertInstanceOf(SystemMessage.class, deserializedMessages.get(0));
		assertInstanceOf(UserMessage.class, deserializedMessages.get(1));
		assertInstanceOf(AssistantMessage.class, deserializedMessages.get(2));

		// Verify content
		assertEquals("System prompt", ((SystemMessage) deserializedMessages.get(0)).getText());
		assertEquals("User question", ((UserMessage) deserializedMessages.get(1)).getText());
		assertEquals("Assistant response", ((AssistantMessage) deserializedMessages.get(2)).getText());
	}

	@Test
	void testSerializeAndDeserializeCheckpointWithMediaStringData() throws Exception {
		// Given - Checkpoint with Media containing string data
		Media media = Media.builder()
				.mimeType(MimeTypeUtils.TEXT_PLAIN)
				.data("test string data")
				.id("media-id")
				.name("test.txt")
				.build();

		UserMessage userMessage = UserMessage.builder()
				.text("Message with media")
				.media(List.of(media))
				.build();

		Map<String, Object> state = Map.of("message", userMessage);

		Checkpoint original = Checkpoint.builder()
				.id("media-string-checkpoint")
				.nodeId("media-node")
				.nextNodeId("process")
				.state(state)
				.build();

		// When - Serialize and deserialize
		Checkpoint deserialized = serializeAndDeserialize(original);

		// Then - Verify Media is correctly preserved
		Object msgObj = deserialized.getState().get("message");
		assertInstanceOf(UserMessage.class, msgObj);

		UserMessage deserializedMsg = (UserMessage) msgObj;
		assertEquals(1, deserializedMsg.getMedia().size());

		Media deserializedMedia = deserializedMsg.getMedia().get(0);
		assertEquals(MimeTypeUtils.TEXT_PLAIN, deserializedMedia.getMimeType());
		assertNotNull(deserializedMedia.getData());
	}

	/**
	 * Tests the fix for "Media data is not a byte[]" issue.
	 * This is the key test case for the serialization fix.
	 */
	@Test
	void testSerializeAndDeserializeCheckpointWithMediaByteArrayData() throws Exception {
		// Given - Checkpoint with Media containing byte[] data
		byte[] byteData = "binary image data".getBytes();
		Media media = Media.builder()
				.mimeType(MimeTypeUtils.IMAGE_PNG)
				.data(byteData)
				.id("image-media-id")
				.name("image.png")
				.build();

		UserMessage userMessage = UserMessage.builder()
				.text("Message with binary media")
				.media(List.of(media))
				.build();

		Map<String, Object> state = Map.of("message", userMessage);

		Checkpoint original = Checkpoint.builder()
				.id("media-bytes-checkpoint")
				.nodeId("image-node")
				.nextNodeId("process")
				.state(state)
				.build();

		// When - Serialize and deserialize (this should NOT throw "Media data is not a byte[]")
		assertDoesNotThrow(() -> {
			Checkpoint deserialized = serializeAndDeserialize(original);

			// Then - Verify Media with byte[] data is correctly preserved
			Object msgObj = deserialized.getState().get("message");
			assertInstanceOf(UserMessage.class, msgObj);

			UserMessage deserializedMsg = (UserMessage) msgObj;
			assertEquals(1, deserializedMsg.getMedia().size());

			Media deserializedMedia = deserializedMsg.getMedia().get(0);
			assertEquals(MimeTypeUtils.IMAGE_PNG, deserializedMedia.getMimeType());
			assertNotNull(deserializedMedia.getData());

			// Verify data content is preserved
			Object deserializedData = deserializedMedia.getData();
			assertInstanceOf(byte[].class, deserializedData);
			assertArrayEquals(byteData, (byte[]) deserializedData);
		}, "Serialization should NOT throw 'Media data is not a byte[]' exception");
	}

	@Test
	void testSerializeAndDeserializeCheckpointWithMultipleMediaTypes() throws Exception {
		// Given - Checkpoint with multiple Media types
		Media textMedia = Media.builder()
				.mimeType(MimeTypeUtils.TEXT_PLAIN)
				.data("text content")
				.id("text-media")
				.build();

		Media binaryMedia = Media.builder()
				.mimeType(MimeTypeUtils.APPLICATION_OCTET_STREAM)
				.data("binary content".getBytes())
				.id("binary-media")
				.build();

		UserMessage userMessage = UserMessage.builder()
				.text("Message with multiple media types")
				.media(List.of(textMedia, binaryMedia))
				.build();

		Map<String, Object> state = Map.of("message", userMessage);

		Checkpoint original = Checkpoint.builder()
				.id("multi-media-checkpoint")
				.nodeId("multi-node")
				.nextNodeId("end")
				.state(state)
				.build();

		// When - Serialize and deserialize
		Checkpoint deserialized = serializeAndDeserialize(original);

		// Then - Verify all media types are preserved
		UserMessage deserializedMsg = (UserMessage) deserialized.getState().get("message");
		assertEquals(2, deserializedMsg.getMedia().size());

		// Verify text media
		Media deserializedTextMedia = deserializedMsg.getMedia().get(0);
		assertEquals(MimeTypeUtils.TEXT_PLAIN, deserializedTextMedia.getMimeType());

		// Verify binary media
		Media deserializedBinaryMedia = deserializedMsg.getMedia().get(1);
		assertEquals(MimeTypeUtils.APPLICATION_OCTET_STREAM, deserializedBinaryMedia.getMimeType());
		assertInstanceOf(byte[].class, deserializedBinaryMedia.getData());
	}

	@Test
	void testSerializeAndDeserializeCheckpointWithComplexNestedState() throws Exception {
		// Given - Checkpoint with complex nested state
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("source", "test");
		metadata.put("priority", 1);
		metadata.put("tags", List.of("tag1", "tag2"));

		Map<String, Object> nestedMap = new HashMap<>();
		nestedMap.put("innerKey", "innerValue");
		metadata.put("nested", nestedMap);

		UserMessage userMessage = UserMessage.builder()
				.text("Complex message")
				.metadata(metadata)
				.build();

		Map<String, Object> state = new HashMap<>();
		state.put("message", userMessage);
		state.put("counter", 100);
		state.put("results", List.of("result1", "result2", "result3"));

		Checkpoint original = Checkpoint.builder()
				.id("complex-checkpoint")
				.nodeId("complex-node")
				.nextNodeId("next")
				.state(state)
				.build();

		// When - Serialize and deserialize
		Checkpoint deserialized = serializeAndDeserialize(original);

		// Then - Verify complex state is preserved
		assertEquals(100, deserialized.getState().get("counter"));
		assertInstanceOf(List.class, deserialized.getState().get("results"));

		UserMessage deserializedMsg = (UserMessage) deserialized.getState().get("message");
		assertEquals("Complex message", deserializedMsg.getText());
		assertEquals("test", deserializedMsg.getMetadata().get("source"));
	}

	@Test
	void testSerializeAndDeserializeEmptyStateCheckpoint() throws Exception {
		// Given - Checkpoint with empty state
		Checkpoint original = Checkpoint.builder()
				.id("empty-checkpoint")
				.nodeId("start")
				.nextNodeId("end")
				.state(new HashMap<>())
				.build();

		// When - Serialize and deserialize
		Checkpoint deserialized = serializeAndDeserialize(original);

		// Then - Verify
		assertEquals(original.getId(), deserialized.getId());
		assertEquals(original.getNodeId(), deserialized.getNodeId());
		assertEquals(original.getNextNodeId(), deserialized.getNextNodeId());
		assertTrue(deserialized.getState().isEmpty());
	}

	@Test
	void testSerializeAndDeserializeLargeStateCheckpoint() throws Exception {
		// Given - Checkpoint with large state
		Map<String, Object> state = new HashMap<>();
		for (int i = 0; i < 1000; i++) {
			state.put("key" + i, "value" + i);
		}

		Checkpoint original = Checkpoint.builder()
				.id("large-checkpoint")
				.nodeId("large-node")
				.nextNodeId("end")
				.state(state)
				.build();

		// When - Serialize and deserialize
		Checkpoint deserialized = serializeAndDeserialize(original);

		// Then - Verify all data is preserved
		assertEquals(1000, deserialized.getState().size());
		for (int i = 0; i < 1000; i++) {
			assertEquals("value" + i, deserialized.getState().get("key" + i));
		}
	}

	/**
	 * Helper method to serialize and deserialize a Checkpoint.
	 */
	private Checkpoint serializeAndDeserialize(Checkpoint original) throws Exception {
		// Serialize
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
			serializer.write(original, oos);
		}

		// Deserialize
		ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
		try (ObjectInputStream ois = new ObjectInputStream(bais)) {
			return serializer.read(ois);
		}
	}

}
