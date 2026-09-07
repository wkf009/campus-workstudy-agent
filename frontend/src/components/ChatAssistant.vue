<template>
  <div class="chat-assistant">
    <a-button v-if="!open" class="chat-fab" type="primary" shape="circle" size="large" @click="openPanel">🤖</a-button>
    <div v-else class="chat-panel">
      <div class="chat-header">
        <span>🤖 AI 求职助手</span>
        <a-button type="text" size="small" @click="open = false">✕</a-button>
      </div>
      <div class="chat-body" ref="bodyRef">
        <div v-if="messages.length === 0" class="chat-welcome">
          你好，我是求职助手～<br />可以帮你：找岗位 / 查申请 / 直接申请<br />试试说"帮我找晚上能做的兼职"
        </div>
        <div v-for="(m, i) in messages" :key="i" :class="['chat-msg', m.role]">
          <div class="bubble" v-html="escapeHtml(m.content)"></div>
        </div>
        <div v-if="thinking" class="chat-msg assistant"><div class="bubble thinking">思考中...</div></div>
      </div>
      <div class="chat-input">
        <a-input v-model:value="input" placeholder="输入你的问题..." @pressEnter="send" :disabled="thinking" />
        <a-button type="primary" :loading="thinking" @click="send" :disabled="!input.trim()">发送</a-button>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, nextTick } from 'vue'
import { aiStart, aiDone } from '../utils/aiProgress.js'

export default {
  name: 'ChatAssistant',
  setup() {
    const open = ref(false)
    const input = ref('')
    const thinking = ref(false)
    const messages = ref([])
    const bodyRef = ref(null)

    const openPanel = () => {
      open.value = true
      scrollToBottom()
    }

    const scrollToBottom = async () => {
      await nextTick()
      if (bodyRef.value) bodyRef.value.scrollTop = bodyRef.value.scrollHeight
    }

    // 简单 HTML 转义，防止 LLM 输出注入
    const escapeHtml = (s) => (s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/\n/g, '<br/>')

    const send = async () => {
      const text = input.value.trim()
      if (!text || thinking.value) return
      input.value = ''
      messages.value.push({ role: 'user', content: text })
      messages.value.push({ role: 'assistant', content: '' })
      thinking.value = true
      aiStart()
      scrollToBottom()

      try {
        const token = sessionStorage.getItem('token')
        const response = await fetch('/api/agent/chat', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`
          },
          body: JSON.stringify({ message: text })
        })
        if (!response.ok || !response.body) throw new Error('请求失败')

        const reader = response.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''
        const last = messages.value[messages.value.length - 1]

        while (true) {
          const { done, value } = await reader.read()
          if (done) break
          buffer += decoder.decode(value, { stream: true })
          let idx
          while ((idx = buffer.indexOf('\n\n')) >= 0) {
            const raw = buffer.slice(0, idx)
            buffer = buffer.slice(idx + 2)
            let event = 'message'
            let data = ''
            for (const line of raw.split('\n')) {
              if (line.startsWith('event:')) event = line.slice(6).trim()
              else if (line.startsWith('data:')) data += line.slice(5).trim()
            }
            if (event === 'delta') {
              last.content += data
              scrollToBottom()
            } else if (event === 'error') {
              last.content = data
              scrollToBottom()
            }
          }
        }
        if (!last.content) last.content = '（无回复）'
      } catch (e) {
        const last = messages.value[messages.value.length - 1]
        last.content = '网络错误或服务不可用，请确认后端已启动并配置通义千问 API Key'
      } finally {
        thinking.value = false
        aiDone()
        scrollToBottom()
      }
    }

    return { open, input, thinking, messages, bodyRef, openPanel, send, escapeHtml }
  }
}
</script>

<style scoped>
.chat-assistant { position: fixed; right: 24px; bottom: 24px; z-index: 999; }
.chat-fab { width: 56px; height: 56px; font-size: 24px; box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2); }
.chat-panel { width: 380px; height: 520px; background: #fff; border-radius: 12px; box-shadow: 0 8px 32px rgba(0, 0, 0, 0.18); display: flex; flex-direction: column; overflow: hidden; }
.chat-header { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; background: linear-gradient(135deg, #1890ff, #36cfc9); color: #fff; font-weight: 600; }
.chat-body { flex: 1; overflow-y: auto; padding: 14px; background: #f7f9fc; }
.chat-welcome { text-align: center; color: #999; font-size: 13px; line-height: 1.8; margin-top: 40px; }
.chat-msg { display: flex; margin-bottom: 10px; }
.chat-msg.user { justify-content: flex-end; }
.chat-msg.assistant { justify-content: flex-start; }
.bubble { max-width: 82%; padding: 9px 12px; border-radius: 10px; font-size: 14px; line-height: 1.6; word-break: break-word; }
.chat-msg.user .bubble { background: #1890ff; color: #fff; border-top-right-radius: 2px; }
.chat-msg.assistant .bubble { background: #fff; color: #333; border: 1px solid #e8e8e8; border-top-left-radius: 2px; }
.bubble.thinking { color: #999; }
.chat-input { display: flex; gap: 8px; padding: 10px 12px; border-top: 1px solid #eee; background: #fff; }
</style>
