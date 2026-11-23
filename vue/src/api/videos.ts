import request from './request'

export interface VideoPageParams {
  pageIndex: number
  pageSize: number
  keyword: string
}

export interface VideoItem {
  id: string
  name: string
  size: number
  durationSeconds: number | null
  thumbnailUrl: string
  videoUrl: string
}

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

export const getVideoDuration = (id: string) =>
  request
    .get<Result<number>>(`/videos/${id}/duration`)
    .then((response) => response.data?.data ?? null)

export interface VideoSyncResult {
  scanned: number
  inserted: number
  updated: number
  removed: number
  total: number
}

export const syncVideos = () =>
  request
    .post<Result<VideoSyncResult>>('/videos/sync', undefined, { timeout: 120000 })
    .then((response) => response.data?.data ?? null)
