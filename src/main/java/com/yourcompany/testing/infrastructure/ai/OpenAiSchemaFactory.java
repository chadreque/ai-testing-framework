package com.yourcompany.testing.infrastructure.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class OpenAiSchemaFactory {
    private OpenAiSchemaFactory() {}

    public static ObjectNode testSpecification(ObjectMapper mapper) {
        ObjectNode root = object(mapper);
        ObjectNode properties = root.putObject("properties");
        string(properties, "title");
        string(properties, "pageUrl");

        ObjectNode testCases = array(properties, "testCases");
        ObjectNode testCase = objectNode(testCases.putObject("items"));
        ObjectNode testCaseProperties = testCase.putObject("properties");
        string(testCaseProperties, "id");
        string(testCaseProperties, "title");
        string(testCaseProperties, "description");
        stringArray(testCaseProperties, "preconditions");

        ObjectNode steps = array(testCaseProperties, "steps");
        ObjectNode step = objectNode(steps.putObject("items"));
        ObjectNode stepProperties = step.putObject("properties");
        integer(stepProperties, "order");
        string(stepProperties, "action");
        string(stepProperties, "data");
        string(stepProperties, "expectedResult");
        requireAll(step, "order", "action", "data", "expectedResult");

        stringArray(testCaseProperties, "expectedResults");
        ObjectNode data = array(testCaseProperties, "data");
        ObjectNode dataItem = objectNode(data.putObject("items"));
        ObjectNode dataProperties = dataItem.putObject("properties");
        string(dataProperties, "key");
        string(dataProperties, "value");
        requireAll(dataItem, "key", "value");

        requireAll(testCase, "id", "title", "description", "preconditions", "steps", "expectedResults", "data");
        requireAll(root, "title", "pageUrl", "testCases");
        return root;
    }

    public static ObjectNode feature(ObjectMapper mapper) {
        ObjectNode root = object(mapper);
        ObjectNode properties = root.putObject("properties");
        string(properties, "featureName");
        ObjectNode scenarios = array(properties, "scenarios");
        ObjectNode scenario = objectNode(scenarios.putObject("items"));
        ObjectNode scenarioProperties = scenario.putObject("properties");
        string(scenarioProperties, "name");
        ObjectNode steps = array(scenarioProperties, "steps");
        ObjectNode step = objectNode(steps.putObject("items"));
        ObjectNode stepProperties = step.putObject("properties");
        enumString(stepProperties, "keyword", "Given", "When", "Then", "And", "But");
        string(stepProperties, "text");
        requireAll(step, "keyword", "text");
        requireAll(scenario, "name", "steps");
        requireAll(root, "featureName", "scenarios");
        return root;
    }

    public static ObjectNode stepGeneration(ObjectMapper mapper) {
        ObjectNode root = object(mapper);
        ObjectNode properties = root.putObject("properties");
        ObjectNode classes = array(properties, "classes");
        ObjectNode stepClass = objectNode(classes.putObject("items"));
        ObjectNode classProperties = stepClass.putObject("properties");

        string(classProperties, "className");

        ObjectNode steps = array(classProperties, "steps");
        ObjectNode step = objectNode(steps.putObject("items"));
        ObjectNode stepProperties = step.putObject("properties");

        enumString(stepProperties, "keyword", "Given", "When", "Then", "And", "But");

        string(stepProperties, "expression");
        string(stepProperties, "methodName");

        ObjectNode parameters = array(stepProperties, "parameters");
        ObjectNode parameter = objectNode(parameters.putObject("items"));
        ObjectNode parameterProperties = parameter.putObject("properties");

        string(parameterProperties, "name");

        requireAll(parameter, "name");

        string(stepProperties, "body");

        requireAll(step, "keyword", "expression", "methodName", "parameters", "body");
        requireAll(stepClass, "className", "steps");
        requireAll(root, "classes");

        return root;
    }

    public static ObjectNode pageComponents(ObjectMapper mapper) {
        ObjectNode root = object(mapper);
        ObjectNode properties = root.putObject("properties");
        ObjectNode components = array(properties, "components");
        components.set("items", componentDefinition(mapper));
        requireAll(root, "components");
        return root;
    }

    public static ObjectNode singleComponentLookup(ObjectMapper mapper) {
        ObjectNode root = object(mapper);
        ObjectNode properties = root.putObject("properties");
        booleanValue(properties, "found");
        string(properties, "reason");
        ObjectNode components = array(properties, "components");
        components.put("maxItems", 1);
        components.set("items", componentDefinition(mapper));
        requireAll(root, "found", "reason", "components");
        return root;
    }

    public static ObjectNode componentDefinition(ObjectMapper mapper) {
        ObjectNode component = object(mapper);
        ObjectNode properties = component.putObject("properties");
        nonBlankString(properties, "logicalName");
        string(properties, "elementType");

        ObjectNode metadata = objectNode(properties.putObject("metadata"));
        ObjectNode metadataProperties = metadata.putObject("properties");
        string(metadataProperties, "expectedTag");
        string(metadataProperties, "expectedRole");
        string(metadataProperties, "expectedText");
        string(metadataProperties, "expectedLabel");
        string(metadataProperties, "expectedType");
        requireAll(metadata, "expectedTag", "expectedRole", "expectedText", "expectedLabel", "expectedType");

        ObjectNode selectors = array(properties, "selectors");
        selectors.put("minItems", 1);
        selectors.put("maxItems", 5);
        ObjectNode selector = objectNode(selectors.putObject("items"));
        ObjectNode selectorProperties = selector.putObject("properties");
        enumString(selectorProperties, "strategy", "ID", "NAME", "CSS", "XPATH", "CLASS_NAME", "TAG_NAME", "LINK_TEXT");
        nonBlankString(selectorProperties, "value");
        number(selectorProperties, "confidence").put("minimum", 0).put("maximum", 1);
        string(selectorProperties, "reason");
        requireAll(selector, "strategy", "value", "confidence", "reason");

        requireAll(component, "logicalName", "elementType", "metadata", "selectors");
        return component;
    }

    private static ObjectNode object(ObjectMapper mapper) {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "object");
        node.put("additionalProperties", false);
        return node;
    }

    private static ObjectNode objectNode(ObjectNode node) {
        node.put("type", "object");
        node.put("additionalProperties", false);
        return node;
    }

    private static ObjectNode array(ObjectNode properties, String name) {
        ObjectNode node = properties.putObject(name);
        node.put("type", "array");
        return node;
    }

    private static void string(ObjectNode properties, String name) {
        properties.putObject(name).put("type", "string");
    }

    private static void nonBlankString(ObjectNode properties, String name) {
        properties.putObject(name).put("type", "string").put("minLength", 1);
    }

    private static void booleanValue(ObjectNode properties, String name) {
        properties.putObject(name).put("type", "boolean");
    }

    private static ObjectNode number(ObjectNode properties, String name) {
        return properties.putObject(name).put("type", "number");
    }

    private static void integer(ObjectNode properties, String name) {
        properties.putObject(name).put("type", "integer");
    }

    private static void stringArray(ObjectNode properties, String name) {
        ObjectNode node = array(properties, name);
        node.putObject("items").put("type", "string");
    }

    private static void enumString(ObjectNode properties, String name, String... values) {
        ObjectNode node = properties.putObject(name);
        node.put("type", "string");
        ArrayNode enumValues = node.putArray("enum");
        for (String value : values) enumValues.add(value);
    }

    private static void requireAll(ObjectNode object, String... fields) {
        ArrayNode required = object.putArray("required");
        for (String field : fields) required.add(field);
    }
}
