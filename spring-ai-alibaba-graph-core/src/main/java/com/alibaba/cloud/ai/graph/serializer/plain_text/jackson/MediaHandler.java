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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.Base64;

public interface MediaHandler {

	enum Field {

		ID("id"), MIME_TYPE("mimeType"), DATA("data"), NAME("name"), DATA_TYPE("dataType"),
		CACHE_CONTROL("cacheControl"), CACHE_CONTROL_TYPE("type");

		final String name;

		Field(String name) {
			this.name = name;
		}

	}

	/**
	 * Data type constants for serialization/deserialization.
	 */
	enum DataType {
		STRING, BYTES, RESOURCE, URL, URI
	}

	class Serializer extends StdSerializer<Media> {

		public Serializer() {
			super(Media.class);
		}

		@Override
		public void serialize(Media media, JsonGenerator gen, SerializerProvider provider) throws IOException {
			gen.writeStartObject();
			gen.writeStringField("@class", media.getClass().getName());

			// Serialize id if present
			if (media.getId() != null) {
				gen.writeStringField(Field.ID.name, media.getId());
			}

			// Serialize mime type
			if (media.getMimeType() != null) {
				gen.writeStringField(Field.MIME_TYPE.name, media.getMimeType().toString());
			}

			// Serialize name if present
			if (media.getName() != null) {
				gen.writeStringField(Field.NAME.name, media.getName());
			}

			// Serialize data - handle different data types
			Object data = media.getData();
			if (data != null) {
				if (data instanceof String stringData) {
					// Handle string data
					gen.writeStringField(Field.DATA_TYPE.name, DataType.STRING.name());
					gen.writeStringField(Field.DATA.name, stringData);
				} else if (data instanceof byte[] byteData) {
					// Handle byte array data - encode as Base64
					gen.writeStringField(Field.DATA_TYPE.name, DataType.BYTES.name());
					gen.writeStringField(Field.DATA.name, Base64.getEncoder().encodeToString(byteData));
				} else if (data instanceof Resource resource) {
					// Handle Resource data - read bytes and encode as Base64
					gen.writeStringField(Field.DATA_TYPE.name, DataType.RESOURCE.name());
					byte[] resourceBytes = resource.getContentAsByteArray();
					gen.writeStringField(Field.DATA.name, Base64.getEncoder().encodeToString(resourceBytes));
				} else if (data instanceof URL urlData) {
					// Handle URL data
					gen.writeStringField(Field.DATA_TYPE.name, DataType.URL.name());
					gen.writeStringField(Field.DATA.name, urlData.toString());
				} else if (data instanceof URI uriData) {
					// Handle URI data
					gen.writeStringField(Field.DATA_TYPE.name, DataType.URI.name());
					gen.writeStringField(Field.DATA.name, uriData.toString());
				} else {
					// For unknown types, try to convert to string
					gen.writeStringField(Field.DATA_TYPE.name, DataType.STRING.name());
					gen.writeStringField(Field.DATA.name, data.toString());
				}
			}

			// Serialize cacheControl if present
			if (media.getCacheControl() != null) {
				gen.writeObjectFieldStart(Field.CACHE_CONTROL.name);
				gen.writeStringField(Field.CACHE_CONTROL_TYPE.name, media.getCacheControl().getType());
				gen.writeEndObject();
			}

			gen.writeEndObject();
		}

		@Override
		public void serializeWithType(Media value, JsonGenerator gen, SerializerProvider serializers, TypeSerializer typeSer) throws IOException {
			serialize(value, gen, serializers);
		}
	}

	class Deserializer extends StdDeserializer<Media> {

		public Deserializer() {
			super(Media.class);
		}

		@Override
		public Media deserialize(JsonParser jsonParser, DeserializationContext ctx) throws IOException {
			ObjectMapper mapper = (ObjectMapper) jsonParser.getCodec();
			ObjectNode node = mapper.readTree(jsonParser);

			// Deserialize mime type
			JsonNode mimeTypeNode = node.get(Field.MIME_TYPE.name);
			String mimeTypeStr = mimeTypeNode != null ? mimeTypeNode.asText() : null;
			MimeType mimeType = StringUtils.hasText(mimeTypeStr) ? MimeType.valueOf(mimeTypeStr) : MimeTypeUtils.APPLICATION_OCTET_STREAM;

			// Deserialize data based on dataType
			JsonNode dataTypeNode = node.get(Field.DATA_TYPE.name);
			JsonNode dataNode = node.get(Field.DATA.name);

			// Create media builder with mimeType
			Media.Builder builder = Media.builder().mimeType(mimeType);

			// Deserialize data according to its type
			if (dataNode != null && !dataNode.isNull()) {
				String dataTypeStr = dataTypeNode != null ? dataTypeNode.asText() : DataType.STRING.name();
				DataType dataType;
				try {
					dataType = DataType.valueOf(dataTypeStr);
				} catch (IllegalArgumentException e) {
					// Default to STRING for backward compatibility
					dataType = DataType.STRING;
				}

				switch (dataType) {
					case BYTES:
						// Decode Base64 to byte array
						byte[] decodedBytes = Base64.getDecoder().decode(dataNode.asText());
						builder.data(decodedBytes);
						break;
					case RESOURCE:
						// Decode Base64 to byte array and wrap in ByteArrayResource
						byte[] resourceBytes = Base64.getDecoder().decode(dataNode.asText());
						builder.data(new ByteArrayResource(resourceBytes));
						break;
					case URL:
						builder.data(new URL(dataNode.asText()));
						break;
					case URI:
						builder.data(URI.create(dataNode.asText()));
						break;
					case STRING:
					default:
						builder.data(dataNode.asText());
						break;
				}
			}

			// Add optional fields if present
			JsonNode idNode = node.get(Field.ID.name);
			if (idNode != null && !idNode.isNull()) {
				builder.id(idNode.asText());
			}

			JsonNode nameNode = node.get(Field.NAME.name);
			if (nameNode != null && !nameNode.isNull()) {
				builder.name(nameNode.asText());
			}

			// Deserialize cacheControl if present
			JsonNode cacheControlNode = node.get(Field.CACHE_CONTROL.name);
			if (cacheControlNode != null && !cacheControlNode.isNull() && cacheControlNode.isObject()) {
				JsonNode typeNode = cacheControlNode.get(Field.CACHE_CONTROL_TYPE.name);
				if (typeNode != null && !typeNode.isNull()) {
					builder.cacheControl(new Media.CacheControl(typeNode.asText()));
				}
			}

			return builder.build();
		}
	}

}