import request from './request'

export interface UserInput {
  name: string
  avatarUrl: string | null
  email: string | null
  phone: string | null
  status: 0 | 1
}

export interface User extends UserInput {
  id: string
  createdAt: string
  updatedAt: string
}

export interface UserPage {
  items: User[]
  total: number
  pageNumber: number
  pageSize: number
}

export const getUsers = (params: { pageNumber: number; pageSize: number; keyword: string }) =>
  request.get<UserPage>('/users', { params }).then((response) => response.data)
export const createUser = (input: UserInput) =>
  request.post<User>('/users', input).then((response) => response.data)
export const updateUser = (id: string, input: UserInput) =>
  request.put<User>(`/users/${id}`, input).then((response) => response.data)
export const deleteUser = (id: string) =>
  request.delete<{ deletedCount: number }>(`/users/${id}`).then((response) => response.data)
export const deleteUsers = (ids: string[]) =>
  request
    .post<{ deletedCount: number }>('/users/batch-delete', { ids })
    .then((response) => response.data)
export const deleteAllUsers = (confirmation: string) =>
  request
    .post<{ deletedCount: number }>('/users/delete-all', { confirmation })
    .then((response) => response.data)
