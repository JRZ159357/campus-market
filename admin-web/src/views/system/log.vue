<!-- 文件路径: admin-web/src/views/system/log.vue -->
<template>
  <div class="page-container">
    <el-alert
      class="readonly-tip"
      type="info"
      show-icon
      :closable="false"
      title="操作日志只读"
      description="操作日志用于追溯管理端的所有写操作（接单、处理、封禁、角色变更、强制下架、审核）。平台不提供任何修改或删除日志的入口——一旦日志可删，它就不再具备追溯价值。"
    />

    <el-card shadow="never">
      <el-form :inline="true" class="filter-form">
        <el-form-item label="操作人">
          <el-input
            v-model.trim="query.operatorName"
            placeholder="昵称关键词"
            clearable
            style="width: 160px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item label="模块">
          <el-select v-model="query.module" placeholder="全部" clearable style="width: 140px" @change="handleSearch">
            <el-option v-for="m in LOG_MODULES" :key="m.value" :label="m.label" :value="m.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="动作">
          <el-select v-model="query.action" placeholder="全部" clearable style="width: 140px" @change="handleSearch">
            <el-option v-for="a in LOG_ACTIONS" :key="a.value" :label="a.label" :value="a.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作时间">
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 360px"
            @change="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="records" style="width: 100%" border>
        <el-table-column prop="id" label="ID" width="90" />

        <el-table-column label="操作人" width="150">
          <template #default="{ row }">
            <div class="cell-user">
              <el-avatar :size="28" class="cell-user__avatar">{{ (row.operatorName || '?').slice(0, 1) }}</el-avatar>
              <div>
                <div class="cell-user__name">{{ row.operatorName || '系统' }}</div>
                <el-tag size="small" effect="plain" :type="roleTag(row.operatorRole)">
                  {{ row.operatorRoleLabel }}
                </el-tag>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="模块" width="120">
          <template #default="{ row }">
            <span class="module-tag" :style="{ background: moduleColor(row.module) }">
              {{ row.moduleLabel }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="动作" width="120">
          <template #default="{ row }">
            <span class="action-text">{{ row.actionLabel }}</span>
          </template>
        </el-table-column>

        <el-table-column label="目标ID" width="100">
          <template #default="{ row }">
            <span v-if="row.targetId" class="cell-mono">{{ row.targetId }}</span>
            <span v-else class="cell-muted">—</span>
          </template>
        </el-table-column>

        <el-table-column prop="detail" label="操作详情" min-width="320" show-overflow-tooltip />

        <el-table-column prop="ip" label="IP" width="130">
          <template #default="{ row }">
            <span class="cell-mono">{{ row.ip || '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作时间" width="160">
          <template #default="{ row }">
            <span class="cell-time">{{ String(row.createTime || '').slice(0, 16) }}</span>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :page-sizes="[20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        class="pager"
        @current-change="loadData"
        @size-change="handleSearch"
      />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { pageOperationLogs, LOG_MODULES, LOG_ACTIONS, MODULE_COLORS } from '@/api/operationLog'

const loading = ref(false)
const records = ref([])
const total = ref(0)
const dateRange = ref([])

const query = reactive({ pageNum: 1, pageSize: 20, operatorName: '', module: '', action: '' })

const loadData = async () => {
  loading.value = true
  try {
    const params = { ...query }
    if (dateRange.value && dateRange.value.length === 2) {
      params.startTime = dateRange.value[0]
      params.endTime = dateRange.value[1]
    }
    const data = await pageOperationLogs(params)
    records.value = data.records || []
    total.value = Number(data.total || 0)
  } catch (e) {
    ElMessage.error(e.message || '操作日志加载失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  query.pageNum = 1
  loadData()
}

const handleReset = () => {
  query.pageNum = 1
  query.operatorName = ''
  query.module = ''
  query.action = ''
  dateRange.value = []
  loadData()
}

const roleTag = role => {
  return { admin: 'danger', customer_service: 'warning' }[role] || 'info'
}

const moduleColor = module => MODULE_COLORS[module] || '#909399'

onMounted(loadData)
</script>

<style scoped>
.readonly-tip {
  margin-bottom: 12px;
}

.filter-form {
  margin-bottom: 4px;
}

.cell-user {
  display: flex;
  gap: 8px;
  align-items: center;
}

.cell-user__avatar {
  flex: none;
  background: linear-gradient(135deg, #409eff, #66b1ff);
  color: #fff;
  font-size: 13px;
}

.cell-user__name {
  font-size: 13px;
  color: #303133;
}

/* 模块用色块而非标签：色块比文字更容易扫读，
   一屏日志里「哪一屏是举报处理」一眼可见 */
.module-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 10px;
  color: #fff;
  font-size: 12px;
}

.action-text {
  font-size: 13px;
  color: #606266;
}

.cell-mono {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  color: #606266;
}

.cell-muted {
  color: #c0c4cc;
}

.cell-time {
  font-size: 13px;
  color: #606266;
}

.pager {
  margin-top: 14px;
  justify-content: flex-end;
}
</style>