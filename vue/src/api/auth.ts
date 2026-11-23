import { refreshAccess } from './request'
import { currentUser, type LoginUser } from './auth-state'

export async function ensureCurrentUser(): Promise<LoginUser> {
  if (currentUser.value) return currentUser.value
  await refreshAccess()
  if (!currentUser.value) throw new Error('刷新响应缺少用户信息，请重新登录')
  return currentUser.value
}
