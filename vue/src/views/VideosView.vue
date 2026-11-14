<script setup lang="ts">
import { computed, type CSSProperties, onMounted, reactive, ref } from 'vue'
import { getErrorMessage as messageOf } from '@/api/errors'
import {
  getVideoDuration,
  getVideos,
  type VideoItem,
  type VideoPage,
} from '@/api/videos'

interface VisibleCard {
  item: VideoItem
  index: number
  offset: number
}

const pageCache = ref<Map<number, VideoItem[]>>(new Map())
const pendingPages = new Set<number>()
const total = ref(0)
const loading = ref(false)
const error = ref('')
const query = reactive({ pageSize: 10, keyword: '' })
const rawPage = ref<VideoPage | null>(null)
const emptyResponse = ref(false)
const dialogVisible = ref(false)
const playerVisible = ref(false)
const playing = ref<VideoItem | null>(null)
const durationLoading = ref<Record<string, boolean>>({})
const durationFailed = ref<Record<string, boolean>>({})
const playerError = ref('')
const playerRef = ref<{ play: () => Promise<void> } | null>(null)
const activeGlobal = ref(0)
const hoveredId = ref<string | null>(null)

let lastWheelAt = 0

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / query.pageSize)))
const currentPage = computed(() => Math.floor(activeGlobal.value / query.pageSize) + 1)
const pageProgress = computed(() =>
  `${Math.max(6, Math.min(100, (currentPage.value / totalPages.value) * 100))}%`,
)
const canPrev = computed(() => !loading.value && total.value > 0 && activeGlobal.value > 0)
const canNext = computed(
  () => !loading.value && total.value > 0 && activeGlobal.value < total.value - 1,
)
const videos = computed(() =>
  [...pageCache.value.entries()]
    .sort((a, b) => a[0] - b[0])
    .flatMap(([, list]) => list),
)
const cardRadius = computed(() => Math.max(0, Math.min(4, Math.floor((total.value - 1) / 2))))

function wrapGlobal(globalIndex: number) {
  const count = total.value
  if (!count) return 0
  return (((globalIndex % count) + count) % count)
}

function itemAtGlobal(globalIndex: number): VideoItem | undefined {
  const pageNumber = Math.floor(globalIndex / query.pageSize) + 1
  return pageCache.value.get(pageNumber)?.[globalIndex % query.pageSize]
}

const visibleCards = computed<VisibleCard[]>(() => {
  const count = total.value
  if (!count) return []
  const radius = cardRadius.value
  const cards: VisibleCard[] = []
  for (let offset = -radius; offset <= radius; offset++) {
    const index = wrapGlobal(activeGlobal.value + offset)
    const item = itemAtGlobal(index)
    if (item) cards.push({ item, index, offset })
  }
  return cards
})

function proxiedUrl(url?: string) {
  if (!url) return ''
  const match = /^https?:\/\/[^/]+(\/.*)$/i.exec(url)
  return match ? match[1] : url
}

function isPlayable(size?: number) {
  return typeof size === 'number' && size > 0
}

function formatSize(bytes?: number) {
  if (!bytes || bytes <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1)
  return `${(bytes / 1024 ** index).toFixed(index === 0 ? 0 : 1)} ${units[index]}`
}

function formatDuration(seconds?: number | null) {
  if (seconds == null || !Number.isFinite(seconds) || seconds < 0) return '—'
  const totalSeconds = Math.floor(seconds)
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const rest = totalSeconds % 60
  return hours > 0
    ? `${hours}:${String(minutes).padStart(2, '0')}:${String(rest).padStart(2, '0')}`
    : `${minutes}:${String(rest).padStart(2, '0')}`
}

function cardStyle(card: VisibleCard): CSSProperties {
  const offset = card.offset
  const abs = Math.abs(offset)
  const dir = Math.sign(offset)
  const hovered = hoveredId.value === card.item.id
  const spread = dir * (abs * 158 - abs * (abs - 1) * 15)
  return {
    transform: `translateX(${spread}px) translateY(${abs * 18}px) rotate(${offset * 6}deg) scale(${Math.max(0.62, 1 - abs * 0.09)})`,
    zIndex: String(hovered ? 30 : 20 - abs),
    filter: hovered ? 'brightness(1)' : `brightness(${Math.max(0.72, 1 - abs * 0.06)})`,
    pointerEvents: 'auto',
  }
}

async function fetchPage(pageNumber: number) {
  if (pageNumber < 1 || pageNumber > totalPages.value) return
  if (pageCache.value.has(pageNumber) || pendingPages.has(pageNumber)) return
  pendingPages.add(pageNumber)
  try {
    const page = await getVideos({
      pageIndex: pageNumber,
      pageSize: query.pageSize,
      keyword: query.keyword,
    })
    rawPage.value = page
    if (pageNumber === 1) emptyResponse.value = page === null
    const list = Array.isArray(page?.list) ? page.list : []
    const nextTotal = Number(page?.total ?? 0)
    if (nextTotal > 0) total.value = nextTotal
    pageCache.value.set(pageNumber, list)
    error.value = ''
  } catch (exception) {
    error.value = messageOf(exception)
  } finally {
    pendingPages.delete(pageNumber)
  }
}

function neededPages() {
  const count = total.value
  if (!count) return new Set<number>()
  const radius = cardRadius.value
  const current = activeGlobal.value
  const globals = [
    wrapGlobal(current - radius),
    wrapGlobal(current),
    wrapGlobal(current + radius),
    wrapGlobal(current + query.pageSize),
    wrapGlobal(current - query.pageSize),
  ]
  return new Set(globals.map((globalIndex) => Math.floor(globalIndex / query.pageSize) + 1))
}

async function ensureAround() {
  const pages = neededPages()
  for (const pageNumber of [...pageCache.value.keys()]) {
    if (!pages.has(pageNumber)) pageCache.value.delete(pageNumber)
  }
  await Promise.all([...pages].map((pageNumber) => fetchPage(pageNumber)))
}

function setActive(globalIndex: number) {
  activeGlobal.value = Math.max(0, Math.min(total.value - 1, globalIndex))
  void ensureAround()
}

function prev() {
  if (!canPrev.value) return
  setActive(activeGlobal.value - 1)
}

function next() {
  if (!canNext.value) return
  setActive(activeGlobal.value + 1)
}

function onCardClick(card: VisibleCard) {
  if (card.offset !== 0) {
    setActive(card.index)
    return
  }
  next()
}

function onCardLeave(id: string) {
  if (hoveredId.value === id) hoveredId.value = null
}

function onWheel(event: { deltaY: number }) {
  if (Math.abs(event.deltaY) < 4) return
  const now = Date.now()
  if (now - lastWheelAt < 320) return
  lastWheelAt = now
  if (event.deltaY > 0) next()
  else prev()
}

function findItem(id: string): VideoItem | undefined {
  for (const list of [...pageCache.value.values()]) {
    const found = list.find((video) => video.id === id)
    if (found) return found
  }
  return undefined
}

async function loadDuration(id: string) {
  const item = findItem(id)
  if (!item || item.durationSeconds != null || durationLoading.value[id]) return
  durationLoading.value = { ...durationLoading.value, [id]: true }
  try {
    const seconds = await getVideoDuration(id)
    if (seconds != null) {
      item.durationSeconds = seconds
    } else {
      durationFailed.value = { ...durationFailed.value, [id]: true }
    }
  } catch {
    durationFailed.value = { ...durationFailed.value, [id]: true }
  } finally {
    const nextLoading = { ...durationLoading.value }
    delete nextLoading[id]
    durationLoading.value = nextLoading
  }
}

function openPlayer(id: string) {
  const item = findItem(id)
  if (!item) return
  playing.value = item
  playerError.value = ''
  playerVisible.value = true
}

function tryAutoPlay() {
  void playerRef.value?.play().catch(() => {})
}

function onPlayerError() {
  playerError.value = '视频加载失败：源文件可能为空、已损坏，或浏览器不支持该视频编码'
}

onMounted(async () => {
  loading.value = true
  await fetchPage(1)
  await ensureAround()
  loading.value = false
})
</script>

<template>
  <main class="videos-page">
    <header class="page-header">
      <div>
        <RouterLink to="/" class="back-link">← 返回工作台</RouterLink>
        <h1>视频列表</h1>
        <p>扇形堆叠卡片 · 滚轮或左右按钮翻页 · 点击卡片切换下一个视频</p>
      </div>
      <el-button :disabled="loading" @click="dialogVisible = true">查看响应</el-button>
    </header>

    <section class="panel">
      <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="hint" />
      <el-alert
        v-else-if="emptyResponse"
        title="接口当前返回空响应（后端 /videos/page 尚未返回数据），页面已按空列表展示。"
        type="info"
        show-icon
        :closable="false"
        class="hint"
      />

      <div
        v-loading="loading"
        class="stage"
        tabindex="0"
        @wheel.prevent="onWheel"
        @keydown.left.prevent="prev"
        @keydown.right.prevent="next"
      >
        <div v-if="!videos.length && !loading" class="stage-empty">暂无视频数据</div>

        <div class="deck">
          <article
            v-for="card in visibleCards"
            :key="card.item.id"
            class="video-card"
            :class="{ 'is-active': card.offset === 0 }"
            :style="cardStyle(card)"
            @mouseenter="hoveredId = card.item.id"
            @mouseleave="onCardLeave(card.item.id)"
            @click="onCardClick(card)"
          >
            <div class="card-cover">
              <img :src="proxiedUrl(card.item.thumbnailUrl)" :alt="card.item.name" loading="lazy" />
              <span v-if="!isPlayable(card.item.size)" class="card-badge">文件为空</span>
            </div>
            <div class="card-body">
              <h3 class="card-title" :title="card.item.name">{{ card.item.name }}</h3>
              <div class="card-meta">
                <span>{{ formatSize(card.item.size) }}</span>
                <span v-if="card.item.durationSeconds != null">
                  {{ formatDuration(card.item.durationSeconds) }}
                </span>
                <button
                  v-else
                  class="duration-btn"
                  type="button"
                  :disabled="!!durationLoading[card.item.id]"
                  @click.stop="loadDuration(card.item.id)"
                >
                  {{ durationFailed[card.item.id] ? '重试探测' : '获取时长' }}
                </button>
              </div>
              <div class="card-actions">
                <button
                  class="card-play"
                  type="button"
                  @click.stop="openPlayer(card.item.id)"
                >
                  播放
                </button>
              </div>
            </div>
          </article>
        </div>

        <button class="nav-btn nav-btn--prev" type="button" :disabled="!canPrev" @click="prev">‹</button>
        <button class="nav-btn nav-btn--next" type="button" :disabled="!canNext" @click="next">›</button>

        <div
          class="page-indicator"
          :aria-label="'当前第 ' + currentPage + ' 页，共 ' + totalPages + ' 页'"
        >
          <div class="page-indicator__row">
            <span class="page-indicator__label">当前页</span>
            <span class="page-indicator__current">{{ currentPage }}</span>
            <span class="page-indicator__slash">/</span>
            <span class="page-indicator__total">{{ totalPages }}</span>
            <span class="page-indicator__label">总页数</span>
          </div>
          <div class="page-indicator__track">
            <span class="page-indicator__fill" :style="{ width: pageProgress }" />
          </div>
        </div>
      </div>
    </section>

    <el-dialog v-model="dialogVisible" title="接口原始响应" width="min(640px, 94vw)">
      <pre class="raw">{{ rawPage ? JSON.stringify(rawPage, null, 2) : '（空响应）' }}</pre>
    </el-dialog>

    <el-dialog
      v-model="playerVisible"
      :title="playing?.name ?? '视频播放'"
      width="min(920px, 94vw)"
      destroy-on-close
      @opened="tryAutoPlay"
    >
      <video
        v-if="playing"
        ref="playerRef"
        class="player"
        controls
        preload="metadata"
        :src="playing.videoUrl"
        @error="onPlayerError"
      />
      <el-alert v-if="playerError" :title="playerError" type="error" show-icon :closable="false" />
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
  max-width: 1420px;
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
  padding: 16px;
  border: 1px solid rgba(224, 231, 225, 0.9);
  border-radius: 22px;
  box-shadow: 0 12px 34px rgba(31, 45, 39, 0.06);
}
.hint {
  flex-shrink: 0;
  margin-bottom: 16px;
  border-radius: 12px;
}
.stage {
  position: relative;
  flex: 1;
  min-height: 520px;
  overflow: hidden;
  border: 1px solid #e8eef0;
  border-radius: 20px;
  outline: none;
  background:
    radial-gradient(circle at 50% 0%, rgba(147, 197, 253, 0.28), transparent 58%),
    linear-gradient(170deg, #f8fbff 0%, #eef5f1 100%);
  &:focus-visible {
    border-color: rgba(59, 130, 246, 0.55);
  }
}
.stage-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  color: var(--videos-muted);
}
.deck {
  position: absolute;
  inset: 0;
  transition: transform 0.3s ease;
}
.video-card {
  position: absolute;
  top: 50%;
  left: 50%;
  display: flex;
  flex-direction: column;
  width: 360px;
  height: 452px;
  margin: -256px 0 0 -180px;
  overflow: hidden;
  background: #fff;
  border: 1px solid rgba(255, 255, 255, 0.95);
  border-radius: 22px;
  box-shadow: 0 18px 40px rgba(31, 45, 39, 0.16);
  transform-origin: center 86%;
  transition:
    transform 0.45s cubic-bezier(0.22, 0.68, 0, 1),
    opacity 0.35s ease,
    filter 0.35s ease,
    box-shadow 0.3s ease,
    border-color 0.3s ease;
  cursor: pointer;
  will-change: transform;
  &.is-active {
    border-color: rgba(59, 130, 246, 0.42);
    box-shadow: 0 30px 64px rgba(31, 45, 39, 0.26);
  }
  &:not(.is-active) {
    .card-body {
      pointer-events: none;
    }
    .card-play {
      pointer-events: auto;
    }
  }
  &:hover {
    border-color: rgba(59, 130, 246, 0.72);
    box-shadow: 0 36px 76px rgba(31, 45, 39, 0.34);
    .card-cover img {
      transform: scale(1.06);
    }
  }
}
.card-cover {
  position: relative;
  height: 268px;
  flex-shrink: 0;
  overflow: hidden;
  background: #e9eef0;
  img {
    display: block;
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform 0.45s ease;
  }
  &::after {
    content: '';
    position: absolute;
    inset: 0;
    z-index: 1;
    background: linear-gradient(180deg, transparent 58%, rgba(9, 22, 18, 0.32));
    pointer-events: none;
  }
}
.card-badge {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 2;
  padding: 5px 12px;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  background: rgba(220, 38, 38, 0.88);
  border-radius: 999px;
}
.card-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: auto;
}
.card-play {
  flex: 1;
  padding: 10px 18px;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.02em;
  background: linear-gradient(100deg, #2563eb, #0d9488);
  border: 0;
  border-radius: 10px;
  box-shadow: 0 6px 16px rgba(37, 99, 235, 0.24);
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, filter 0.2s ease;
  &:hover {
    filter: brightness(1.08);
    transform: translateY(-2px);
    box-shadow: 0 10px 22px rgba(37, 99, 235, 0.32);
  }
}
.card-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 18px 20px 20px;
  min-height: 0;
}
.card-title {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: var(--videos-ink);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.card-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: var(--videos-muted);
}
.duration-btn {
  padding: 0;
  color: var(--el-color-primary);
  font-size: 13px;
  background: none;
  border: 0;
  cursor: pointer;
  &:disabled {
    opacity: 0.55;
    cursor: default;
  }
}
.nav-btn {
  position: absolute;
  top: 50%;
  z-index: 30;
  width: 52px;
  height: 52px;
  margin-top: -26px;
  color: #3b82f6;
  font-size: 34px;
  line-height: 1;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(224, 231, 225, 0.95);
  border-radius: 50%;
  box-shadow: 0 8px 22px rgba(31, 45, 39, 0.12);
  cursor: pointer;
  transition: transform 0.2s ease, opacity 0.2s ease, background 0.2s ease;
  &:hover:not(:disabled) {
    transform: scale(1.1);
    background: #fff;
    box-shadow: 0 12px 28px rgba(59, 130, 246, 0.24);
  }
  &:disabled {
    opacity: 0.35;
    cursor: not-allowed;
  }
}
.nav-btn--prev {
  left: 20px;
}
.nav-btn--next {
  right: 20px;
}
.page-indicator {
  position: absolute;
  bottom: 20px;
  left: 50%;
  z-index: 25;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  transform: translateX(-50%);
  white-space: nowrap;
  pointer-events: none;
  user-select: none;
}
.page-indicator__row {
  display: flex;
  align-items: baseline;
  gap: 9px;
  font-variant-numeric: tabular-nums;
  line-height: 1;
}
.page-indicator__label {
  font-size: 11px;
  letter-spacing: 0.08em;
  color: #87978f;
}
.page-indicator__current {
  font-size: 25px;
  font-weight: 750;
  letter-spacing: -0.03em;
  color: #1f3b5c;
  background: linear-gradient(100deg, #16304d, #2563eb 60%, #0d9488);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}
.page-indicator__slash {
  font-size: 14px;
  color: #c3cdd0;
}
.page-indicator__total {
  font-size: 15px;
  font-weight: 650;
  color: #8b9a94;
}
.page-indicator__track {
  width: 168px;
  height: 4px;
  overflow: hidden;
  background: rgba(31, 45, 39, 0.13);
  border-radius: 999px;
}
.page-indicator__fill {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #3b82f6, #14b8a6);
  border-radius: 999px;
  transition: width 0.45s ease;
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
.player {
  display: block;
  width: 100%;
  max-height: 70vh;
  background: #000;
  border-radius: 12px;
}
@media (max-width: 1200px) {
  .deck {
    transform: scale(0.8);
  }
}
@media (max-width: 900px) {
  .deck {
    transform: scale(0.66);
  }
}
@media (max-width: 640px) {
  .videos-page {
    padding: 16px 12px 24px;
  }
  .page-header {
    padding: 16px;
  }
  .panel {
    padding: 12px;
  }
  .stage {
    min-height: 400px;
  }
  .deck {
    transform: scale(0.48);
  }
  .nav-btn {
    width: 40px;
    height: 40px;
    margin-top: -20px;
    font-size: 26px;
  }
  .nav-btn--prev {
    left: 10px;
  }
  .nav-btn--next {
    right: 10px;
  }
}
</style>
