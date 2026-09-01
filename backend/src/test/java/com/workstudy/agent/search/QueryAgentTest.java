package com.workstudy.agent.search;

import com.workstudy.agent.common.LlmJsonParser;
import com.workstudy.llm.ChatService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueryAgentTest {

    @Mock
    private ChatService chatService;
    @Mock
    private LlmJsonParser jsonParser;

    @InjectMocks
    private QueryAgent queryAgent;

    @Test
    void parseStructuredQuery() {
        when(chatService.chat(anyString(), anyString())).thenReturn("ok");
        Map<String, Object> parsed = new HashMap<>();
        parsed.put("keyword", "兼职");
        parsed.put("location", "图书馆");
        parsed.put("timePref", "晚上");
        parsed.put("salaryMin", null);
        parsed.put("salaryMax", null);
        parsed.put("summary", "想找晚上能在图书馆做的兼职");
        when(jsonParser.parseJsonObject(anyString())).thenReturn(parsed);

        QueryAgent.QueryIntent intent = queryAgent.parse("晚上能在图书馆做的兼职");

        assertEquals("兼职", intent.getKeyword());
        assertEquals("图书馆", intent.getLocation());
        assertEquals("晚上", intent.getTimePref());
        assertTrue(intent.getSummary().contains("图书馆"));
    }

    @Test
    void parseFallsBackToWholeQuery() {
        when(chatService.chat(anyString(), anyString())).thenThrow(new RuntimeException("LLM 不可用"));

        QueryAgent.QueryIntent intent = queryAgent.parse("图书馆助理");

        assertEquals("图书馆助理", intent.getKeyword());
        assertNotNull(intent.getSummary());
    }

    @Test
    void parseSalaryNumbers() {
        when(chatService.chat(anyString(), anyString())).thenReturn("ok");
        Map<String, Object> parsed = new HashMap<>();
        parsed.put("keyword", "兼职");
        parsed.put("location", null);
        parsed.put("timePref", null);
        parsed.put("salaryMin", 20);
        parsed.put("salaryMax", 30);
        parsed.put("summary", "时薪 20-30 的兼职");
        when(jsonParser.parseJsonObject(anyString())).thenReturn(parsed);

        QueryAgent.QueryIntent intent = queryAgent.parse("20到30元一小时的兼职");

        assertEquals(20, intent.getSalaryMin());
        assertEquals(30, intent.getSalaryMax());
    }
}
