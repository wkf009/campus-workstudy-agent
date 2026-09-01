package com.workstudy.agent.analyst;

import com.workstudy.agent.common.LlmJsonParser;
import com.workstudy.llm.ChatService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalystAgentTest {

    @Mock
    private ChatService chatService;
    @Mock
    private LlmJsonParser jsonParser;

    @InjectMocks
    private AnalystAgent analystAgent;

    @Test
    void analyzeProducesStructuredResult() {
        when(chatService.chat(anyString(), anyString())).thenReturn("ok");
        when(jsonParser.parseJsonObject(anyString())).thenReturn(Map.of(
                "summary", "本周申请量环比上升 35%",
                "trends", List.of("图书馆岗位申请最多"),
                "anomalies", List.of("机房岗位 30 天无人申请"),
                "advice", "建议调整机房岗位薪资"
        ));

        AnalystAgent.AnalysisResult result = analystAgent.analyze(new HashMap<>());

        assertTrue(result.getSummary().contains("35%"));
        assertEquals(1, result.getTrends().size());
        assertEquals("建议调整机房岗位薪资", result.getAdvice());
    }

    @Test
    void analyzeFallsBackWhenLlmFails() {
        when(chatService.chat(anyString(), anyString())).thenThrow(new RuntimeException("LLM 不可用"));

        AnalystAgent.AnalysisResult result = analystAgent.analyze(new HashMap<>());

        assertNotNull(result.getSummary());
        assertTrue(result.getTrends().isEmpty());
    }
}
