import { ref, computed } from 'vue'

/**
 * 全局 AI 进度状态（顶部进度条驱动）。
 * 所有 AI 操作（/agent/** 请求、AI 对话等）通过 aiStart/aiDone 维护一个活跃计数：
 *   active > 0   → 显示顶部"AI 处理中"线性进度条
 *   active == 0  → 隐藏
 * 支持并发：多个 AI 操作同时进行时，最后一个结束才隐藏。
 */
export const aiActive = ref(0)

/** 是否有任意 AI 操作进行中 */
export const aiBusy = computed(() => aiActive.value > 0)

/** AI 操作开始：活跃计数 +1 */
export function aiStart() {
  aiActive.value++
}

/** AI 操作结束：活跃计数 -1（不低于 0） */
export function aiDone() {
  if (aiActive.value > 0) aiActive.value--
}

/** 便捷包装：让一个 Promise 执行期间维持进度条（成功或失败都会结束） */
export async function withAi(promise) {
  aiStart()
  try {
    return await promise
  } finally {
    aiDone()
  }
}