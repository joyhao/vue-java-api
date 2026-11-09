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
    <header class="home__header">
      <span class="home__label">工作台</span>
      <h1>joykit</h1>
    </header>
    <section class="home__content" aria-label="功能列表">
      <h2>用户管理</h2>
      <p>管理用户资料、状态，支持分页查询与批量操作。</p>
      <RouterLink to="/users"><el-button type="primary">进入用户管理</el-button></RouterLink>
    </section>
    <section class="home__content" aria-label="接口测试">
      <h2>接口测试</h2>
      <p>点击按钮调用 POST /api/users/test，返回数据会打印到浏览器控制台。</p>
      <el-button type="success" :loading="testing" @click="runTest">测试 /users/test</el-button>
      <pre v-if="testResult" class="home__result">{{ testResult }}</pre>
    </section>
  </main>
</template>

<style scoped lang="scss">
.home {
  max-width: 1120px;
  margin: 0 auto;
  padding: 48px 24px;

  &__label {
    color: var(--el-color-primary);
    font-size: 14px;
    font-weight: 600;
  }

  &__header {
    margin-bottom: 32px;

    h1 {
      margin: 12px 0;
      font-size: clamp(28px, 4vw, 40px);
    }

    p {
      color: #78867f;
      line-height: 1.8;
    }
  }

  &__content {
    padding: 32px 16px;
    background: #fff;
    border: 1px solid #e0e7e1;
    border-radius: 16px;

    & + & {
      margin-top: 16px;
    }
  }

  &__result {
    margin-top: 16px;
    padding: 12px;
    overflow: auto;
    color: #33443b;
    font-size: 13px;
    line-height: 1.6;
    white-space: pre-wrap;
    word-break: break-all;
    background: #f4f8f5;
    border-radius: 8px;
  }
}
</style>
