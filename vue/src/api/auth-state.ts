import { shallowRef } from 'vue'

export interface LoginUser {
  id: string
  account: string
  name: string
  clientType: 'WEB' | 'APP'
}

export const currentUser = shallowRef<LoginUser | null>(null)
