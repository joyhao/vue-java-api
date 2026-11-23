import request from './request'
import { currentUser, type LoginUser } from './auth-state'

let pending: Promise<LoginUser> | null = null

export function ensureCurrentUser(): Promise<LoginUser> {
  if (currentUser.value) return Promise.resolve(currentUser.value)
  if (!pending) {
    pending = request.get<{ data: LoginUser }>('/auth/me')
      .then((response) => {
        currentUser.value = response.data.data
        return response.data.data
      })
      .finally(() => { pending = null })
  }
  return pending
}
