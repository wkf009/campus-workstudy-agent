<template>
  <div v-if="aiBusy" class="ai-progress-fixed">
    <div class="ai-progress-track">
      <div class="ai-progress-bar"></div>
    </div>
    <span class="ai-progress-label">AI 处理中…</span>
  </div>
</template>

<script>
import { aiBusy } from '../utils/aiProgress.js'
export default {
  name: 'AiProgressBar',
  setup() {
    return { aiBusy }
  }
}
</script>

<style scoped>
.ai-progress-fixed { position: fixed; top: 0; left: 0; right: 0; z-index: 2000; pointer-events: none; }
.ai-progress-track { position: relative; height: 3px; background: rgba(24, 144, 255, 0.12); overflow: hidden; }
.ai-progress-bar { position: absolute; top: 0; left: 0; height: 100%; width: 40%; border-radius: 2px; background: linear-gradient(90deg, #1890ff, #69c0ff, #1890ff); animation: ai-progress-slide 1.2s ease-in-out infinite; }
.ai-progress-label { position: fixed; top: 6px; right: 12px; font-size: 12px; color: #1890ff; background: #e6f7ff; padding: 1px 8px; border-radius: 10px; border: 1px solid #91d5ff; }
@keyframes ai-progress-slide {
  0% { transform: translateX(-100%); }
  100% { transform: translateX(350%); }
}
</style>