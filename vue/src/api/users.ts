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
  list: User[]
  total: number
}

interface Result<T> {
  code: number
  message: string
  data: T
  timestamp: number
}

export const getUser = (id: string) =>
  request.get<Result<User>>(`/users/${id}`).then((response) => response.data.data)

export const getUsers = (params: { pageNumber: number; pageSize: number; keyword: string }) =>
  request
    .post<Result<UserPage>>('/users/page', {
      pageIndex: params.pageNumber,
      pageSize: params.pageSize,
      keyword: params.keyword,
    })
    .then((response) => response.data.data)
export const createUser = (input: UserInput) =>
  request.post<Result<User>>('/users', input).then((response) => response.data.data)
export const updateUser = (id: string, input: UserInput) =>
  request.put<Result<User>>(`/users/${id}`, input).then((response) => response.data.data)
export const deleteUser = (id: string) =>
  request
    .delete<Result<{ deletedCount: number }>>(`/users/${id}`)
    .then((response) => response.data.data)
export const deleteUsers = (ids: string[]) =>
  request
    .post<Result<{ deletedCount: number }>>('/users/batch-delete', { ids })
    .then((response) => response.data.data)
export const deleteAllUsers = (confirmation: string) =>
  request
    .post<Result<{ deletedCount: number }>>('/users/delete-all', { confirmation })
    .then((response) => response.data.data)
