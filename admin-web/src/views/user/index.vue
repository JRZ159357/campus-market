<!-- 文件路径: admin-web/src/views/user/index.vue -->
<template>
  <div class="page-container">
    <div class="page-card">
      <!-- 1. 顶部标签：角色与状态 -->
      <el-tabs v-model="activeTab" class="user-tabs" @tab-change="handleTabChange">
        <el-tab-pane
          v-for="tab in tabs"
          :key="tab.key"
          :label="tab.label"
          :name="tab.key"
        />
      </el-tabs>

      <!-- 2. 搜索栏 -->
      <el-form :model="query" inline class="search-bar">
        <el-form-item label="关键词">
          <el-input
            v-model.trim="query.keyword"
            placeholder="昵称 / 账号 / 手机号 / 学号 / 用户ID"
            clearable
            style="width: 240px"
            :prefix-icon="Search"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item label="信誉分">
          <el-input-number
            v-model="query.minCredit"
            :min="0"
            :max="150"
            :controls="false"
            placeholder="最低"
            style="width: 92px"
          />
          <span class="range-sep">—</span>
          <el-input-number
            v-model="query.maxCredit"
            :min="0"
            :max="150"
            :controls="false"
            placeholder="最高"
            style="width: 92px"
          />
        </el-form-item>
        <el-form-item label="注册时间">
          <el-date-picker
            v-model="registerRange"
            type="datetimerange"
            range-separator="—"
            start-placeholder="开始"
            end-placeholder="结束"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 340px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 3. 工具栏 -->
      <div class="toolbar">
        <span class="toolbar__title">
          用户列表
          <el-tag v-if="isReadOnly" type="warning" size="small" effect="plain">客服只读</el-tag>
        </span>
        <el-button :icon="Refresh" circle @click="loadData" />
      </div>

      <!-- 4. 表格 -->
      <template v-if="!error">
        <el-table
          v-loading="loading"
          :data="list"
          border
          stripe
          highlight-current-row
          :header-cell-style="{ background: '#fafafa', color: '#606266', fontWeight: 600 }"
          style="width: 100%"
        >
          <el-table-column type="index" label="#" width="55" align="center" />
          <el-table-column label="用户" min-width="190">
            <template #default="{ row }">
              <div class="user-cell">
                <el-avatar :size="34" :src="row.avatar || undefined">
                  {{ (row.nickname || 'U').slice(0, 1) }}
                </el-avatar>
                <div class="user-cell__info">
                  <div class="user-cell__name">
                    {{ row.nickname || '未设置昵称' }}
                    <el-tag v-if="row.studentVerified === 1" size="small" type="success" effect="plain">
                      已认证
                    </el-tag>
                  </div>
                  <div class="user-cell__sub">
                    ID {{ row.userId }} ·
                    {{ row.username || '未绑定账号' }}
                    <template v-if="row.studentNo"> · {{ row.studentNo }}</template>
                  </div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="手机号" width="130">
            <template #default="{ row }">
              <span v-if="row.phone">{{ row.phone }}</span>
              <span v-else class="text-muted">未绑定</span>
            </template>
          </el-table-column>
          <el-table-column label="角色" width="96" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="roleType(row.role)">{{ row.roleLabel }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="认证状态" width="92" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.studentVerified === 1 ? 'success' : 'info'" effect="plain">
                {{ row.studentVerifiedLabel }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="信誉分" width="110" align="center">
            <template #default="{ row }">
              <div class="score-cell">
                <span class="score-cell__num" :style="{ color: row.creditLevelColor }">
                  {{ row.creditScore }}
                </span>
                <el-tag size="small" effect="dark" :color="row.creditLevelColor" class="level-tag">
                  {{ row.creditLevelLabel }}
                </el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="发布数" width="86" align="center">
            <template #default="{ row }">{{ row.publishCount || 0 }}</template>
          </el-table-column>
          <el-table-column prop="createTime" label="注册时间" width="165" align="center" />
          <el-table-column label="状态" width="88" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 1 ? 'danger' : 'success'" effect="light">
                {{ row.statusLabel }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="230" align="center" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              <el-button v-permission="'admin'" link type="warning" @click="openEdit(row)">编辑</el-button>
              <el-button
                v-permission="'admin'"
                link
                :type="row.status === 1 ? 'success' : 'danger'"
                @click="openStatus(row)"
              >
                {{ row.status === 1 ? '解封' : '封禁' }}
              </el-button>
              <el-dropdown v-permission="'admin'" @command="cmd => handleCommand(cmd, row)">
                <el-button link type="primary">
                  更多<el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="role">分配角色</el-dropdown-item>
                    <el-dropdown-item command="credit">调整信誉分</el-dropdown-item>
                    <el-dropdown-item command="auth" :disabled="row.studentVerified === 1">
                      学生认证
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="没有匹配的用户" :image-size="80" />
          </template>
        </el-table>

        <div class="pagination">
          <el-pagination
            v-model:current-page="query.pageNum"
            :page-size="query.pageSize"
            :total="total"
            layout="total, prev, pager, next"
            small
            @current-change="loadData"
          />
        </div>
      </template>

      <el-result
        v-else
        icon="error"
        title="数据加载失败"
        sub-title="请检查后端服务是否启动、网络是否正常"
      >
        <template #extra>
          <el-button type="primary" @click="loadData">重新加载</el-button>
        </template>
      </el-result>
    </div>

    <!-- 用户详情弹窗 -->
    <el-dialog v-model="detailVisible" title="用户详情" width="880px" destroy-on-close>
      <div v-if="detail" v-loading="detailLoading" class="detail">
        <!-- 基本信息 -->
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="用户ID">{{ detail.user?.userId }}</el-descriptions-item>
          <el-descriptions-item label="昵称">{{ detail.user?.nickname }}</el-descriptions-item>
          <el-descriptions-item label="账号">
            {{ detail.user?.username || '未绑定账号' }}
          </el-descriptions-item>
          <el-descriptions-item label="手机号">
            {{ detail.user?.phone || '未绑定' }}
          </el-descriptions-item>
          <el-descriptions-item label="角色">{{ detail.user?.roleLabel }}</el-descriptions-item>
          <el-descriptions-item label="性别">{{ detail.genderLabel }}</el-descriptions-item>
          <el-descriptions-item label="信誉分">
            <span :style="{ color: detail.user?.creditLevelColor, fontWeight: 600 }">
              {{ detail.user?.creditScore }}
            </span>
            <el-tag size="small" effect="dark" :color="detail.user?.creditLevelColor" class="level-tag">
              {{ detail.user?.creditLevelLabel }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="认证状态">
            {{ detail.user?.studentVerifiedLabel }}
            <template v-if="detail.studentNo"> · {{ detail.studentNo }}</template>
          </el-descriptions-item>
          <el-descriptions-item label="姓名">{{ detail.realName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="学校">{{ detail.school || '—' }}</el-descriptions-item>
          <el-descriptions-item label="注册时间">{{ detail.createTime }}</el-descriptions-item>
          <el-descriptions-item label="简介" :span="3">
            {{ detail.intro || '—' }}
          </el-descriptions-item>
        </el-descriptions>

        <!-- 汇总数字 -->
        <div class="detail__stat">
          <div class="stat-item">
            <span class="stat-item__num">{{ detail.publishCount || 0 }}</span>
            <span class="stat-item__label">发布商品</span>
          </div>
          <div class="stat-item">
            <span class="stat-item__num">{{ detail.orderCount || 0 }}</span>
            <span class="stat-item__label">订单</span>
          </div>
          <div class="stat-item">
            <span class="stat-item__num">{{ detail.reviewCount || 0 }}</span>
            <span class="stat-item__label">收到评价</span>
          </div>
          <div class="stat-item">
            <span class="stat-item__num">{{ detail.tradeAmount || 0 }}</span>
            <span class="stat-item__label">累计成交额</span>
          </div>
        </div>

        <!-- 信誉分变动 -->
        <el-collapse v-model="activePanel">
          <el-collapse-item name="credit" title="信誉分变动记录">
            <el-table :data="detail.creditLogs || []" size="small" border max-height="220">
              <el-table-column label="时间" prop="createTime" width="160" align="center" />
              <el-table-column label="变动" width="80" align="center">
                <template #default="{ row }">
                  <span :class="row.changeScore >= 0 ? 'delta--up' : 'delta--down'">
                    {{ row.changeScore > 0 ? '+' : '' }}{{ row.changeScore }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="变动前后" width="110" align="center">
                <template #default="{ row }">{{ row.beforeScore }} → {{ row.afterScore }}</template>
              </el-table-column>
              <el-table-column label="类型" prop="typeLabel" width="100" align="center" />
              <el-table-column label="原因" prop="reason" min-width="180" show-overflow-tooltip />
              <template #empty>
                <el-empty description="暂无信誉分变动" :image-size="60" />
              </template>
            </el-table>
          </el-collapse-item>

          <!-- 发布商品 -->
          <el-collapse-item name="products" title="发布商品">
            <el-table :data="detail.products || []" size="small" border max-height="240">
              <el-table-column label="商品" min-width="200" show-overflow-tooltip>
                <template #default="{ row }">{{ row.title }}</template>
              </el-table-column>
              <el-table-column label="价格" width="100" align="center">
                <template #default="{ row }">¥{{ row.price }}</template>
              </el-table-column>
              <el-table-column label="状态" width="92" align="center">
                <template #default="{ row }">
                  <el-tag size="small" effect="plain">{{ row.statusLabel }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="浏览" width="76" align="center">
                <template #default="{ row }">{{ row.viewCount || 0 }}</template>
              </el-table-column>
              <el-table-column label="收藏" width="76" align="center">
                <template #default="{ row }">{{ row.favoriteCount || 0 }}</template>
              </el-table-column>
              <el-table-column prop="createTime" label="发布时间" width="160" align="center" />
              <template #empty>
                <el-empty description="该用户还没有发布商品" :image-size="60" />
              </template>
            </el-table>
          </el-collapse-item>

          <!-- 订单记录 -->
          <el-collapse-item name="orders" title="订单记录">
            <el-table :data="detail.orders || []" size="small" border max-height="240">
              <el-table-column prop="orderNo" label="订单号" width="190" />
              <el-table-column label="商品" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">{{ row.productTitle || '—' }}</template>
              </el-table-column>
              <el-table-column label="金额" width="100" align="center">
                <template #default="{ row }">¥{{ row.price }}</template>
              </el-table-column>
              <el-table-column label="买家" width="110" show-overflow-tooltip>
                <template #default="{ row }">{{ row.buyerNickname }}</template>
              </el-table-column>
              <el-table-column label="卖家" width="110" show-overflow-tooltip>
                <template #default="{ row }">{{ row.sellerNickname }}</template>
              </el-table-column>
              <el-table-column label="状态" width="90" align="center">
                <template #default="{ row }">
                  <el-tag size="small" effect="plain">{{ row.statusLabel }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="createTime" label="下单时间" width="160" align="center" />
              <template #empty>
                <el-empty description="暂无订单记录" :image-size="60" />
              </template>
            </el-table>
          </el-collapse-item>

          <!-- 评价记录 -->
          <el-collapse-item name="reviews" title="收到的评价">
            <el-table :data="detail.reviews || []" size="small" border max-height="240">
              <el-table-column label="评价人" width="130" show-overflow-tooltip>
                <template #default="{ row }">{{ row.reviewerNickname }}</template>
              </el-table-column>
              <el-table-column label="星级" width="120" align="center">
                <template #default="{ row }">
                  <el-rate :model-value="row.stars" disabled allow-half />
                </template>
              </el-table-column>
              <el-table-column label="内容" min-width="200" show-overflow-tooltip>
                <template #default="{ row }">{{ row.content || '—' }}</template>
              </el-table-column>
              <el-table-column prop="createTime" label="评价时间" width="160" align="center" />
              <template #empty>
                <el-empty description="暂无评价记录" :image-size="60" />
              </template>
            </el-table>
          </el-collapse-item>
        </el-collapse>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 编辑资料弹窗 -->
    <el-dialog v-model="editVisible" title="编辑用户资料" width="520px" destroy-on-close>
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="88px">
        <el-form-item label="昵称" prop="nickname">
          <el-input
            v-model.trim="editForm.nickname"
            maxlength="20"
            show-word-limit
            placeholder="不超过 20 个字"
          />
        </el-form-item>
        <el-form-item label="头像">
          <el-input v-model.trim="editForm.avatar" placeholder="图片地址，如 /uploads/2026/10/xxx.png" />
        </el-form-item>
        <el-form-item label="性别" prop="gender">
          <el-radio-group v-model="editForm.gender">
            <el-radio :value="0">保密</el-radio>
            <el-radio :value="1">男</el-radio>
            <el-radio :value="2">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="个人简介" prop="intro">
          <el-input
            v-model.trim="editForm.intro"
            type="textarea"
            :rows="4"
            maxlength="100"
            show-word-limit
            placeholder="不超过 100 个字"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleEditSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 封禁 / 解封弹窗 -->
    <el-dialog v-model="statusVisible" :title="statusTarget?.status === 1 ? '解封账号' : '封禁账号'" width="480px">
      <el-alert
        v-if="statusTarget"
        type="warning"
        :closable="false"
        show-icon
        class="status-alert"
        :title="`用户：${statusTarget.nickname}`"
        :description="
          statusTarget.status === 1
            ? '解封后该用户可以重新登录并继续使用平台。'
            : '封禁后该用户将无法登录，其在架商品会立即全部下架。'
        "
      />
      <el-form ref="statusFormRef" :model="statusForm" :rules="statusRules" label-width="88px">
        <el-form-item label="备注" prop="reason">
          <el-input
            v-model.trim="statusForm.reason"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="填写处理依据，会随通知发给用户"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="statusVisible = false">取消</el-button>
        <el-button
          :type="statusTarget?.status === 1 ? 'success' : 'danger'"
          :loading="submitting"
          @click="handleStatusSubmit"
        >
          确认{{ statusTarget?.status === 1 ? '解封' : '封禁' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 分配角色弹窗 -->
    <el-dialog v-model="roleVisible" title="分配角色" width="440px" destroy-on-close>
      <el-alert
        v-if="roleTarget"
        type="info"
        :closable="false"
        show-icon
        class="status-alert"
        :title="`用户：${roleTarget.nickname}（当前：${roleTarget.roleLabel}）`"
        description="降级为普通用户会同时回收其学生认证与发布额度。"
      />
      <el-radio-group v-model="roleForm.role" class="role-group">
        <el-radio v-for="opt in roleOptions" :key="opt.value" :value="opt.value">
          {{ opt.label }}
        </el-radio>
      </el-radio-group>
      <template #footer>
        <el-button @click="roleVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleRoleSubmit">确认</el-button>
      </template>
    </el-dialog>

    <!-- 调整信誉分弹窗 -->
    <el-dialog v-model="creditVisible" title="调整信誉分" width="480px" destroy-on-close>
      <el-alert
        v-if="creditTarget"
        type="info"
        :closable="false"
        show-icon
        class="status-alert"
        :title="`用户：${creditTarget.nickname}`"
        :description="`当前信誉分 ${creditTarget.creditScore} 分（${creditTarget.creditLevelLabel}），调整会写入流水，用户可在小程序查看明细。`"
      />
      <el-form ref="creditFormRef" :model="creditForm" :rules="creditRules" label-width="88px">
        <el-form-item label="调整方向" prop="direction">
          <el-radio-group v-model="creditForm.direction">
            <el-radio-button value="add">加分</el-radio-button>
            <el-radio-button value="minus">减分</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="调整分值" prop="points">
          <el-input-number v-model="creditForm.points" :min="1" :max="100" controls-position="right" />
          <span class="form-hint">调整后预计：{{ creditPreview }} 分</span>
        </el-form-item>
        <el-form-item label="调整原因" prop="reason">
          <el-input
            v-model.trim="creditForm.reason"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="例如：举报核实存在虚假描述，扣除 10 分"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="creditVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreditSubmit">确认调整</el-button>
      </template>
    </el-dialog>

    <!-- 学生认证审核弹窗 -->
    <el-dialog v-model="authVisible" title="学生认证审核" width="520px" destroy-on-close>
      <el-alert
        v-if="authTarget"
        type="info"
        :closable="false"
        show-icon
        class="status-alert"
        :title="`用户：${authTarget.nickname}`"
        description="通过后该用户角色将升级为「认证学生」并获得发布商品的权限；驳回需填写原因。"
      />
      <el-form ref="authFormRef" :model="authForm" :rules="authRules" label-width="88px">
        <el-form-item label="审核结果" prop="status">
          <el-radio-group v-model="authForm.status">
            <el-radio :value="1">通过</el-radio>
            <el-radio :value="2">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="authForm.status === 2" label="驳回原因" prop="rejectReason">
          <el-input
            v-model.trim="authForm.rejectReason"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="请填写驳回原因，将展示给申请人"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="authVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleAuthSubmit">提交审核</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, Refresh, Search } from '@element-plus/icons-vue'
import {
  auditUserStudentAuth,
  getUserDetail,
  pageUsers,
  updateUserProfile,
  updateUserRole,
  updateUserStatus
} from '@/api/user'
import { adjustCredit } from '@/api/credit'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
/** 客服只读：隐藏所有写操作入口 */
const isReadOnly = computed(() => userStore.isReadOnly)

/**
 * 顶部标签。全部/普通用户/认证学生/客服/管理员按角色过滤，
 * 「已封禁」按状态过滤——它们与搜索栏条件是「与」的关系。
 */
const tabs = [
  { key: 'all', label: '全部' },
  { key: 'user', label: '普通用户', role: 'user' },
  { key: 'student', label: '认证学生', role: 'student' },
  { key: 'customer_service', label: '客服', role: 'customer_service' },
  { key: 'admin', label: '管理员', role: 'admin' },
  { key: 'banned', label: '已封禁', status: 1 }
]

const activeTab = ref('all')
const registerRange = ref([])

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  role: null,
  status: null,
  studentVerified: null,
  minCredit: null,
  maxCredit: null,
  startTime: null,
  endTime: null
})

const list = ref([])
const total = ref(0)
const loading = ref(false)
const error = ref(false)

const roleType = role => {
  switch (role) {
    case 'admin':
      return 'danger'
    case 'customer_service':
      return 'warning'
    case 'student':
      return 'success'
    default:
      return 'info'
  }
}

const loadData = async () => {
  loading.value = true
  error.value = false
  try {
    const params = { ...query }
    if (registerRange.value && registerRange.value.length === 2) {
      params.startTime = registerRange.value[0]
      params.endTime = registerRange.value[1]
    } else {
      params.startTime = undefined
      params.endTime = undefined
    }
    // 空值不传给后端，避免 0 / '' 被当成有效筛选条件
    Object.keys(params).forEach(key => {
      if (params[key] === '' || params[key] === null || params[key] === undefined) {
        delete params[key]
      }
    })
    const data = await pageUsers(params)
    list.value = data.records || []
    total.value = Number(data.total || 0)
  } catch (e) {
    error.value = true
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  query.pageNum = 1
  loadData()
}

const handleReset = () => {
  query.keyword = ''
  query.role = null
  query.status = null
  query.studentVerified = null
  query.minCredit = null
  query.maxCredit = null
  query.pageNum = 1
  registerRange.value = []
  activeTab.value = 'all'
  loadData()
}

const handleTabChange = name => {
  const tab = tabs.find(item => item.key === name)
  query.role = tab?.role || null
  query.status = tab?.status ?? null
  query.pageNum = 1
  loadData()
}

// ---------------- 详情 ----------------
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const activePanel = ref(['credit'])

const openDetail = async row => {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getUserDetail(row.userId)
  } finally {
    detailLoading.value = false
  }
}

// ---------------- 编辑资料 ----------------
const editVisible = ref(false)
const editFormRef = ref()
const submitting = ref(false)
const editTarget = ref(null)
const editForm = reactive({ nickname: '', avatar: '', gender: 0, intro: '' })

const editRules = {
  nickname: [
    { max: 20, message: '昵称不能超过 20 个字', trigger: 'blur' }
  ],
  intro: [{ max: 100, message: '个人简介不能超过 100 个字', trigger: 'blur' }]
}

const openEdit = row => {
  editTarget.value = row
  editForm.nickname = row.nickname || ''
  editForm.avatar = row.avatar || ''
  editForm.gender = row.gender === 1 || row.gender === 2 ? row.gender : 0
  editForm.intro = row.intro || ''
  editVisible.value = true
}

const handleEditSubmit = async () => {
  const valid = await editFormRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await updateUserProfile(editTarget.value.userId, { ...editForm })
    ElMessage.success('资料已更新')
    editVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

// ---------------- 封禁 / 解封 ----------------
const statusVisible = ref(false)
const statusFormRef = ref()
const statusTarget = ref(null)
const statusForm = reactive({ reason: '' })

const statusRules = {
  reason: [
    {
      validator: (rule, value, callback) => {
        // 封禁必须写原因：没有依据的封禁事后无法向用户解释
        if (statusTarget.value?.status !== 1 && !value) {
          callback(new Error('封禁必须填写原因'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ]
}

const openStatus = row => {
  statusTarget.value = row
  statusForm.reason = ''
  statusVisible.value = true
}

const handleStatusSubmit = async () => {
  const valid = await statusFormRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const target = statusTarget.value
    await updateUserStatus(target.userId, {
      status: target.status === 1 ? 0 : 1,
      reason: statusForm.reason
    })
    ElMessage.success(target.status === 1 ? '已解封' : '已封禁')
    statusVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

// ---------------- 分配角色 ----------------
const roleOptions = [
  { label: '普通用户', value: 'user' },
  { label: '认证学生', value: 'student' },
  { label: '客服', value: 'customer_service' },
  { label: '管理员', value: 'admin' }
]
const roleVisible = ref(false)
const roleTarget = ref(null)
const roleForm = reactive({ role: 'user' })

const handleCommand = (cmd, row) => {
  if (cmd === 'role') {
    roleTarget.value = row
    roleForm.role = row.role
    roleVisible.value = true
  } else if (cmd === 'credit') {
    openCredit(row)
  } else if (cmd === 'auth') {
    openAuth(row)
  }
}

const handleRoleSubmit = async () => {
  submitting.value = true
  try {
    await updateUserRole(roleTarget.value.userId, roleForm.role)
    ElMessage.success('角色已更新')
    roleVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

// ---------------- 调整信誉分 ----------------
const creditVisible = ref(false)
const creditFormRef = ref()
const creditTarget = ref(null)
const creditForm = reactive({ direction: 'add', points: 5, reason: '' })

const creditRules = {
  points: [{ required: true, message: '请填写调整分值', trigger: 'blur' }],
  reason: [{ required: true, message: '调整原因不能为空', trigger: 'blur' }]
}

/** 预览调整后分数，与后端 clamp(0,150) 同算法 */
const creditPreview = computed(() => {
  const base = creditTarget.value?.creditScore ?? 0
  const delta = creditForm.direction === 'add' ? creditForm.points : -creditForm.points
  return Math.min(150, Math.max(0, base + delta))
})

const openCredit = row => {
  creditTarget.value = row
  creditForm.direction = 'add'
  creditForm.points = 5
  creditForm.reason = ''
  creditVisible.value = true
}

const handleCreditSubmit = async () => {
  const valid = await creditFormRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const changeScore = creditForm.direction === 'add' ? creditForm.points : -creditForm.points
    await adjustCredit(creditTarget.value.userId, { changeScore, reason: creditForm.reason })
    ElMessage.success(`已${changeScore > 0 ? '加' : '扣'} ${Math.abs(changeScore)} 分`)
    creditVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

// ---------------- 学生认证审核 ----------------
const authVisible = ref(false)
const authFormRef = ref()
const authTarget = ref(null)
const authForm = reactive({ status: 1, rejectReason: '' })

const authRules = {
  status: [{ required: true, message: '请选择审核结果', trigger: 'change' }],
  rejectReason: [
    {
      validator: (rule, value, callback) => {
        if (authForm.status === 2 && !value) {
          callback(new Error('驳回时必须填写驳回原因'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ]
}

const openAuth = row => {
  authTarget.value = row
  authForm.status = 1
  authForm.rejectReason = ''
  authVisible.value = true
}

const handleAuthSubmit = async () => {
  const valid = await authFormRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await auditUserStudentAuth(authTarget.value.userId, {
      status: authForm.status,
      rejectReason: authForm.status === 2 ? authForm.rejectReason : undefined
    })
    ElMessage.success(authForm.status === 1 ? '认证已通过' : '认证已驳回')
    authVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.user-tabs {
  margin-bottom: 4px;
}

.user-tabs :deep(.el-tabs__header) {
  margin-bottom: 8px;
}

.range-sep {
  margin: 0 6px;
  color: #909399;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-cell__info {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
  min-width: 0;
}

.user-cell__name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #303133;
}

.user-cell__sub {
  font-size: 12px;
  color: #a8abb2;
}

.score-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.score-cell__num {
  font-size: 15px;
  font-weight: 700;
}

.level-tag {
  border: none;
  color: #fff;
}

.text-muted {
  color: #c0c4cc;
  font-size: 13px;
}

.detail__stat {
  display: flex;
  gap: 12px;
  margin: 14px 0;
}

.stat-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 10px 0;
  background: #fafafa;
  border-radius: 6px;
}

.stat-item__num {
  font-size: 18px;
  font-weight: 700;
  color: #303133;
}

.stat-item__label {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.delta--up {
  color: #67c23a;
  font-weight: 600;
}

.delta--down {
  color: #f56c6c;
  font-weight: 600;
}

.status-alert {
  margin-bottom: 16px;
}

.role-group {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.form-hint {
  margin-left: 12px;
  font-size: 12px;
  color: #909399;
}
</style>
