<script setup lang="ts">
import { computed, ref } from 'vue'
import { currentUser } from '@/api/auth-state'
import { useRouter } from 'vue-router'
import request from '@/api/request'
import { ElMessage } from 'element-plus'
import { getErrorMessage as messageOf } from '@/api/errors'

const router = useRouter()
const loggingOut = ref(false)
const displayName = computed(() => currentUser.value?.name || currentUser.value?.account || '加载中…')
const avatarText = computed(() => currentUser.value ? Array.from(displayName.value)[0]?.toUpperCase() : '…')

async function logout() {
  if (loggingOut.value) return
  loggingOut.value = true
  try {
    await request.post('/auth/logout')
    currentUser.value = null
    await router.replace('/login')
  } catch (error) {
    ElMessage.error(messageOf(error))
  } finally {
    loggingOut.value = false
  }
}

</script>

<template>
  <main class="home">
    <div class="account-bar">
      <div class="profile-menu">
        <button class="profile-trigger" type="button" aria-label="用户菜单">
          <span class="account-avatar" aria-hidden="true">{{ avatarText }}</span>
          <strong :title="displayName">{{ displayName }}</strong>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><path d="m7 10 5 5 5-5" /></svg>
        </button>
        <div class="profile-dropdown">
          <button class="profile-logout" type="button" :disabled="loggingOut" @click="logout">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><path d="M9 4H5a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h4M14 8l4 4-4 4M8 12h13" /></svg>
            {{ loggingOut ? '退出中…' : '退出登录' }}
          </button>
        </div>
      </div>
    </div>

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

    <footer class="home__footer">joykit · 前后端练习项目</footer>
  </main>
</template>

<style scoped lang="scss">
.profile-menu { position: relative; margin-left: auto; z-index: 5; padding-bottom: 10px; margin-bottom: -10px; }
.profile-trigger { display: flex; align-items: center; gap: 10px; border: 1px solid #e1eae5; border-radius: 14px; padding: 7px 11px 7px 7px; background: #ffffffc9; color: #365448; cursor: pointer; font: inherit; strong { max-width: 180px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; font-size: 13px; font-weight: 600; } > svg { width: 16px; height: 16px; color: #8a9c92; } }
.profile-dropdown { position: absolute; top: 100%; right: 0; width: 168px; padding: 6px; background: white; border: 1px solid #e4ece7; border-radius: 12px; box-shadow: 0 12px 32px #203b3414; visibility: hidden; opacity: 0; transform: translateY(-4px); transition: opacity .15s, transform .15s, visibility .15s; }
.profile-menu:hover .profile-dropdown, .profile-menu:focus-within .profile-dropdown { visibility: visible; opacity: 1; transform: translateY(0); }
.profile-logout { display: flex; align-items: center; gap: 9px; width: 100%; border: 0; padding: 11px 12px; border-radius: 8px; background: transparent; color: #b35461; font: inherit; font-size: 13px; cursor: pointer; svg { width: 17px; height: 17px; } &:hover { background: #fff1f3; } &:disabled { opacity: .6; cursor: wait; } }
.profile-trigger:focus-visible, .profile-logout:focus-visible { outline: 2px solid #26705b; outline-offset: 3px; }

.account-bar { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 22px; }
.account-bar__label { color: #718579; font-size: 13px; }
.account-bar__user { display: flex; align-items: center; gap: 12px; min-width: 0; }
.account-avatar { display: grid; place-items: center; width: 42px; height: 42px; flex-shrink: 0; border-radius: 14px; background: linear-gradient(135deg, #dbeafe, #ccfbf1); color: #287764; font-size: 18px; font-weight: 700; border: 1px solid #d9e9e3; }
.account-info { display: flex; flex-direction: column; gap: 3px; min-width: 0; strong { max-width: 180px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; color: #344e43; } > span { font-size: 11px; color: #87988e; } }
.logout-button { display: flex; align-items: center; gap: 6px; flex-shrink: 0; padding: 9px 12px; margin-left: 8px; border: 1px solid #dfe8e2; border-radius: 9px; color: #60776a; background: #ffffffc9; font: inherit; font-size: 12px; cursor: pointer; svg { width: 16px; height: 16px; } &:hover:not(:disabled) { color: #b04d58; border-color: #ebcfd2; background: #fff8f8; } &:disabled { opacity: .6; cursor: wait; } &:focus-visible { outline: 2px solid #26705b; outline-offset: 3px; } }
@media (max-width: 480px) { .account-bar__label { display: none; } .account-bar__user { width: 100%; } .account-info { flex: 1; } .account-info strong { max-width: 140px; } .logout-button { margin-left: auto; } }

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
