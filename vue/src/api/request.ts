import axios, { type InternalAxiosRequestConfig } from 'axios'

const options = {
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
  headers: { 'X-Requested-With': 'joykit' },
}
const request = axios.create(options)
const refreshClient = axios.create(options)
let refreshing: Promise<unknown> | null = null
let accessToken: string | null = null

request.interceptors.request.use(async (config) => {
  const publicRequest = /\/auth\/(login|register|refresh)(?:\?|$)/.test(config.url || '')
  if (!accessToken && !publicRequest) await refreshAccess()
  if (accessToken) config.headers.set('Authorization', `Bearer ${accessToken}`)
  else config.headers.delete('Authorization')
  return config
})

request.interceptors.response.use(
  (response) => {
    if (/\/auth\/(login|refresh)(?:\?|$)/.test(response.config.url || '')) {
      accessToken = response.data?.data?.token || null
    }
    if (/\/auth\/logout(?:\?|$)/.test(response.config.url || '')) accessToken = null
    return response
  },
  async (error) => {
    const config = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined
    const publicRequest = /\/auth\/(login|register|refresh)(?:\?|$)/.test(config?.url || '')
    if (error.response?.status !== 401 || !config || publicRequest) return Promise.reject(error)
    if (!config._retried) {
      config._retried = true
      try {
        await refreshAccess()
      } catch (refreshError) {
        if (axios.isAxiosError(refreshError) && refreshError.response?.status === 401) redirectToLogin()
        return Promise.reject(refreshError)
      }
      return request(config)
    }
    redirectToLogin()
    return Promise.reject(error)
  },
)

function refreshAccess() {
  if (!refreshing) {
    refreshing = refreshClient.post('/auth/refresh').then((response) => {
      accessToken = response.data?.data?.token || null
      if (!accessToken) throw new Error('刷新响应缺少访问凭证')
    }).catch((error) => {
      accessToken = null
      throw error
    }).finally(() => { refreshing = null })
  }
  return refreshing
}

function redirectToLogin() {
  if (['/login', '/register'].includes(window.location.pathname)) return
  const redirect = window.location.pathname + window.location.search + window.location.hash
  window.location.replace(`/login?redirect=${encodeURIComponent(redirect)}`)
}

export default request
