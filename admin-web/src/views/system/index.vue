<!-- 文件路径: admin-web/src/views/system/index.vue -->
<template>
  <div class="page-container">
    <el-row :gutter="16">
      <el-col :xs="24" :lg="10">
        <el-card shadow="never" class="card">
          <template #header>
            <div class="card__head">
              <span class="card__title">当前账号</span>
              <el-tag size="small" type="danger" effect="light">管理员</el-tag>
            </div>
          </template>

          <div v-loading="loading" class="account">
            <el-avatar :size="56" :src="profile.avatar" class="account__avatar">
              {{ (profile.nickname || 'A').slice(0, 1) }}
            </el-avatar>
            <div class="account__main">
              <div class="account__name">{{ profile.nickname || '—' }}</div>
              <div class="account__role">{{ profile.roleLabel || '管理员' }}</div>
            </div>
          </div>

          <el-descriptions :column="1" border class="account__desc">
            <el-descriptions-item label="登录账号">
              {{ profile.username || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="注册方式">
              {{ profile.registerTypeLabel || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="信誉分">
              {{ profile.creditScore ?? '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="注册时间">
              {{ profile.createTime || '—' }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="14">
        <el-card shadow="never" class="card">
          <template #header>
            <span class="card__title">账号安全</span>
          </template>

          <div class="security">
            <div class="security__item">
              <div class="security__text">
                <div class="security__label">登录密码</div>
                <div class="security__hint">初始密码为 123456，建议首次登录后立即修改</div>
              </div>
              <el-button type="primary" plain :icon="Key" @click="passwordVisible = true">
                修改密码
              </el-button>
            </div>
            <el-divider />
            <div class="security__item">
              <div class="security__text">
                <div class="security__label">客服账号管理</div>
                <div class="security__hint">
                  新增客服、重置密码、启停用账号
                </div>
              </div>
              <el-button link type="primary" @click="$router.push('/system/account')">
                去账号管理
              </el-button>
            </div>
            <el-divider />
            <div class="security__item">
              <div class="security__text">
                <div class="security__label">操作日志</div>
                <div class="security__hint">记录关键管理操作，便于追溯</div>
              </div>
              <el-button link type="primary" @click="$router.push('/system/log')">
                去查看
              </el-button>
            </div>
          </div>
        </el-card>

        <el-card shadow="never" class="card">
          <template #header>
            <span class="card__title">角色权限矩阵</span>
          </template>
          <el-table :data="permissions" size="small" border>
            <el-table-column prop="role" label="角色" width="110" />
            <el-table-column prop="channel" label="可访问端" width="150" />
            <el-table-column prop="scope" label="权限范围" min-width="260" />
          </el-table>
          <p class="matrix-tip">
            权限在服务端拦截器统一判定：客服账号除登录、登出、个人信息、绑定账号与改密外，
            一切非 GET 请求返回 403。
          </p>
        </el-card>
      </el-col>
    </el-row>

    <ChangePasswordDialog v-model="passwordVisible" />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Key } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue'

const userStore = useUserStore()
const loading = ref(false)
const passwordVisible = ref(false)

const profile = reactive({
  username: '',
  nickname: '',
  avatar: '',
  roleLabel: '',
  registerTypeLabel: '',
  creditScore: null,
  createTime: ''
})

const permissions = [
  { role: '普通用户', channel: '小程序', scope: '浏览、搜索、收藏、下单、评价，不可发布商品' },
  { role: '认证学生', channel: '小程序', scope: '普通用户全部权限 + 发布商品（上限 20 件）' },
  { role: '客服', channel: '管理端（只读）', scope: '查看用户、商品、认证、订单、举报；不可新增/修改/删除/审核' },
  { role: '管理员', channel: '管理端', scope: '全部权限：审核、分类、轮播、用户、订单、举报、系统配置' }
]

onMounted(async () => {
  loading.value = true
  try {
    const me = await userStore.fetchMe()
    Object.assign(profile, me)
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.card {
  margin-bottom: 16px;
  border-radius: 8px;
}

.card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card__title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.account {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 16px;
}

.account__avatar {
  background: linear-gradient(135deg, #409eff, #8b5cf6);
  color: #fff;
  font-size: 20px;
}

.account__name {
  font-size: 17px;
  font-weight: 600;
  color: #303133;
}

.account__role {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.account__desc {
  margin-top: 4px;
}

.security__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.security__label {
  font-size: 14px;
  color: #303133;
}

.security__hint {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.matrix-tip {
  margin: 12px 0 0;
  font-size: 12px;
  color: #a8abb2;
  line-height: 1.7;
}
</style>
