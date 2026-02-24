package com.tradingplatform.infra.kafka.contract.schema;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

final class JsonSchemaValidatorSupport {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final JsonSchemaFactory SCHEMA_FACTORY =
      JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

  private JsonSchemaValidatorSupport() {}

  static void assertValid(String schemaPath, String json) {
    List<String> violations = validate(schemaPath, json);
    assertTrue(
        violations.isEmpty(),
        () -> "Expected payload to satisfy schema " + schemaPath + " but got: " + violations);
  }

  static void assertInvalid(String schemaPath, String json, String expectedMessageFragment) {
    List<String> violations = validate(schemaPath, json);
    assertFalse(
        violations.isEmpty(),
        () -> "Expected payload to violate schema " + schemaPath + " but it was valid");
    assertTrue(
        violations.stream().anyMatch(message -> message.contains(expectedMessageFragment)),
        () ->
            "Expected at least one validation message containing '"
                + expectedMessageFragment
                + "' but got: "
                + violations);
  }

  private static List<String> validate(String schemaPath, String json) {
    JsonSchema schema = loadSchema(schemaPath);
    JsonNode payloadNode = parseJson(json);
    Set<ValidationMessage> messages = schema.validate(payloadNode);
    return messages.stream()
        .map(ValidationMessage::getMessage)
        .sorted(Comparator.naturalOrder())
        .collect(Collectors.toList());
  }

  private static JsonSchema loadSchema(String schemaPath) {
    try (InputStream schemaStream =
        JsonSchemaValidatorSupport.class.getClassLoader().getResourceAsStream(schemaPath)) {
      assertTrue(schemaStream != null, () -> "Schema resource not found: " + schemaPath);
      return SCHEMA_FACTORY.getSchema(schemaStream);
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to load schema from classpath: " + schemaPath, ex);
    }
  }

  private static JsonNode parseJson(String json) {
    try {
      return OBJECT_MAPPER.readTree(json);
    } catch (IOException ex) {
      throw new IllegalArgumentException("Invalid JSON payload under test", ex);
    }
  }
}
