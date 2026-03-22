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

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MediaHandler}.
 */
class MediaHandlerTest {

	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		// Register the Media serializers/deserializers
		var module = new com.fasterxml.jackson.databind.module.SimpleModule();
		module.addSerializer(Media.class, new MediaHandler.Serializer());
		module.addDeserializer(Media.class, new MediaHandler.Deserializer());
		objectMapper.registerModule(module);
	}


	@Test
	void testSerializeAndDeserializeMediaWithStringData() throws JsonProcessingException {
		// Given - Media with string data
		String stringData = "test string data";
		MimeType mimeType = MimeTypeUtils.TEXT_PLAIN;
		String mediaId = "string-media-id";

		Media originalMedia = Media.builder()
				.data(stringData)
				.mimeType(mimeType)
				.id(mediaId)
				.build();

		// When - Serialize
		String json = objectMapper.writeValueAsString(originalMedia);

		// Then - Verify JSON contains expected fields
		assertTrue(json.contains("\"@class\""));
		assertTrue(json.contains("\"data\""));
		assertTrue(json.contains("\"mimeType\""));
		assertTrue(json.contains("\"id\""));

		// When - Deserialize
		Media deserializedMedia = objectMapper.readValue(json, Media.class);

		// Then - Verify deserialized object
		assertEquals(originalMedia.getId(), deserializedMedia.getId());
		assertEquals(originalMedia.getMimeType(), deserializedMedia.getMimeType());
		
		// Note: String data gets converted to ByteArrayResource during serialization/deserialization
		assertNotNull(deserializedMedia.getData());
		assertInstanceOf(String.class, deserializedMedia.getData());
		assertEquals(stringData, deserializedMedia.getData());
	}

	@Test
	void testSerializerHandlesNullDataGracefully() throws JsonProcessingException {
		// Given - Media with null data
		MimeType mimeType = MimeTypeUtils.APPLICATION_OCTET_STREAM;

		Media originalMedia = Media.builder()
				.data("aaa")
				.mimeType(mimeType)
				.id("null-data-media")
				.build();

		// When - Serialize
		String json = objectMapper.writeValueAsString(originalMedia);

		// Then - Should still serialize successfully
		assertFalse(json.isEmpty());
		assertTrue(json.contains("\"@class\""));
		assertTrue(json.contains("\"mimeType\""));

		// When - Deserialize
		Media deserializedMedia = objectMapper.readValue(json, Media.class);

		// Then - Should have the same mime type and id
		assertEquals(originalMedia.getId(), deserializedMedia.getId());
		assertEquals(originalMedia.getMimeType(), deserializedMedia.getMimeType());
	}

	@Test
	void testSerializeAndDeserializeMediaWithByteArrayData() throws JsonProcessingException {
		// Given - Media with byte[] data
		byte[] byteData = "test binary data".getBytes();
		MimeType mimeType = MimeTypeUtils.APPLICATION_OCTET_STREAM;
		String mediaId = "byte-array-media-id";

		Media originalMedia = Media.builder()
				.data(byteData)
				.mimeType(mimeType)
				.id(mediaId)
				.build();

		// When - Serialize
		String json = objectMapper.writeValueAsString(originalMedia);

		// Then - Verify JSON contains expected fields
		assertTrue(json.contains("\"@class\""));
		assertTrue(json.contains("\"data\""));
		assertTrue(json.contains("\"dataType\":\"BYTES\""));
		assertTrue(json.contains("\"mimeType\""));

		// When - Deserialize
		Media deserializedMedia = objectMapper.readValue(json, Media.class);

		// Then - Verify deserialized object
		assertEquals(originalMedia.getId(), deserializedMedia.getId());
		assertEquals(originalMedia.getMimeType(), deserializedMedia.getMimeType());
		assertNotNull(deserializedMedia.getData());
		// Data is returned as byte[] after deserialization
		assertInstanceOf(byte[].class, deserializedMedia.getData());
		byte[] resultBytes = (byte[]) deserializedMedia.getData();
		assertArrayEquals(byteData, resultBytes);
	}

	@Test
	void testSerializeAndDeserializeMediaWithResourceData() throws Exception {
		// Given - Media with ByteArrayResource data
		// Note: Media internally converts ByteArrayResource to byte[]
		byte[] resourceData = "resource content".getBytes();
		ByteArrayResource resource = new ByteArrayResource(resourceData);
		MimeType mimeType = MimeTypeUtils.TEXT_PLAIN;

		Media originalMedia = Media.builder()
				.data(resource)
				.mimeType(mimeType)
				.id("resource-media-id")
				.build();

		// When - Serialize
		String json = objectMapper.writeValueAsString(originalMedia);

		// Then - Verify JSON contains expected fields (Media converts Resource to byte[])
		assertTrue(json.contains("\"data\""));
		assertTrue(json.contains("\"dataType\":\"BYTES\""));

		// When - Deserialize
		Media deserializedMedia = objectMapper.readValue(json, Media.class);

		// Then - Verify deserialized object preserves the content
		assertEquals(originalMedia.getId(), deserializedMedia.getId());
		assertEquals(originalMedia.getMimeType(), deserializedMedia.getMimeType());
		assertNotNull(deserializedMedia.getData());
		assertInstanceOf(byte[].class, deserializedMedia.getData());
		assertArrayEquals(resourceData, (byte[]) deserializedMedia.getData());
	}

	@Test
	void testSerializeAndDeserializeMediaWithUrlData() throws Exception {
		// Given - Media with URL data
		URL urlData = new URL("https://example.com/image.png");
		MimeType mimeType = MimeTypeUtils.IMAGE_PNG;

		Media originalMedia = Media.builder()
				.data(urlData)
				.mimeType(mimeType)
				.id("url-media-id")
				.build();

		// When - Serialize
		String json = objectMapper.writeValueAsString(originalMedia);

		// Then - Verify JSON contains expected fields
		assertTrue(json.contains("\"dataType\":\"URL\""));
		assertTrue(json.contains("https://example.com/image.png"));

		// When - Deserialize
		Media deserializedMedia = objectMapper.readValue(json, Media.class);

		// Then - Verify deserialized object
		assertEquals(originalMedia.getId(), deserializedMedia.getId());
		assertEquals(originalMedia.getMimeType(), deserializedMedia.getMimeType());
		assertInstanceOf(URL.class, deserializedMedia.getData());
		assertEquals(urlData, deserializedMedia.getData());
	}

	@Test
	void testSerializeAndDeserializeMediaWithUriData() throws Exception {
		// Given - Media with URI data
		// Note: Media internally converts URI to String
		URI uriData = URI.create("file:///path/to/file.txt");
		MimeType mimeType = MimeTypeUtils.TEXT_PLAIN;

		Media originalMedia = Media.builder()
				.data(uriData)
				.mimeType(mimeType)
				.id("uri-media-id")
				.build();

		// When - Serialize
		String json = objectMapper.writeValueAsString(originalMedia);

		// Then - Verify JSON contains expected fields (Media converts URI to String)
		assertTrue(json.contains("\"data\""));
		assertTrue(json.contains("\"dataType\":\"STRING\""));
		assertTrue(json.contains("file:///path/to/file.txt"));

		// When - Deserialize
		Media deserializedMedia = objectMapper.readValue(json, Media.class);

		// Then - Verify deserialized object
		assertEquals(originalMedia.getId(), deserializedMedia.getId());
		assertEquals(originalMedia.getMimeType(), deserializedMedia.getMimeType());
		assertNotNull(deserializedMedia.getData());
		assertEquals(uriData.toString(), deserializedMedia.getData());
	}

	/**
	 * Test serialization with polymorphic type handling enabled (DefaultTyping).
	 * This simulates the configuration used in SpringAIJacksonStateSerializer.
	 */
	@Test
	void testSerializeWithPolymorphicTypeHandling() throws JsonProcessingException {
		// Given - ObjectMapper with DefaultTyping enabled (same as SpringAIJacksonStateSerializer)
		ObjectMapper polymorphicMapper = createPolymorphicObjectMapper();

		String stringData = "test string data";
		MimeType mimeType = MimeTypeUtils.TEXT_PLAIN;
		String mediaId = "poly-media-id";

		Media originalMedia = Media.builder()
				.data(stringData)
				.mimeType(mimeType)
				.id(mediaId)
				.build();

		// When - Serialize with polymorphic type handling
		String json = polymorphicMapper.writeValueAsString(originalMedia);

		// Then - Verify JSON contains expected fields
		assertTrue(json.contains("\"@class\""));
		assertTrue(json.contains("\"data\""));
		assertTrue(json.contains("\"mimeType\""));
		assertTrue(json.contains("\"id\""));

		// When - Deserialize
		Media deserializedMedia = polymorphicMapper.readValue(json, Media.class);

		// Then - Verify deserialized object
		assertEquals(originalMedia.getId(), deserializedMedia.getId());
		assertEquals(originalMedia.getMimeType(), deserializedMedia.getMimeType());
		assertEquals(stringData, deserializedMedia.getData());
	}

	/**
	 * Test UserMessage with Media serialization using polymorphic type handling.
	 * This directly tests the scenario that caused InvalidDefinitionException.
	 */
	@Test
	void testUserMessageWithMediaPolymorphicSerialization() throws JsonProcessingException {
		// Given - ObjectMapper with DefaultTyping enabled
		ObjectMapper polymorphicMapper = createPolymorphicObjectMapper();

		Media media = Media.builder()
				.data("test image data")
				.mimeType(MimeTypeUtils.IMAGE_PNG)
				.id("image-001")
				.name("test.png")
				.build();

		UserMessage userMessage = UserMessage.builder()
				.text("Hello with image")
				.media(List.of(media))
				.build();

		// When - Serialize UserMessage (this triggers the scenario in the error)
		String json = polymorphicMapper.writeValueAsString(userMessage);

		// Then - Verify JSON is valid and contains media
		assertTrue(json.contains("\"@class\""));
		assertTrue(json.contains("\"text\""));
		assertTrue(json.contains("\"media\""));

		// When - Deserialize
		UserMessage deserializedMessage = polymorphicMapper.readValue(json, UserMessage.class);

		// Then - Verify
		assertEquals("Hello with image", deserializedMessage.getText());
		assertEquals(1, deserializedMessage.getMedia().size());
		assertEquals("image-001", deserializedMessage.getMedia().get(0).getId());
		assertEquals(MimeTypeUtils.IMAGE_PNG, deserializedMessage.getMedia().get(0).getMimeType());
	}

	/**
	 * Test Map with messages containing Media - simulates the actual error scenario.
	 */
	@Test
	void testMapWithUserMessageContainingMediaSerialization() throws JsonProcessingException {
		// Given - ObjectMapper with DefaultTyping enabled
		ObjectMapper polymorphicMapper = createPolymorphicObjectMapper();

		Media media = Media.builder()
				.data("test data")
				.mimeType(MimeTypeUtils.TEXT_PLAIN)
				.id("media-id")
				.build();

		UserMessage userMessage = UserMessage.builder()
				.text("Test message")
				.media(List.of(media))
				.build();

		// This simulates the state map in the error: LinkedHashMap["messages"]->ArrayList[0]
		Map<String, Object> state = new LinkedHashMap<>();
		state.put("messages", List.of(userMessage));

		// When - Serialize the map (this is the exact scenario from the error)
		String json = polymorphicMapper.writeValueAsString(state);

		// Then - Verify JSON is valid
		assertNotNull(json);
		assertFalse(json.isEmpty());
		assertTrue(json.contains("\"messages\""));
	}

	/**
	 * Creates an ObjectMapper configured with polymorphic type handling,
	 * similar to SpringAIJacksonStateSerializer.
	 */
	private ObjectMapper createPolymorphicObjectMapper() {
		ObjectMapper mapper = new ObjectMapper();

		// Register handlers
		var module = new com.fasterxml.jackson.databind.module.SimpleModule();
		module.addSerializer(Media.class, new MediaHandler.Serializer());
		module.addDeserializer(Media.class, new MediaHandler.Deserializer());
		module.addSerializer(UserMessage.class, new UserMessageHandler.Serializer());
		module.addDeserializer(UserMessage.class, new UserMessageHandler.Deserializer());
		mapper.registerModule(module);

		// Enable DefaultTyping (same configuration as SpringAIJacksonStateSerializer)
		ObjectMapper.DefaultTypeResolverBuilder typeResolver = new ObjectMapper.DefaultTypeResolverBuilder(
				ObjectMapper.DefaultTyping.NON_FINAL, LaissezFaireSubTypeValidator.instance) {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean useForType(JavaType t) {
				if (t.isTypeOrSubTypeOf(Map.class) || t.isMapLikeType() || t.isCollectionLikeType()
						|| t.isTypeOrSubTypeOf(Collection.class) || t.isArrayType()) {
					return false;
				}
				return super.useForType(t);
			}
		};
		typeResolver = (ObjectMapper.DefaultTypeResolverBuilder) typeResolver.init(JsonTypeInfo.Id.CLASS, null);
		typeResolver = (ObjectMapper.DefaultTypeResolverBuilder) typeResolver.inclusion(JsonTypeInfo.As.PROPERTY);
		typeResolver = (ObjectMapper.DefaultTypeResolverBuilder) typeResolver.typeProperty("@class");
		mapper.setDefaultTyping(typeResolver);

		return mapper;
	}

}