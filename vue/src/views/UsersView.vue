<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { getErrorMessage as messageOf } from '@/api/errors'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createUser,
  updateUser,
  deleteUser,
  deleteUsers,
  deleteAllUsers,
  getUsers,
  type User,
  type UserInput,
} from '@/api/users'

const users = ref<User[]>([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const deleting = ref(false)
const error = ref('')
const keyword = ref('')
const query = reactive({ pageNumber: 1, pageSize: 20, keyword: '' })
const selected = ref<User[]>([])
const dialogVisible = ref(false)
const editingId = ref<string | null>(null)
const formRef = ref<FormInstance>()
const emptyForm = (): UserInput => ({
  name: '',
  avatarUrl: null,
  email: null,
  phone: null,
  status: 1,
})
const form = reactive<UserInput>(emptyForm())
const busy = computed(() => loading.value || deleting.value || saving.value)
const rules: FormRules = {
  name: [
    { required: true, whitespace: true, message: '请输入姓名', trigger: 'blur' },
    { max: 255, message: '姓名最多 255 字', trigger: 'blur' },
  ],
  email: [{ type: 'email', message: '请输入正确的邮箱', trigger: 'blur' }],
  phone: [{ pattern: /^\+?[0-9()\-\s]{3,32}$/, message: '请输入有效的联系电话', trigger: 'blur' }],
  avatarUrl: [
    { pattern: /^https?:\/\/\S+$/i, message: '请输入 HTTP 或 HTTPS 地址', trigger: 'blur' },
  ],
}

async function loadUsers() {
  loading.value = true
  error.value = ''
  try {
    let page = await getUsers({ ...query })
    const lastPage = Math.max(1, Math.ceil(page.total / query.pageSize))
    if (query.pageNumber > lastPage) {
      query.pageNumber = lastPage
      page = await getUsers({ ...query })
    }
    users.value = page.items
    total.value = page.total
    selected.value = []
  } catch (exception) {
    error.value = messageOf(exception)
  } finally {
    loading.value = false
  }
}

function search() {
  query.keyword = keyword.value.trim()
  query.pageNumber = 1
  void loadUsers()
}

function openEditor(user?: User) {
  editingId.value = user?.id ?? null
  Object.assign(
    form,
    user
      ? {
          name: user.name,
          avatarUrl: user.avatarUrl,
          email: user.email,
          phone: user.phone,
          status: user.status,
        }
      : emptyForm(),
  )
  dialogVisible.value = true
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    const input: UserInput = {
      name: form.name.trim(),
      avatarUrl: form.avatarUrl?.trim() || null,
      email: form.email?.trim() || null,
      phone: form.phone?.trim() || null,
      status: form.status,
    }
    if (editingId.value) await updateUser(editingId.value, input)
    else await createUser(input)
    ElMessage.success(editingId.value ? '用户已更新' : '用户已新增')
    dialogVisible.value = false
    await loadUsers()
  } catch (exception) {
    ElMessage.error(messageOf(exception))
  } finally {
    saving.value = false
  }
}

async function removeUsers(user?: User) {
  const ids = user ? [user.id] : selected.value.map((item) => item.id)
  if (!ids.length) return
  try {
    await ElMessageBox.confirm(
      user
        ? `确定删除用户“${user.name}”吗？关联的角色关系也会被删除。`
        : `确定删除选中的 ${ids.length} 个用户吗？关联的角色关系也会被删除。`,
      '删除用户',
      { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  deleting.value = true
  try {
    const result = user ? await deleteUser(user.id) : await deleteUsers(ids)
    ElMessage.success(`已删除 ${result.deletedCount} 个用户`)
    await loadUsers()
  } catch (exception) {
    ElMessage.error(messageOf(exception))
  } finally {
    deleting.value = false
  }
}

async function removeAll() {
  let confirmation: string
  try {
    const result = await ElMessageBox.prompt(
      '将永久删除数据库中的全部用户及其角色关联，不受搜索条件限制。请输入“删除全部用户”确认。',
      '危险操作：删除全部用户',
      {
        type: 'warning',
        inputPattern: /^删除全部用户$/,
        inputErrorMessage: '请输入“删除全部用户”',
        confirmButtonText: '永久删除',
        cancelButtonText: '取消',
        closeOnClickModal: false,
      },
    )
    confirmation = result.value
  } catch {
    return
  }
  deleting.value = true
  try {
    const result = await deleteAllUsers(confirmation)
    ElMessage.success(`已删除 ${result.deletedCount} 个用户`)
    query.pageNumber = 1
    await loadUsers()
  } catch (exception) {
    ElMessage.error(messageOf(exception))
  } finally {
    deleting.value = false
  }
}

function resizePage() {
  query.pageNumber = 1
  void loadUsers()
}
function formatDate(value: string) {
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}
onMounted(loadUsers)
</script>

<template>
  <main class="users-page">
    <header class="page-header">
      <div>
        <RouterLink to="/" class="back-link">← 返回工作台</RouterLink>
        <h1>用户管理</h1>
        <p>统一维护用户资料与状态</p>
      </div>
      <el-button type="primary" :disabled="busy" @click="openEditor()">新增用户</el-button>
    </header>
    <section class="panel">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索姓名、邮箱或电话"
          clearable
          maxlength="100"
          :disabled="busy"
          @keyup.enter="search"
          @clear="search"
        />
        <el-button :disabled="busy" @click="search">查询</el-button>
        <el-button :disabled="busy" @click="loadUsers">刷新</el-button>
        <div class="toolbar__spacer" />
        <span class="selection">已选 {{ selected.length }} 项</span>
        <el-button type="danger" plain :disabled="busy || !selected.length" @click="removeUsers()"
          >批量删除</el-button
        >
        <el-button type="danger" text :disabled="busy || !!error" @click="removeAll"
          >删除全部</el-button
        >
      </div>
      <el-alert
        v-if="error"
        :title="error"
        type="error"
        show-icon
        :closable="false"
        class="error-alert"
      />
      <el-table
        v-loading="loading || deleting"
        :data="users"
        row-key="id"
        empty-text="暂无用户，请新增用户或调整查询条件"
        @selection-change="(rows: User[]) => (selected = rows)"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column prop="id" label="ID" width="100" />
        <el-table-column label="用户" min-width="180"
          ><template #default="{ row }"
            ><div class="user-cell">
              <el-avatar :size="36" :src="row.avatarUrl || undefined">{{
                row.name.slice(0, 1)
              }}</el-avatar
              ><strong>{{ row.name }}</strong>
            </div></template
          ></el-table-column
        >
        <el-table-column prop="email" label="邮箱" min-width="200" show-overflow-tooltip
          ><template #default="{ row }">{{ row.email || '—' }}</template></el-table-column
        >
        <el-table-column prop="phone" label="联系电话" min-width="150"
          ><template #default="{ row }">{{ row.phone || '—' }}</template></el-table-column
        >
        <el-table-column label="状态" width="90"
          ><template #default="{ row }"
            ><el-tag :type="row.status === 1 ? 'success' : 'info'">{{
              row.status === 1 ? '正常' : '禁用'
            }}</el-tag></template
          ></el-table-column
        >
        <el-table-column label="创建时间" min-width="185"
          ><template #default="{ row }">{{ formatDate(row.createdAt) }}</template></el-table-column
        >
        <!-- @vue-generic {User} -->
        <el-table-column label="操作" width="140" fixed="right"
          ><template #default="{ row }"
            ><el-button link type="primary" :disabled="busy" @click="openEditor(row)"
              >编辑</el-button
            ><el-button link type="danger" :disabled="busy" @click="removeUsers(row)"
              >删除</el-button
            ></template
          ></el-table-column
        >
      </el-table>
      <div class="pagination">
        <el-pagination
          v-model:current-page="query.pageNumber"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          :disabled="busy"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="loadUsers"
          @size-change="resizePage"
        />
      </div>
    </section>
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑用户' : '新增用户'"
      width="min(520px, 94vw)"
      :close-on-click-modal="false"
      :close-on-press-escape="!saving"
      :show-close="!saving"
      destroy-on-close
      @opened="formRef?.clearValidate()"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        :disabled="saving"
        @submit.prevent="save"
      >
        <el-form-item label="姓名" prop="name"
          ><el-input
            v-model="form.name"
            maxlength="255"
            show-word-limit
            placeholder="请输入姓名或昵称"
        /></el-form-item>
        <el-form-item label="邮箱" prop="email"
          ><el-input v-model="form.email" maxlength="254" placeholder="选填"
        /></el-form-item>
        <el-form-item label="联系电话" prop="phone"
          ><el-input
            v-model="form.phone"
            maxlength="32"
            placeholder="选填，填写时不可与其他用户重复"
        /></el-form-item>
        <el-form-item label="头像地址" prop="avatarUrl"
          ><el-input
            v-model="form.avatarUrl"
            maxlength="2048"
            placeholder="选填，HTTP 或 HTTPS 地址"
        /></el-form-item>
        <el-form-item label="状态"
          ><el-radio-group v-model="form.status"
            ><el-radio :value="1">正常</el-radio
            ><el-radio :value="0">禁用</el-radio></el-radio-group
          ></el-form-item
        >
      </el-form>
      <template #footer
        ><el-button :disabled="saving" @click="dialogVisible = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="save">保存</el-button></template
      >
    </el-dialog>
  </main>
</template>

<style scoped lang="scss">
.users-page {
  max-width: 1480px;
  margin: 0 auto;
  padding: 32px 24px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  margin-bottom: 24px;
  h1 {
    margin: 16px 0 8px;
    font-size: 30px;
  }
  p {
    margin: 0;
    color: #78867f;
  }
}
.back-link {
  color: var(--el-color-primary);
  font-size: 14px;
}
.panel {
  background: #fff;
  padding: 24px;
  border: 1px solid #e0e7e1;
  border-radius: 16px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 24px;
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
.selection {
  font-size: 13px;
  color: #78867f;
  white-space: nowrap;
}
.user-cell {
  display: flex;
  align-items: center;
  gap: 12px;
  strong {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
.error-alert {
  margin-bottom: 16px;
}
.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 24px;
  overflow-x: auto;
}
@media (max-width: 640px) {
  .users-page {
    padding: 24px 12px;
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
