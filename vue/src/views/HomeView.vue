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
  }
}
</style>
