<!-- 文件路径: admin-web/src/views/category/index.vue -->
<template>
  <div class="page-container">
    <div class="page-card">
      <div class="head">
        <div class="head__text">
          <h2 class="head__title">商品分类</h2>
          <p class="head__desc">
            拖动左侧分类可调整小程序首页的展示顺序（排序值越小越靠前），右侧可编辑名称、图标与状态。
          </p>
        </div>
        <div class="head__actions">
          <el-button :icon="Refresh" @click="loadCategories">刷新</el-button>
          <el-button v-permission="'admin'" type="primary" :icon="Plus" @click="openCreate">
            新增分类
          </el-button>
        </div>
      </div>

      <!-- 错误态 -->
      <el-result
        v-if="error"
        icon="error"
        title="分类数据加载失败"
        sub-title="请检查后端服务是否启动、网络是否正常"
      >
        <template #extra>
          <el-button type="primary" @click="loadCategories">重新加载</el-button>
        </template>
      </el-result>

      <div v-else class="layout">
        <!-- 左：分类列表（可拖拽排序） -->
        <aside v-loading="loading" class="side">
          <div v-if="categories.length && canWrite" class="side__hint">按住拖动可排序</div>
          <el-empty
            v-if="!loading && !categories.length"
            :description="canWrite ? '还没有分类，点击右上角新增' : '暂无分类'"
            :image-size="90"
          />
          <div
            v-for="(item, index) in categories"
            :key="item.id"
            class="cat"
            :class="{ 'cat--active': item.id === selectedId }"
            :draggable="canWrite"
            @click="selectCategory(item)"
            @dragstart="onDragStart(index)"
            @dragover.prevent="onDragOver(index)"
            @drop.prevent="onDrop"
            @dragend="onDragEnd"
          >
            <span v-if="canWrite" class="cat__handle" title="拖动排序">⋮⋮</span>
            <span class="cat__icon">
              <img v-if="isImageIcon(item.icon)" :src="item.icon" alt="" />
              <template v-else>{{ item.icon || '—' }}</template>
            </span>
            <span class="cat__info">
              <span class="cat__name">{{ item.name }}</span>
              <span class="cat__meta">商品 {{ item.productCount || 0 }} 件 · 排序 {{ item.sort }}</span>
            </span>
            <el-tag :type="item.status === 1 ? 'success' : 'info'" size="small" effect="light">
              {{ item.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </div>
        </aside>

        <!-- 右：编辑表单 -->
        <section class="main">
          <el-empty
            v-if="!selected"
            description="请选择左侧分类进行编辑"
            :image-size="100"
          />
          <template v-else>
            <div class="main__head">
              <span class="main__title">编辑分类</span>
              <el-tag size="small" type="info" effect="plain">ID: {{ selected.id }}</el-tag>
            </div>
            <el-form ref="editFormRef" :model="form" :rules="rules" label-width="90px" :disabled="!canWrite">
              <el-form-item label="分类名称" prop="name">
                <el-input v-model.trim="form.name" maxlength="30" show-word-limit placeholder="如：数码电子" />
              </el-form-item>
              <el-form-item label="分类图标">
                <div class="icon-row">
                  <div class="icon-preview">
                    <img v-if="isImageIcon(form.icon)" :src="form.icon" alt="图标" />
                    <span v-else>{{ form.icon || '无' }}</span>
                  </div>
                  <div class="icon-actions">
                    <el-upload
                      v-if="canWrite"
                      :show-file-list="false"
                      accept="image/*"
                      :http-request="handleIconUpload"
                    >
                      <el-button size="small" :loading="iconUploading">上传图片</el-button>
                    </el-upload>
                    <el-input
                      v-if="canWrite"
                      v-model.trim="form.icon"
                      size="small"
                      maxlength="20"
                      placeholder="或直接输入 emoji，如 📱"
                      style="margin-top: 8px; width: 220px"
                    />
                    <div v-if="canWrite" class="icon-tip">支持 jpg/png/webp，5MB 以内；也可用 emoji 代替图片</div>
                    <div v-else class="icon-tip">客服账号为只读模式，不可修改图标</div>
                  </div>
                </div>
              </el-form-item>
              <el-form-item label="排序值">
                <el-input-number v-model="form.sort" :min="1" :max="9999" controls-position="right" />
                <span class="inline-tip">数值越小越靠前</span>
              </el-form-item>
              <el-form-item label="状态">
                <el-switch v-model="form.status" :active-value="1" :inactive-value="0" :disabled="!canWrite" />
                <span class="inline-tip">
                  {{ form.status === 1 ? '启用后在小程序分类导航中显示' : '禁用后小程序不再展示该分类' }}
                </span>
              </el-form-item>
              <el-form-item v-if="canWrite">
                <el-button type="primary" :loading="saving" @click="handleSave">保存修改</el-button>
                <el-button type="danger" plain :icon="Delete" @click="handleDelete">删除分类</el-button>
              </el-form-item>
            </el-form>
          </template>
        </section>
      </div>
    </div>

    <!-- 新增分类弹窗 -->
    <el-dialog v-model="createVisible" title="新增分类" width="520px" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" :rules="rules" label-width="90px">
        <el-form-item label="分类名称" prop="name">
          <el-input v-model.trim="createForm.name" maxlength="30" show-word-limit placeholder="如：数码电子" />
        </el-form-item>
        <el-form-item label="分类图标">
          <div class="icon-row">
            <div class="icon-preview">
              <img v-if="isImageIcon(createForm.icon)" :src="createForm.icon" alt="图标" />
              <span v-else>{{ createForm.icon || '无' }}</span>
            </div>
            <div class="icon-actions">
              <el-upload :show-file-list="false" accept="image/*" :http-request="handleCreateIconUpload">
                <el-button size="small" :loading="iconUploading">上传图片</el-button>
              </el-upload>
              <el-input
                v-model.trim="createForm.icon"
                size="small"
                maxlength="20"
                placeholder="或直接输入 emoji，如 📚"
                style="margin-top: 8px; width: 220px"
              />
            </div>
          </div>
        </el-form-item>
        <el-form-item label="排序值">
          <el-input-number v-model="createForm.sort" :min="1" :max="9999" controls-position="right" />
          <span class="inline-tip">不填则自动排到末尾</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="createForm.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreateSubmit">确定新增</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Plus, Refresh } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import {
  createCategory,
  deleteCategory,
  listCategories,
  updateCategory
} from '@/api/category'
import { uploadImage } from '@/api/upload'

const userStore = useUserStore()
// 客服只读：表单整体禁用，写操作按钮由 v-permission 移除
const canWrite = computed(() => userStore.isAdmin)

const categories = ref([])
const loading = ref(false)
const error = ref(false)
const selectedId = ref(null)
const selected = computed(() => categories.value.find(item => item.id === selectedId.value) || null)

const rules = {
  name: [
    { required: true, message: '请输入分类名称', trigger: 'blur' },
    { max: 30, message: '分类名称不能超过30个字', trigger: 'blur' }
  ]
}

// ---------- 图标 ----------
const isImageIcon = icon => !!icon && /^(https?:\/\/|\/uploads\/)/.test(icon)

const iconUploading = ref(false)

const handleIconUpload = async options => {
  iconUploading.value = true
  try {
    const url = await uploadImage(options.file)
    form.icon = url
    ElMessage.success('图标已上传')
  } catch (e) {
    // 错误提示已由请求拦截器统一处理
  } finally {
    iconUploading.value = false
  }
}

const handleCreateIconUpload = async options => {
  iconUploading.value = true
  try {
    const url = await uploadImage(options.file)
    createForm.icon = url
    ElMessage.success('图标已上传')
  } catch (e) {
    // ignore
  } finally {
    iconUploading.value = false
  }
}

// ---------- 列表 ----------
const loadCategories = async () => {
  loading.value = true
  error.value = false
  try {
    const data = await listCategories({ all: true })
    categories.value = data || []
    if (selectedId.value && !categories.value.some(c => c.id === selectedId.value)) {
      selectedId.value = categories.value[0]?.id ?? null
    }
    if (!selectedId.value && categories.value.length) {
      selectedId.value = categories.value[0].id
    }
    syncForm()
  } catch (e) {
    error.value = true
    categories.value = []
  } finally {
    loading.value = false
  }
}

const selectCategory = item => {
  selectedId.value = item.id
  syncForm()
}

// ---------- 编辑表单 ----------
const editFormRef = ref()
const saving = ref(false)
const form = reactive({ name: '', icon: '', sort: 1, status: 1 })

const syncForm = () => {
  const current = selected.value
  if (!current) return
  form.name = current.name
  form.icon = current.icon || ''
  form.sort = current.sort ?? 1
  form.status = current.status ?? 1
}

const handleSave = async () => {
  const valid = await editFormRef.value.validate().catch(() => false)
  if (!valid || !selected.value) return
  saving.value = true
  try {
    await updateCategory(selected.value.id, {
      name: form.name,
      icon: form.icon,
      sort: form.sort,
      status: form.status
    })
    ElMessage.success('已保存')
    await loadCategories()
  } finally {
    saving.value = false
  }
}

const handleDelete = async () => {
  if (!selected.value) return
  const confirmed = await ElMessageBox.confirm(
    `确定删除分类「${selected.value.name}」吗？分类下仍有商品时无法删除。`,
    '删除分类',
    { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }
  ).catch(() => false)
  if (!confirmed) return
  await deleteCategory(selected.value.id)
  ElMessage.success('已删除')
  selectedId.value = null
  await loadCategories()
}

// ---------- 拖拽排序 ----------
let dragIndex = -1
let sortSnapshot = ''

const onDragStart = index => {
  dragIndex = index
  sortSnapshot = categories.value.map(c => c.id).join(',')
}

const onDragOver = index => {
  if (dragIndex === -1 || dragIndex === index) return
  const list = [...categories.value]
  const [moved] = list.splice(dragIndex, 1)
  list.splice(index, 0, moved)
  categories.value = list
  dragIndex = index
}

const onDragEnd = () => {
  dragIndex = -1
}

const onDrop = async () => {
  const current = categories.value.map(c => c.id).join(',')
  dragIndex = -1
  if (current === sortSnapshot) return
  // 按新顺序重排 sort，再逐条保存（接口复用 PUT /api/category/{id}）
  const payload = categories.value.map((item, index) => ({ ...item, sort: index + 1 }))
  categories.value = payload
  try {
    await Promise.all(
      payload.map(item =>
        updateCategory(item.id, {
          name: item.name,
          icon: item.icon,
          sort: item.sort,
          status: item.status
        })
      )
    )
    ElMessage.success('排序已保存')
    syncForm()
  } catch (e) {
    ElMessage.error('排序保存失败，已重新加载')
    await loadCategories()
  }
}

// ---------- 新增 ----------
const createVisible = ref(false)
const creating = ref(false)
const createFormRef = ref()
const createForm = reactive({ name: '', icon: '', sort: 1, status: 1 })

const openCreate = () => {
  const maxSort = categories.value.reduce((max, item) => Math.max(max, item.sort || 0), 0)
  Object.assign(createForm, { name: '', icon: '', sort: maxSort + 1, status: 1 })
  createVisible.value = true
}

const handleCreateSubmit = async () => {
  const valid = await createFormRef.value.validate().catch(() => false)
  if (!valid) return
  creating.value = true
  try {
    const created = await createCategory({
      name: createForm.name,
      icon: createForm.icon,
      sort: createForm.sort,
      status: createForm.status
    })
    ElMessage.success('新增成功')
    createVisible.value = false
    await loadCategories()
    if (created?.id) {
      selectedId.value = created.id
      syncForm()
    }
  } finally {
    creating.value = false
  }
}

onMounted(loadCategories)
</script>

<style scoped>
.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding-bottom: 14px;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 16px;
}

.head__title {
  margin: 0 0 6px;
  font-size: 17px;
  font-weight: 600;
  color: #303133;
}

.head__desc {
  margin: 0;
  font-size: 13px;
  color: #909399;
}

.layout {
  display: flex;
  gap: 20px;
  min-height: 460px;
}

/* 左侧列表 */
.side {
  width: 360px;
  flex: 0 0 360px;
  border-right: 1px solid #f0f2f5;
  padding-right: 16px;
}

.side__hint {
  font-size: 12px;
  color: #c0c4cc;
  margin-bottom: 8px;
  padding-left: 4px;
}

.cat {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid transparent;
  cursor: pointer;
  transition: all 0.15s;
  user-select: none;
}

.cat:hover {
  background: #f5f7fa;
}

.cat--active {
  background: #ecf5ff;
  border-color: #b3d8ff;
}

.cat__handle {
  color: #c0c4cc;
  font-size: 14px;
  letter-spacing: -2px;
  cursor: grab;
}

.cat__icon {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: #f5f7fa;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  overflow: hidden;
  flex: none;
}

.cat__icon img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cat__info {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

.cat__name {
  font-size: 14px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cat__meta {
  font-size: 12px;
  color: #a8abb2;
  margin-top: 2px;
}

/* 右侧表单 */
.main {
  flex: 1;
  min-width: 0;
  padding: 4px 8px 0 4px;
}

.main__head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 18px;
}

.main__title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.icon-row {
  display: flex;
  gap: 14px;
  align-items: flex-start;
}

.icon-preview {
  width: 62px;
  height: 62px;
  border-radius: 10px;
  border: 1px dashed #dcdfe6;
  background: #fafafa;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  color: #c0c4cc;
  overflow: hidden;
  flex: none;
}

.icon-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.icon-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.icon-tip {
  font-size: 12px;
  color: #a8abb2;
  margin-top: 6px;
}

.inline-tip {
  margin-left: 12px;
  font-size: 12px;
  color: #909399;
}

@media (max-width: 1100px) {
  .layout {
    flex-direction: column;
  }

  .side {
    width: 100%;
    flex: none;
    border-right: none;
    border-bottom: 1px solid #f0f2f5;
    padding: 0 0 12px;
  }
}
</style>
