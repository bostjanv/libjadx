package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

/** Evaluates the JSON Schema keywords used by the reviewed OpenAPI examples. */
final class OpenApiExampleValidator {
	private OpenApiExampleValidator() { }

	static void assertValid(JsonNode document, String schemaName, JsonNode value) {
		List<String> errors = new ArrayList<>();
		validate(document, document.path("components").path("schemas").path(schemaName), value, "$", errors);
		assertTrue(errors.isEmpty(), String.join("\n", errors));
	}

	private static void validate(JsonNode document, JsonNode schema, JsonNode value, String path, List<String> errors) {
		if (schema.has("$ref")) {
			String ref = schema.path("$ref").asText();
			if (!ref.startsWith("#/components/schemas/")) { errors.add(path + " unsupported ref " + ref); return; }
			validate(document, document.path("components").path("schemas").path(ref.substring(21)), value, path, errors);
			return;
		}
		if (schema.has("oneOf")) {
			int valid = 0;
			for (JsonNode option : schema.path("oneOf")) {
				List<String> optionErrors = new ArrayList<>();
				validate(document, option, value, path, optionErrors);
				if (optionErrors.isEmpty()) valid++;
			}
			if (valid != 1) errors.add(path + " must match exactly one schema (matched " + valid + ")");
			return;
		}
		if (schema.has("type")) {
			JsonNode types = schema.path("type");
			boolean okay = types.isArray() ? iterable(types).stream().anyMatch(type -> matchesType(type.asText(), value))
					: matchesType(types.asText(), value);
			if (!okay) { errors.add(path + " has wrong type"); return; }
		}
		if (schema.has("const") && !schema.path("const").equals(value)) errors.add(path + " violates const");
		if (schema.has("enum")) {
			boolean found = iterable(schema.path("enum")).stream().anyMatch(value::equals);
			if (!found) errors.add(path + " violates enum");
		}
		if (value.isTextual()) {
			String text = value.asText();
			if (schema.has("maxLength") && text.length() > schema.path("maxLength").asInt()) errors.add(path + " exceeds maxLength");
			if (schema.has("pattern") && !Pattern.compile(schema.path("pattern").asText()).matcher(text).find())
				errors.add(path + " violates pattern");
			if ("uuid".equals(schema.path("format").asText())) {
				try { UUID.fromString(text); } catch (RuntimeException invalid) { errors.add(path + " is not a UUID"); }
			}
		}
		if (value.isNumber() && schema.has("minimum") && value.asDouble() < schema.path("minimum").asDouble())
			errors.add(path + " is below minimum");
		if (value.isArray()) {
			if (schema.has("maxItems") && value.size() > schema.path("maxItems").asInt()) errors.add(path + " exceeds maxItems");
			for (int i = 0; i < value.size(); i++) validate(document, schema.path("items"), value.get(i), path + "[" + i + "]", errors);
		}
		if (value.isObject()) {
			for (JsonNode field : schema.path("required"))
				if (!value.has(field.asText())) errors.add(path + " missing " + field.asText());
			JsonNode properties = schema.path("properties");
			value.fieldNames().forEachRemaining(name -> {
				if (!properties.has(name)) {
					if (schema.path("additionalProperties").isBoolean()
							&& !schema.path("additionalProperties").booleanValue()) errors.add(path + " unknown " + name);
				} else validate(document, properties.path(name), value.path(name), path + "." + name, errors);
			});
			if (schema.has("if")) {
				List<String> conditionErrors = new ArrayList<>();
				validate(document, schema.path("if"), value, path, conditionErrors);
				JsonNode branch = conditionErrors.isEmpty() ? schema.path("then") : schema.path("else");
				if (!branch.isMissingNode()) validate(document, branch, value, path, errors);
			}
		}
	}

	private static boolean matchesType(String type, JsonNode value) {
		return switch (type) {
			case "object" -> value.isObject();
			case "array" -> value.isArray();
			case "string" -> value.isTextual();
			case "integer" -> value.isIntegralNumber();
			case "number" -> value.isNumber();
			case "boolean" -> value.isBoolean();
			case "null" -> value.isNull();
			default -> false;
		};
	}

	private static List<JsonNode> iterable(JsonNode array) {
		List<JsonNode> result = new ArrayList<>();
		array.forEach(result::add);
		return result;
	}
}
