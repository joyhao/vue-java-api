import request from './request'

export const fetchTestData = () =>
  request.post<unknown>('/users/test').then((response) => response.data)
