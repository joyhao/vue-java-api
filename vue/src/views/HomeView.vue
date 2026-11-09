<script setup lang="ts">
/* global console */
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getErrorMessage as messageOf } from '@/api/errors'
import { fetchTestData } from '@/api/test'

const testing = ref(false)
const testResult = ref('')

async function runTest() {
  testing.value = true
  testResult.value = ''
  try {
    const data = await fetchTestData()
    console.log('[POST /api/users/test] 返回数据:', data)
    testResult.value = JSON.stringify(data, null, 2) ?? ''
    ElMessage.success('请求成功，返回数据已输出到控制台')
  } catch (exception) {
    console.error('[POST /api/users/test] 请求失败:', exception)
    testResult.value = messageOf(exception)
    ElMessage.error(messageOf(exception))
  } finally {
    testing.value = false
  }
}
</script>

<template>
  <main class="home">
    <header class="hero">
      <div class="hero__content">
        <span class="hero__badge"><i class="hero__badge-dot" />工作台 · Workspace</span>
        <h1>joykit</h1>
        <p>一个轻量的前后端练习工作台，集中管理用户、视频内容与接口调试。</p>
        <div class="hero__meta">
          <span>Vue 3 · Element Plus</span>
          <span>Spring Boot · PostgreSQL</span>
        </div>
      </div>
      <div class="hero__art" aria-hidden="true">
        <div class="hero__orb" />
        <div class="hero__card hero__card--back" />
        <div class="hero__card hero__card--front">
          <span class="hero__bar" />
          <span class="hero__bar hero__bar--short" />
          <span class="hero__block" />
        </div>
      </div>
    </header>

    <section class="section" aria-label="功能列表">
      <div class="section__head">
        <h2>功能导航</h2>
        <span>选择模块开始使用</span>
      </div>
      <div class="grid">
        <RouterLink to="/users" class="feature feature--blue">
          <div class="feature__top">
            <span class="feature__icon">
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
              >
                <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
                <circle cx="9" cy="7" r="4" />
                <path d="M22 21v-2a4 4 0 0 0-3-3.87" />
                <path d="M16 3.13a4 4 0 0 1 0 7.75" />
              </svg>
            </span>
            <span class="feature__arrow">↗</span>
          </div>
          <h3>用户管理</h3>
          <p>维护用户资料与状态，支持分页查询、编辑和批量操作。</p>
          <span class="feature__cta">进入用户管理</span>
        </RouterLink>

        <RouterLink to="/videos" class="feature feature--teal">
          <div class="feature__top">
            <span class="feature__icon">
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
              >
                <rect x="2.5" y="5" width="14" height="14" rx="3" />
                <path d="m17 10 4.5-2.5v9L17 14" />
                <path d="m8.5 9.5 4 2.5-4 2.5z" />
              </svg>
            </span>
            <span class="feature__arrow">↗</span>
          </div>
          <h3>视频列表</h3>
          <p>按接口返回字段自动展示视频数据，支持关键字搜索与分页。</p>
          <span class="feature__cta">进入视频列表</span>
        </RouterLink>
      </div>
    </section>

    <section class="section" aria-label="接口测试">
      <div class="section__head">
        <h2>接口调试</h2>
        <span>POST /api/users/test</span>
      </div>
      <div class="test-panel">
        <span class="test-panel__icon">
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <rect x="2.5" y="4" width="19" height="16" rx="3" />
            <path d="m7 9 3 3-3 3" />
            <path d="M13 15h4" />
          </svg>
        </span>
        <div class="test-panel__body">
          <h3>连通性检查</h3>
          <p>调用测试接口，返回数据会打印到浏览器控制台，并展示在下方。</p>
          <div class="test-panel__actions">
            <el-button type="primary" :loading="testing" @click="runTest">运行测试</el-button>
            <span v-if="testResult" class="test-panel__status">已完成</span>
          </div>
          <pre v-if="testResult" class="test-panel__result">{{ testResult }}</pre>
        </div>
      </div>
    </section>

    <footer class="home__footer">joykit · 前后端练习项目</footer>
  </main>
</template>

<style scoped lang="scss">
.home {
  --home-ink: #1f2d27;
  --home-muted: #78867f;

  min-height: 100vh;
  min-height: 100dvh;
  max-width: 1120px;
  margin: 0 auto;
  padding: 24px 24px 40px;
  background:
    radial-gradient(circle at 12% -8%, rgba(147, 197, 253, 0.28), transparent 42%),
    radial-gradient(circle at 92% 4%, rgba(167, 243, 208, 0.32), transparent 40%),
    linear-gradient(160deg, #f4f8f6 0%, #f7f9ff 55%, #fdf8f2 100%);
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  align-items: center;
  gap: 32px;
  padding: 40px 8px 36px;
}
.hero__badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 7px 14px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: #3f6b8c;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.9);
  border-radius: 999px;
  box-shadow: 0 6px 18px rgba(31, 45, 39, 0.05);
}
.hero__badge-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #22c55e;
  box-shadow: 0 0 0 4px rgba(34, 197, 94, 0.16);
}
.hero h1 {
  margin: 22px 0 12px;
  font-size: clamp(42px, 7vw, 68px);
  font-weight: 750;
  letter-spacing: -0.045em;
  line-height: 1;
  background: linear-gradient(110deg, #1f3b5c 0%, #3b82f6 45%, #14b8a6 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}
.hero__content p {
  max-width: 520px;
  margin: 0;
  font-size: 15px;
  line-height: 1.8;
  color: var(--home-muted);
}
.hero__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 24px;
  span {
    padding: 6px 12px;
    font-size: 12px;
    color: #5b6b63;
    background: rgba(255, 255, 255, 0.66);
    border: 1px solid rgba(224, 231, 225, 0.9);
    border-radius: 8px;
  }
}
.hero__art {
  position: relative;
  height: 190px;
}
.hero__orb {
  position: absolute;
  inset: 8px 26px 12px;
  border-radius: 50%;
  background: radial-gradient(circle at 32% 28%, #bbf7d0, #93c5fd 52%, #c4b5fd 100%);
  opacity: 0.65;
  filter: blur(3px);
}
.hero__card {
  position: absolute;
  width: 174px;
  height: 118px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.92);
  box-shadow: 0 22px 44px rgba(31, 45, 39, 0.14);
  backdrop-filter: blur(10px);
}
.hero__card--back {
  top: 12px;
  left: 92px;
  transform: rotate(9deg);
}
.hero__card--front {
  top: 56px;
  left: 26px;
  display: flex;
  flex-direction: column;
  gap: 9px;
  padding: 18px;
  transform: rotate(-6deg);
  animation: float 6s ease-in-out infinite;
}
.hero__bar {
  height: 8px;
  border-radius: 99px;
  background: linear-gradient(90deg, #93c5fd, #a7f3d0);
}
.hero__bar--short {
  width: 58%;
}
.hero__block {
  flex: 1;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(147, 197, 253, 0.4), rgba(167, 243, 208, 0.4));
}
@keyframes float {
  0%,
  100% {
    transform: rotate(-6deg) translateY(0);
  }
  50% {
    transform: rotate(-6deg) translateY(-9px);
  }
}

.section {
  margin-top: 30px;
}
.section__head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin: 0 4px 16px;
  h2 {
    margin: 0;
    font-size: 17px;
    font-weight: 700;
    color: var(--home-ink);
  }
  span {
    font-size: 12px;
    color: var(--home-muted);
  }
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 18px;
}

.feature {
  --accent: #3b82f6;
  --accent-gradient: linear-gradient(100deg, #3b82f6, #6366f1);
  --accent-soft: #dbeafe;

  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 236px;
  padding: 26px;
  overflow: hidden;
  color: inherit;
  text-decoration: none;
  background: #fff;
  border: 1px solid rgba(224, 231, 225, 0.9);
  border-radius: 22px;
  box-shadow: 0 10px 30px rgba(31, 45, 39, 0.05);
  transition:
    transform 0.25s ease,
    box-shadow 0.25s ease,
    border-color 0.25s ease;

  &::before {
    content: '';
    position: absolute;
    inset: 0 0 auto;
    height: 4px;
    background: var(--accent-gradient);
  }
  &::after {
    content: '';
    position: absolute;
    top: -70px;
    right: -70px;
    width: 170px;
    height: 170px;
    border-radius: 50%;
    background: var(--accent-soft);
    opacity: 0.55;
    transition: transform 0.35s ease;
  }
  &:hover {
    transform: translateY(-5px);
    border-color: transparent;
    box-shadow: 0 22px 48px rgba(31, 45, 39, 0.13);
  }
  &:hover::after {
    transform: scale(1.3);
  }
  &:hover .feature__arrow {
    transform: translate(3px, -3px);
  }
}
.feature--teal {
  --accent: #0d9488;
  --accent-gradient: linear-gradient(100deg, #0ea5e9, #14b8a6);
  --accent-soft: #ccfbf1;
}
.feature__top {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}
.feature__icon {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  color: #fff;
  background: var(--accent-gradient);
  border-radius: 16px;
  box-shadow: 0 12px 24px -8px var(--accent);
  svg {
    width: 26px;
    height: 26px;
  }
}
.feature__arrow {
  font-size: 22px;
  line-height: 1;
  color: #b4c0b9;
  transition: transform 0.25s ease;
}
.feature h3 {
  position: relative;
  z-index: 1;
  margin: 22px 0 8px;
  font-size: 20px;
  font-weight: 700;
  color: var(--home-ink);
}
.feature p {
  position: relative;
  z-index: 1;
  flex: 1;
  margin: 0;
  font-size: 14px;
  line-height: 1.75;
  color: var(--home-muted);
}
.feature__cta {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 20px;
  font-size: 13px;
  font-weight: 650;
  color: var(--accent);
  &::after {
    content: '→';
    transition: transform 0.25s ease;
  }
}
.feature:hover .feature__cta::after {
  transform: translateX(4px);
}

.test-panel {
  display: flex;
  gap: 20px;
  padding: 26px;
  background: #fff;
  border: 1px solid rgba(224, 231, 225, 0.9);
  border-radius: 22px;
  box-shadow: 0 10px 30px rgba(31, 45, 39, 0.05);
}
.test-panel__icon {
  flex: 0 0 52px;
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  color: #fff;
  background: linear-gradient(100deg, #10b981, #34d399);
  border-radius: 16px;
  box-shadow: 0 12px 24px -8px #10b981;
  svg {
    width: 26px;
    height: 26px;
  }
}
.test-panel__body {
  flex: 1;
  min-width: 0;
  h3 {
    margin: 2px 0 6px;
    font-size: 17px;
    color: var(--home-ink);
  }
  p {
    margin: 0;
    font-size: 14px;
    line-height: 1.75;
    color: var(--home-muted);
  }
}
.test-panel__actions {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 18px;
}
.test-panel__status {
  font-size: 12px;
  font-weight: 600;
  color: #10b981;
}
.test-panel__result {
  max-height: 260px;
  margin: 18px 0 0;
  padding: 16px;
  overflow: auto;
  font-size: 13px;
  line-height: 1.7;
  color: #a7f3d0;
  white-space: pre-wrap;
  word-break: break-all;
  background: #12251f;
  border-radius: 14px;
}
.home__footer {
  margin: 40px 4px 0;
  padding-top: 20px;
  font-size: 12px;
  color: #9aa8a0;
  text-align: center;
  border-top: 1px solid rgba(224, 231, 225, 0.9);
}

@media (max-width: 760px) {
  .home {
    padding: 16px 16px 32px;
  }
  .hero {
    grid-template-columns: 1fr;
    padding: 28px 4px 20px;
  }
  .hero__art {
    display: none;
  }
  .test-panel {
    flex-direction: column;
    gap: 16px;
  }
}
</style>
