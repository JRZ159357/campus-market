<!-- 文件路径: admin-web/src/views/notice/index.vue -->
<template>
  <div class="page-container">
    <el-row :gutter="12">
      <!-- 左：发布公告（仅管理员） -->
      <el-col :xs="24" :md="9">
        <div class="page-card">
          <div class="toolbar">
            <span class="toolbar__title">发布系统公告</span>
            <el-tag v-if="isReadOnly" type="warning" size="small" effect="plain">客服只读</el-tag>
          </div>

          <el-alert
            class="tip"
            type="info"
            :closable="false"
            show-icon
            title="公告会推送到每位用户的「消息 → 通知」，并实时弹出给在线用户"
          />

          <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
            <el-form-item label="标题" prop="title">
              <el-input
                v-model.trim="form.title"
                maxlength="60"
                show-word-limit
                placeholder="例如：国庆假期校园快递点调整通知"
                :disabled="isReadOnly"
              />
            </el-form-item>

            <el-form-item label="内容" prop="content">
              <el-input
                v-model="form.content"
                type="textarea"
                :rows="7"
                maxlength="500"
                show-word-limit
                placeholder="请填写公告正文，建议说清时间、地点与影响范围"
                :disabled="isReadOnly"
              />
            </el-form-item>

            <el-form-item label="发布范围">
              <el-radio-group v-model="form.scope" :disabled="isReadOnly">
                <el-radio :value="1">全部用户</el-radio>
                <el-radio :value="2">指定用户</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item v-if="form.scope === 2" label="指定用户ID" prop="userIds">
              <el-select
                v-model="form.userIds"
                multiple
                filterable
                allow-create
                default-first-option
                placeholder="输入用户ID后回车，可填多个"
                style="width: 100%"
                :disabled="isReadOnly"
              />
              <div class="hint">按用户ID精确发送，适合定向通知（如某位用户的举报处理结果）</div>
            </el-form-item>

            <el-button
              v-permission="'admin'"
              type="primary"
              :loading="submitting"
              style="width: 100%"
              @click="handlePublish"
            >
              发布公告
            </el-button>
          </el-form>
        </div>
      </el-col>

      <!-- 右：已发布公告 -->
      <el-col :xs="24" :md="15">
        <div class="page-card">
          <div class="toolbar">
            <span class="toolbar__title">已发布公告</span>
            <el-button link type="primary" :icon="Refresh" @click="loadList">刷新</el-button>
          </div>

          <el-table v-loading="loading" :data="list" size="default" style="width: 100%">
            <el-table-column label="标题" min-width="180">
              <template #default="{ row }">
                <div class="notice-title">{{ row.title }}</div>
                <div class="notice-content">{{ row.content }}</div>
              </template>
            </el-table-column>
            <el-table-column label="接收 / 已读" width="120" align="center">
              <template #default="{ row }">
                <span class="stat">{{ row.readCount || 0 }} / {{ row.receiverCount || 0 }}</span>
                <el-progress
                  :percentage="percentOf(row)"
                  :stroke-width="4"
                  :show-text="false"
                  class="stat-bar"
                />
              </template>
            </el-table-column>
            <el-table-column label="发布时间" width="170">
              <template #default="{ row }">{{ row.createTime || '—' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="90" align="center">
              <template #default="{ row }">
                <el-button v-permission="'admin'" link type="danger" @click="handleDelete(row)">
                  删除
                </el-button>
                <span v-if="isReadOnly" class="muted">只读</span>
              </template>
            </el-table-column>

            <template #empty>
              <el-empty description="还没有发布过公告" :image-size="90" />
            </template>
          </el-table>

          <div class="pagination">
            <el-pagination
              v-model:current-page="query.pageNum"
              v-model:page-size="query.pageSize"
              :total="total"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              background
              @current-change="loadList"
              @size-change="loadList"
            />
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { pageBroadcasts, publishNotice, deleteBroadcast } from '@/api/notice'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const isReadOnly = computed(() => userStore.isReadOnly)

const formRef = ref(null)
const submitting = ref(false)
const form = reactive({ title: '', content: '', scope: 1, userIds: [] })
const rules = {
  title: [
    { required: true, message: '请填写公告标题', trigger: 'blur' },
    { min: 2, max: 60, message: '标题长度 2-60 字', trigger: 'blur' }
  ],
  content: [
    { required: true, message: '请填写公告内容', trigger: 'blur' },
    { min: 5, max: 500, message: '内容长度 5-500 字', trigger: 'blur' }
  ],
  userIds: [
    {
      validator: (rule, value, callback) => {
        if (form.scope === 2 && (!value || value.length === 0)) {
          callback(new Error('请至少选择一位接收用户'))
          return
        }
        callback()
      },
      trigger: 'change'
    }
  ]
}

const query = reactive({ pageNum: 1, pageSize: 10 })
const list = ref([])
const total = ref(0)
const loading = ref(false)

const percentOf = row => {
  if (!row.receiverCount) return 0
  return Math.round(((row.readCount || 0) / row.receiverCount) * 100)
}

const loadList = async () => {
  loading.value = true
  try {
    const data = await pageBroadcasts({ ...query })
    list.value = data.records || []
    total.value = Number(data.total || 0)
  } catch (e) {
    ElMessage.error(e.message || '公告列表加载失败')
  } finally {
    loading.value = false
  }
}

const handlePublish = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async valid => {
    if (!valid) return
    submitting.value = true
    try {
      await publishNotice({
        title: form.title,
        content: form.content,
        scope: form.scope,
        // 多选组件在 allow-create 下可能给出字符串，统一转成数字 ID
        userIds: form.scope === 2 ? form.userIds.map(v => Number(v)).filter(v => v > 0) : undefined
      })
      ElMessage.success('公告已发布')
      form.title = ''
      form.content = ''
      form.scope = 1
      form.userIds = []
      query.pageNum = 1
      loadList()
    } catch (e) {
      ElMessage.error(e.message || '发布失败')
    } finally {
      submitting.value = false
    }
  })
}

const handleDelete = async row => {
  try {
    await ElMessageBox.confirm(
      `删除后该公告将从所有用户的「通知」中消失（共 ${row.receiverCount || 0} 条），确定删除？`,
      '删除公告',
      { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  try {
    await deleteBroadcast(row.id)
    ElMessage.success('公告已删除')
    loadList()
  } catch (e) {
    ElMessage.error(e.message || '删除失败')
  }
}

onMounted(loadList)
</script>

<style scoped>
.tip {
  margin-bottom: 14px;
}

.hint {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}

.notice-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.notice-content {
  margin-top: 2px;
  font-size: 12px;
  color: #909399;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.stat {
  font-size: 12px;
  font-family: Consolas, Monaco, monospace;
  color: #606266;
}

.stat-bar {
  margin-top: 2px;
}

.muted {
  font-size: 12px;
  color: #c0c4cc;
}
</style>
