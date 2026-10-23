<script setup lang="ts">
import { useCounterStore } from '@/stores/counter'

const counter = useCounterStore()
const technologies = [
  'Vue 3',
  'TypeScript',
  'Vite',
  'Vue Router',
  'Axios',
  'Element Plus',
  'Pinia',
  'Sass',
  'ESLint',
  'Prettier',
  'unplugin-auto-import',
  'unplugin-vue-components',
]
</script>

<template>
  <main class="workspace">
    <header class="topbar">
      <a class="brand" href="/">vue<span> / starter</span></a>
      <el-tag type="success" effect="plain" round>开发环境已就绪</el-tag>
    </header>

    <section class="hero">
      <p class="eyebrow">YOUR NEXT PROJECT STARTS HERE</p>
      <h1>从一个好起点，<br /><span>构建下一个想法。</span></h1>
      <p class="description">轻量、类型安全、开箱即用的 Vue 3 工程模板。</p>
      <div class="technology-list">
        <el-tag v-for="technology in technologies" :key="technology" type="info" effect="plain">
          {{ technology }}
        </el-tag>
      </div>
    </section>

    <section class="cards">
      <el-card shadow="never" class="demo-card">
        <p class="eyebrow">01 / STATE MANAGEMENT</p>
        <h2>让状态流动起来</h2>
        <p class="muted">点击按钮，体验 Pinia 响应式状态管理。</p>
        <div class="counter">{{ counter.count }}</div>
        <p class="muted">双倍计算值：{{ counter.doubled }}</p>
        <div class="actions">
          <el-button type="primary" @click="counter.increment">增加计数</el-button>
          <el-button @click="counter.reset">重置</el-button>
        </div>
      </el-card>

      <el-card shadow="never" class="demo-card">
        <p class="eyebrow">02 / READY TO BUILD</p>
        <h2>专注于你的业务</h2>
        <ul class="feature-list">
          <li><span>页面导航</span><strong>Vue Router · 懒加载路由</strong></li>
          <li><span>接口请求</span><strong>Axios · 独立请求实例</strong></li>
          <li><span>组件开发</span><strong>Element Plus · 按需自动导入</strong></li>
          <li><span>代码质量</span><strong>TypeScript + ESLint + Prettier</strong></li>
        </ul>
      </el-card>
    </section>
    <footer>VUE STARTER <span>简洁基础，无限可能。</span></footer>
  </main>
</template>
