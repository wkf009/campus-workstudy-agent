package com.workstudy.agent.common;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LlmJsonParserTest {

    private final LlmJsonParser parser = new LlmJsonParser();

    @Test
    void parseArrayWithCodeBlock() {
        String text = "好的，以下是结果：\n```json\n[{\"jobId\":1,\"score\":90,\"reason\":\"匹配\"}]\n```\n希望有帮助";
        List<Map<String, Object>> list = parser.parseJsonArray(text);
        assertEquals(1, list.size());
        assertEquals(1L, Long.parseLong(list.get(0).get("jobId").toString()));
        assertEquals(90.0, Double.parseDouble(list.get(0).get("score").toString()));
    }

    @Test
    void parseArrayPlain() {
        String text = "[{\"jobId\":2,\"score\":80}]";
        List<Map<String, Object>> list = parser.parseJsonArray(text);
        assertEquals(1, list.size());
        assertEquals(2L, Long.parseLong(list.get(0).get("jobId").toString()));
    }

    @Test
    void parseObject() {
        String text = "{\"major\":\"计算机\",\"skills\":[\"Java\"],\"timePref\":[\"晚上\"]}";
        Map<String, Object> map = parser.parseJsonObject(text);
        assertEquals("计算机", map.get("major"));
        assertEquals(1, LlmJsonParser.strList(map, "skills").size());
    }

    @Test
    void parseFailureReturnsEmpty() {
        assertTrue(parser.parseJsonArray("抱歉，我无法回答").isEmpty());
        assertTrue(parser.parseJsonObject("").isEmpty());
        assertTrue(parser.parseJsonArray(null).isEmpty());
    }

    @Test
    void parseArrayWrappedInData() {
        String text = "{\"data\":[{\"jobId\":3,\"score\":70}]}";
        List<Map<String, Object>> list = parser.parseJsonArray(text);
        assertEquals(1, list.size());
        assertEquals(3L, Long.parseLong(list.get(0).get("jobId").toString()));
    }
}
