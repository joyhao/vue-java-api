<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { getErrorMessage as messageOf } from '@/api/errors'
import { getVideos, type VideoItem, type VideoPage } from '@/api/videos'

const videos = ref<VideoItem[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const keyword = ref('')
const query = reactive({ pageIndex: 1, pageSize: 10, keyword: '' })
const rawPage = ref<VideoPage | null>(null)
const emptyResponse = ref(false)
const dialogVisible = ref(false)

const LABELS: Record<string, string> = {
  id: 'ID',
  title: '标题',
  category: '分类',
  duration: '时长（秒）',
  durationSeconds: '时长（秒）',
  views: '播放量',
  playCount: '播放量',
  coverUrl: '封面地址',
  author: '作者',
  createdAt: '创建时间',
  updatedAt: '更新时间',
}

const columns = computed(() => {
  const keys = new Set<string>()
  videos.value.forEach((item) => Object.keys(item).forEach((key) => keys.add(key)))
  return [...keys]
})

function labelOf(key: string) {
  return LABELS[key] ?? key
}

function cellOf(row: VideoItem, key: string) {
  const value = row[key]
  if (value === null || value === undefined || value === '') return '—'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

async function loadVideos() {
  loading.value = true
  error.value = ''
  try {
    const page = await getVideos({ ...query })
    rawPage.value = page
    emptyResponse.value = page === null
    videos.value = Array.isArray(page?.list) ? page.list : []
    total.value = Number(page?.total ?? 0)
  } catch (exception) {
    error.value = messageOf(exception)
    rawPage.value = null
    videos.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  query.keyword = keyword.value.trim()
  query.pageIndex = 1
  void loadVideos()
}

function resizePage() {
  query.pageIndex = 1
  void loadVideos()
}

onMounted(loadVideos)
</script>

<template>
  <main class="videos-page">
    <header class="page-header">
      <div>
        <RouterLink to="/" class="back-link">← 返回工作台</RouterLink>
        <h1>视频列表</h1>
        <p>调用 POST /api/videos/page，按接口返回的字段自动展示</p>
      </div>
      <el-button :disabled="loading" @click="dialogVisible = true">查看响应</el-button>
    </header>

    <section class="panel">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索关键字"
          clearable
          maxlength="100"
          :disabled="loading"
          @keyup.enter="search"
          @clear="search"
        />
        <el-button :disabled="loading" @click="search">查询</el-button>
        <el-button :disabled="loading" @click="loadVideos">刷新</el-button>
        <div class="toolbar__spacer" />
        <span class="counter">共 {{ total }} 条</span>
      </div>

      <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="hint" />
      <el-alert
        v-else-if="emptyResponse"
        title="接口当前返回空响应（后端 /videos/page 尚未返回数据），页面已按空列表展示。"
        type="info"
        show-icon
        :closable="false"
        class="hint"
      />

      <div class="table-container">
        <el-table v-loading="loading" height="100%" :data="videos" empty-text="暂无视频数据">
          <el-table-column type="index" label="序号" width="72" />
          <el-table-column
            v-for="key in columns"
            :key="key"
            :prop="key"
            :label="labelOf(key)"
            min-width="160"
            show-overflow-tooltip
          >
            <template #default="{ row }">{{ cellOf(row, key) }}</template>
          </el-table-column>
        </el-table>
      </div>

      <div class="pagination">
        <el-pagination
          v-model:current-page="query.pageIndex"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          :disabled="loading"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="loadVideos"
          @size-change="resizePage"
        />
      </div>
    </section>

    <el-dialog v-model="dialogVisible" title="接口原始响应" width="min(640px, 94vw)">
      <pre class="raw">{{ rawPage ? JSON.stringify(rawPage, null, 2) : '（空响应）' }}</pre>
    </el-dialog>
  </main>
</template>

<style scoped lang="scss">
.videos-page {
  --videos-ink: #1f2d27;
  --videos-muted: #78867f;

  display: flex;
  flex-direction: column;
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
  max-width: 1320px;
  margin: 0 auto;
  padding: 24px 24px 28px;
  background:
    radial-gradient(circle at 8% -6%, rgba(147, 197, 253, 0.24), transparent 40%),
    radial-gradient(circle at 96% 2%, rgba(167, 243, 208, 0.28), transparent 38%),
    linear-gradient(160deg, #f4f8f6 0%, #f7f9ff 55%, #fdf8f2 100%);
}
.page-header {
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  margin-bottom: 18px;
  padding: 18px 24px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.92);
  border-radius: 20px;
  box-shadow: 0 10px 30px rgba(31, 45, 39, 0.05);
  backdrop-filter: blur(10px);
  h1 {
    margin: 8px 0 6px;
    font-size: 27px;
    font-weight: 750;
    letter-spacing: -0.02em;
    color: var(--videos-ink);
    background: linear-gradient(100deg, #1f3b5c, #3b82f6 55%, #14b8a6);
    -webkit-background-clip: text;
    background-clip: text;
    -webkit-text-fill-color: transparent;
  }
  p {
    margin: 0;
    font-size: 13px;
    color: var(--videos-muted);
  }
}
.back-link {
  color: var(--el-color-primary);
  font-size: 13px;
  text-decoration: none;
  &:hover {
    text-decoration: underline;
  }
}
.panel {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  background: #fff;
  padding: 22px 24px;
  border: 1px solid rgba(224, 231, 225, 0.9);
  border-radius: 22px;
  box-shadow: 0 12px 34px rgba(31, 45, 39, 0.06);
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 18px;
  padding-bottom: 18px;
  border-bottom: 1px solid #eef2ef;
  .el-input {
    width: 260px;
  }
  .el-button + .el-button {
    margin-left: 0;
  }
}
.toolbar__spacer {
  flex: 1;
}
.counter {
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 600;
  color: #3f6b8c;
  background: #f0f6ff;
  border-radius: 999px;
  white-space: nowrap;
}
.table-container {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  border: 1px solid #eef2ef;
  border-radius: 14px;

  :deep(.el-table__header th) {
    font-size: 12px;
    font-weight: 700;
    color: #5b6b63;
    background: #f7faf8;
  }
  :deep(.el-table__row:hover > td.el-table__cell) {
    background: #f8fbff;
  }
}
.toolbar,
.hint,
.pagination {
  flex-shrink: 0;
}
.hint {
  margin-bottom: 16px;
  border-radius: 12px;
}
.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
  overflow-x: auto;
}
.raw {
  margin: 0;
  padding: 16px;
  max-height: 60vh;
  overflow: auto;
  color: #a7f3d0;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-all;
  background: #12251f;
  border-radius: 14px;
}
@media (max-width: 640px) {
  .videos-page {
    padding: 16px 12px 24px;
  }
  .page-header {
    padding: 16px;
  }
  .panel {
    padding: 16px;
  }
  .toolbar .el-input {
    width: 100%;
  }
  .toolbar__spacer {
    display: none;
  }
  .pagination {
    justify-content: flex-start;
  }
}
</style>
