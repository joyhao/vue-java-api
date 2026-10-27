import { isAxiosError } from 'axios'

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

export function getErrorMessage(exception: unknown): string {
  if (!isAxiosError<unknown>(exception)) return '操作失败，请稍后重试。'
  const data = exception.response?.data
  if (isRecord(data)) {
    const errors = isRecord(data.data) ? data.data.errors : data.errors
    if (isRecord(errors)) {
      const messages = Object.values(errors).flatMap((value) =>
        Array.isArray(value)
          ? value.filter((item): item is string => typeof item === 'string')
          : [],
      )
      if (messages.length) return messages.join('；')
    }
    if (typeof data.message === 'string' && data.message) return data.message
    if (typeof data.detail === 'string' && data.detail) return data.detail
    if (typeof data.title === 'string' && data.title) return data.title
  }
  return '请求失败，请确认服务可用后重试。'
}
