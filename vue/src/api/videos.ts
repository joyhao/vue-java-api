import request from './request'

export interface VideoPageParams {
  pageIndex: number
  pageSize: number
  keyword: string
}

export type VideoItem = Record<string, unknown>

export interface VideoPage {
  list: VideoItem[]
  total: number
}

interface Result<T> {
  code: number
  message: string
  data: T | null
  timestamp: number
}

export const getVideos = (params: VideoPageParams) =>
  request
    .post<Result<VideoPage>>('/videos/page', params)
    .then((response) => response.data?.data ?? null)
