package com.workstudy.agent.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * LLM 结构化输出容错解析器。
 * 模型可能输出 ```json 代码块、前后解释文本、或偶发多余字符，
 * 这里统一提取首个 JSON 数组/对象并解析，解析失败返回空（调用方兜底降级）。
 */
@Component
public class LlmJsonParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 从 LLM 回复中解析出 JSON 数组，失败返回空列表。
     */
    public List<Map<String, Object>> parseJsonArray(String llmText) {
        String json = extractJson(llmText);
        if (json == null) {
            return Collections.emptyList();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.isArray()) {
                return objectMapper.convertValue(node, new TypeReference<List<Map<String, Object>>>() {});
            }
            if (node.isObject()) {
                // 个别模型会包一层 {data: [...]}
                JsonNode data = node.get("data");
                if (data != null && data.isArray()) {
                    return objectMapper.convertValue(data, new TypeReference<List<Map<String, Object>>>() {});
                }
            }
        } catch (Exception ignored) {
            // 解析失败返回空
        }
        return Collections.emptyList();
    }

    /**
     * 从 LLM 回复中解析出 JSON 对象，失败返回空 Map。
     */
    public Map<String, Object> parseJsonObject(String llmText) {
        String json = extractJson(llmText);
        if (json == null) {
            return Collections.emptyMap();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.isObject()) {
                return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {});
            }
        } catch (Exception ignored) {
            // 解析失败返回空
        }
        return Collections.emptyMap();
    }

    /**
     * 提取文本中的首个 JSON 结构（{...} 或 [...]），
     * 处理 ```json 代码块包裹与前后杂文本。
     */
    private String extractJson(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        // 去掉 ```json ... ``` 包裹
        String cleaned = text.replaceAll("(?s)```(?:json)?\\s*", "").replaceAll("(?s)```\\s*$", "").trim();
        int start = -1;
        char open = 0;
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (c == '{' || c == '[') {
                start = i;
                open = c;
                break;
            }
        }
        if (start < 0) {
            return null;
        }
        char close = open == '{' ? '}' : ']';
        int depth = 0;
        for (int i = start; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (c == open) depth++;
            else if (c == close) {
                depth--;
                if (depth == 0) {
                    return cleaned.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    /**
     * 从 Map 中安全取字符串字段。
     */
    public static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }

    /**
     * 从 Map 中安全取字符串列表字段。
     */
    public static List<String> strList(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) {
            return new ArrayList<>();
        }
        if (v instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object o : list) {
                if (o != null) result.add(o.toString());
            }
            return result;
        }
        return new ArrayList<>();
    }
}
