<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import request from '@/api/request'
import { getErrorMessage } from '@/api/errors'

const router = useRouter()
const route = useRoute()
const rememberedAccount = localStorage.getItem('joykit-account') || ''
const form = reactive({ account: rememberedAccount, password: '', remember: !!rememberedAccount })
const visible = ref(false)
const submitted = ref(false)
const submitting = ref(false)

async function submit() {
  submitted.value = true
  if (submitting.value || !form.account.trim() || !form.password) return
  submitting.value = true
  try {
    await request.post('/auth/login', { account: form.account.trim(), password: form.password })
    if (form.remember) localStorage.setItem('joykit-account', form.account.trim())
    else localStorage.removeItem('joykit-account')
    form.password = ''
    const target = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(target.startsWith('/') && !target.startsWith('//') && !target.includes('\\') && target.split('?')[0] !== '/login' ? target : '/')
  } catch (error) {
    ElMessage.error(getErrorMessage(error))
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <header class="page-header">
      <a class="brand" href="/login" aria-label="joykit 登录">
        <svg class="brand-symbol" viewBox="0 0 32 32" fill="none" aria-hidden="true"><rect x="3" y="3" width="12" height="12" rx="4" fill="currentColor" /><rect x="18" y="3" width="11" height="12" rx="4" fill="currentColor" opacity=".45" /><rect x="3" y="18" width="12" height="11" rx="4" fill="currentColor" opacity=".45" /><rect x="18" y="18" width="11" height="11" rx="4" fill="currentColor" /></svg>
        joykit<span class="brand-divider" /><span class="workspace-label">个人工作台</span>
      </a>
      <span class="header-note">你的内容，你的空间。</span>
    </header>

    <section class="login-content">
      <div class="intro">
        <span class="welcome-label">YOUR SPACE. YOUR FLOW.</span>
        <h1>很高兴，再次见到你<span>。</span></h1>
        <p>登录 joykit，回到属于你的工作空间。</p>
      </div>
      <section class="login-card" aria-label="账号登录">
        <div class="card-heading"><h2>账号登录</h2><span class="secure-label"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path d="m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6l8-3Z" /><path d="m8 12 3 3 5-6" /></svg>安全访问</span></div>
        <form novalidate @submit.prevent="submit">
          <div class="field">
            <label for="account">账号</label>
            <div class="input-wrap" :class="{ invalid: submitted && !form.account.trim() }">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><circle cx="12" cy="8" r="4" /><path d="M4 21v-2a8 8 0 0 1 16 0v2" /></svg>
              <input id="account" v-model="form.account" name="username" autocomplete="username" placeholder="请输入你的账号" :aria-invalid="submitted && !form.account.trim()" :aria-describedby="submitted && !form.account.trim() ? 'account-error' : undefined" />
            </div>
            <span v-if="submitted && !form.account.trim()" id="account-error" class="field-error">请输入账号</span>
          </div>
          <div class="field">
            <label for="password">密码</label>
            <div class="input-wrap" :class="{ invalid: submitted && !form.password }">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><rect x="5" y="10" width="14" height="11" rx="3" /><path d="M8 10V7a4 4 0 0 1 8 0v3" /></svg>
              <input id="password" v-model="form.password" name="password" :type="visible ? 'text' : 'password'" autocomplete="current-password" placeholder="请输入你的密码" :aria-invalid="submitted && !form.password" :aria-describedby="submitted && !form.password ? 'password-error' : undefined" />
              <button class="password-toggle" type="button" :aria-label="visible ? '隐藏密码' : '显示密码'" :aria-pressed="visible" @click="visible = !visible"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z" /><circle cx="12" cy="12" r="3" /><path v-if="visible" d="m3 3 18 18" /></svg></button>
            </div>
            <span v-if="submitted && !form.password" id="password-error" class="field-error">请输入密码</span>
          </div>
          <div class="form-options"><label class="remember"><input v-model="form.remember" type="checkbox" />记住账号</label><button type="button" class="text-button" @click="ElMessage.info('请联系管理员重置密码。')">忘记密码？</button></div>
          <button type="submit" class="submit-button" :disabled="submitting" :aria-busy="submitting"><span>{{ submitting ? '正在登录…' : '登录工作台' }}</span><svg v-if="!submitting" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M4 12h16m-6-6 6 6-6 6" /></svg></button>
        </form>
        <div class="card-footer"><span class="status-dot" />专注于重要的事，其他的交给 joykit。</div>
      </section>
      <p class="session-note">为保障账号安全，长时间未操作后需重新登录。</p>
    </section>
    <footer class="page-footer"><span>© 2026 joykit. All rights reserved.</span><span>简单一点，专注多一点。</span></footer>
  </main>
</template>

<style scoped lang="scss">
.login-page {
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  padding: 30px 48px 24px;
  color: #202b45;
  background: radial-gradient(ellipse at 50% 18%, #e8edff 0, transparent 48%), radial-gradient(ellipse at 80% 75%, #e9f5f8 0, transparent 45%), #f7f9fc;
  position: relative;
  isolation: isolate;
  &::before {
    content: '';
    position: absolute;
    inset: 0;
    z-index: -1;
    opacity: .3;
    background-image: radial-gradient(#b5c0d6 .7px, transparent .7px);
    background-size: 24px 24px;
    mask-image: linear-gradient(to bottom, transparent, #000 45%, transparent);
    pointer-events: none;
  }
}
.page-header, .page-footer { display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.brand { display: inline-flex; align-items: center; gap: 10px; font-size: 24px; font-weight: 750; letter-spacing: -.9px; color: #202c49; }
.brand-symbol { width: 28px; height: 28px; color: #4c6ef5; }
.brand-divider { height: 18px; width: 1px; background: #d9dfeb; margin: 0 4px 0 9px; }
.workspace-label { font-size: 12px; letter-spacing: 1px; font-weight: 400; color: #7f8ba2; }
.header-note { font-size: 12px; color: #8b95a9; }
.login-content { width: min(420px, 100%); margin: auto; padding: 65px 0 60px; }
.intro { text-align: center; margin-bottom: 30px; }
.welcome-label { font-size: 10px; letter-spacing: 2.6px; font-weight: 650; color: #7182af; }
h1 { font-size: 29px; letter-spacing: -1px; font-weight: 650; margin: 15px -15px 12px; line-height: 1.5; span { color: #5479f6; } }
.intro p { font-size: 13px; color: #8590a5; line-height: 1.8; margin: 0; }
.login-card { background: #fff; padding: 32px; border: 1px solid #e8ecf4; border-radius: 18px; box-shadow: 0 16px 48px #2d45740a, 0 2px 6px #2d457404; }
.card-heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: 28px; h2 { font-size: 16px; font-weight: 650; margin: 0; } }
.secure-label { display: flex; align-items: center; gap: 4px; color: #8797aa; font-size: 10px; svg { width: 14px; height: 14px; color: #7ca392; } }
.field { margin-bottom: 22px; > label { display: block; margin-bottom: 10px; color: #4b5770; font-size: 12px; font-weight: 550; } }
.input-wrap { display: flex; align-items: center; gap: 11px; min-height: 48px; padding: 0 14px; border: 1px solid #e2e7f0; border-radius: 8px; color: #9aa6bc; background: #fafbfe; transition: border-color .2s, box-shadow .2s, background .2s;
  &:focus-within { background: #fff; border-color: #6f8bf5; box-shadow: 0 0 0 3px #537af510; }
  &.invalid { border-color: #d66b79; }
  svg { width: 17px; height: 17px; flex-shrink: 0; }
  input { padding: 14px 0; min-width: 0; width: 100%; outline: none; border: 0; background: transparent; font: inherit; font-size: 13px; color: #334360; &::placeholder { color: #a4aec1; } }
}
.password-toggle { display: grid; place-items: center; background: none; border: 0; color: #99a5ba; padding: 5px; cursor: pointer; &:hover { color: #4f72e5; } }
.field-error { display: block; color: #c65769; font-size: 11px; margin-top: 7px; }
.form-options { display: flex; align-items: center; justify-content: space-between; margin: 0 0 25px; font-size: 12px; }
.remember { display: flex; align-items: center; gap: 7px; color: #8490a5; cursor: pointer; input { margin: 0; width: 14px; height: 14px; accent-color: #5275ee; } }
.text-button { color: #6b83c1; font: inherit; font-size: 12px; background: none; border: 0; padding: 4px 0; cursor: pointer; &:hover { color: #3b63e2; } }
.submit-button { width: 100%; min-height: 47px; display: flex; align-items: center; justify-content: center; gap: 12px; border: 1px solid #4568e6; border-radius: 8px; color: #fff; background: linear-gradient(110deg, #5277f0, #4768e4); box-shadow: 0 4px 10px #5275e923; font-size: 13px; font-weight: 550; cursor: pointer; transition: transform .2s, box-shadow .2s; svg { width: 17px; height: 17px; } &:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 7px 18px #5275e933; } &:disabled { opacity: .65; cursor: wait; } }
.card-footer { display: flex; align-items: center; justify-content: center; gap: 7px; margin-top: 27px; padding-top: 21px; border-top: 1px solid #eff2f7; color: #98a3b6; font-size: 10px; }
.status-dot { width: 5px; height: 5px; background: #8bb8a6; border-radius: 50%; flex-shrink: 0; }
.session-note { margin: 22px 0 0; text-align: center; color: #98a4b8; font-size: 10px; line-height: 1.8; }
.page-footer { color: #9aa5b7; font-size: 10px; }
button:focus-visible, a:focus-visible, input[type='checkbox']:focus-visible { outline: 2px solid #5e7cec; outline-offset: 4px; }
@media (max-width: 600px) {
  .login-page { padding: 23px 20px; }
  .header-note, .page-footer > span:last-child, .brand-divider, .workspace-label { display: none; }
  .brand { font-size: 22px; }
  .login-content { padding: 50px 0; }
  h1 { font-size: 25px; }
  .login-card { padding: 27px 24px; }
  .page-footer { justify-content: center; }
}
@media (prefers-reduced-motion: reduce) { *, *::before, *::after { transition: none !important; } }
</style>
