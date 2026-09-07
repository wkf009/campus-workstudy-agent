import axios from 'axios'
import { message } from 'ant-design-vue'
import { aiStart, aiDone } from './aiProgress.js'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// 判定是否为 AI 操作（走 /agent/** 的接口），用于驱动顶部 AI 进度条
const isAiRequest = (url = '') => /^\/?agent\//.test(url) || url.includes('/agent/')

request.interceptors.request.use(
  config => {
    if (isAiRequest(config.url)) aiStart()
    const token = sessionStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => Promise.reject(error)
)

request.interceptors.response.use(
  response => {
    if (isAiRequest(response.config.url)) aiDone()
    const data = response.data
    // 如果是文件下载等直接返回
    if (response.config.responseType === 'blob') {
      return response
    }
    // 统一处理后端Result格式
    if (data.code !== undefined) {
      if (data.code === 200) {
        return data
      }
      message.error(data.message || '请求失败')
      return Promise.reject(new Error(data.message || '请求失败'))
    }
    // 兼容老接口格式 (success/msg)
    if (data.success === false) {
      message.error(data.message || '操作失败')
      return Promise.reject(new Error(data.message || '操作失败'))
    }
    return data
  },
  error => {
    if (isAiRequest(error.config?.url)) aiDone()
    if (error.response) {
      const status = error.response.status
      if (status === 401) {
        sessionStorage.removeItem('token')
        sessionStorage.removeItem('user')
        window.location.href = '/'
        message.error('登录已过期，请重新登录')
      } else if (status === 403) {
        message.error('没有操作权限')
      } else {
        message.error(error.response.data?.message || `服务器错误 (${status})`)
      }
    } else if (error.code === 'ECONNABORTED') {
      // axios 超时（AI 接口含 LLM 调用，可能较慢）
      message.error('请求超时：AI 服务响应较慢，请稍后重试')
    } else {
      message.error('网络错误，请检查后端服务是否运行')
    }
    return Promise.reject(error)
  }
)

export default request

/**
 * 浏览器下载 Blob 文件（配合 axios responseType:'blob' 使用）。
 * 用于 CSV/文件导出：通过 axios 携带 Authorization 头，避免 window.open 丢失 token。
 */
export const downloadBlob = (blob, filename) => {
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}
