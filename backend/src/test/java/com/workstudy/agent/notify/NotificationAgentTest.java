package com.workstudy.agent.notify;

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
class NotificationAgentTest {

    @Mock
    private ChatService chatService;
    @Mock
    private LlmJsonParser jsonParser;

    @InjectMocks
    private NotificationAgent notificationAgent;

    @Test
    void generateProducesPersonalizedDraft() {
        when(chatService.chat(anyString(), anyString())).thenReturn("ok");
        when(jsonParser.parseJsonObject(anyString())).thenReturn(Map.of(
                "title", "为你找到更合适的岗位",
                "content", "你申请的《机房值班》未通过，AI 为你推荐了《图书馆助理》，可在申请页一键转投。"
        ));

        NotificationAgent.NotificationDraft draft =
                notificationAgent.generate("APPLICATION_REMATCH", new HashMap<>());

        assertNotNull(draft);
        assertTrue(draft.getTitle().contains("岗位"));
        assertTrue(draft.getContent().contains("一键转投"));
    }

    @Test
    void generateFallsBackToNullWhenLlmFails() {
        when(chatService.chat(anyString(), anyString())).thenThrow(new RuntimeException("LLM 不可用"));

        NotificationAgent.NotificationDraft draft =
                notificationAgent.generate("APPLICATION_REMATCH", new HashMap<>());

        assertNull(draft); // 调用方回退模板
    }

    @Test
    void generateRematchHelper() {
        when(chatService.chat(anyString(), anyString())).thenReturn("ok");
        when(jsonParser.parseJsonObject(anyString())).thenReturn(Map.of(
                "title", "AI 撮合推荐",
                "content", "替代岗位：图书馆助理、复印室助理"
        ));

        NotificationAgent.NotificationDraft draft =
                notificationAgent.generateRematch("张三", "机房值班", "图书馆助理、复印室助理");

        assertNotNull(draft);
        assertTrue(draft.getContent().contains("图书馆助理"));
    }
}
