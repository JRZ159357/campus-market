<!-- 文件路径: admin-web/src/views/login/index.vue -->
<template>
  <div class="login-page">
    <div class="login-page__glow login-page__glow--one"></div>
    <div class="login-page__glow login-page__glow--two"></div>

    <div class="login-shell">
      <!-- 左侧品牌区 -->
      <section class="brand">
        <div class="brand__logo">
          <span>校</span>
        </div>
        <h1 class="brand__title">校园二手交易管理系统</h1>
        <p class="brand__subtitle">Campus Second-hand Trading Platform</p>
        <ul class="brand__features">
          <li>
            <el-icon><Stamp /></el-icon>
            <span>学生认证审核与四级角色权限体系</span>
          </li>
          <li>
            <el-icon><Goods /></el-icon>
            <span>商品审核、分类维护与首页轮播配置</span>
          </li>
          <li>
            <el-icon><DataLine /></el-icon>
            <span>交易数据看板与客服协同工作台</span>
          </li>
        </ul>
        <p class="brand__footer">北华大学 · 计算机科学技术学院 · 2026 届毕业设计</p>
      </section>

      <!-- 右侧登录卡片 -->
      <section class="panel">
        <div class="panel__head">
          <h2 class="panel__title">欢迎登录</h2>
          <p class="panel__desc">请使用管理员或客服账号登录后台</p>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          size="large"
          label-position="top"
          class="panel__form"
          @keyup.enter="handleLogin"
        >
          <el-form-item prop="username">
            <el-input
              v-model.trim="form.username"
              placeholder="请输入账号"
              :prefix-icon="User"
              clearable
              autocomplete="username"
            />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model.trim="form.password"
              type="password"
              placeholder="请输入密码"
              :prefix-icon="Lock"
              show-password
              autocomplete="current-password"
            />
          </el-form-item>

          <transition name="fade">
            <div v-if="errorMsg" class="panel__error">
              <el-icon><WarningFilled /></el-icon>
              <span>{{ errorMsg }}</span>
            </div>
          </transition>

          <el-form-item class="panel__submit">
            <el-button
              class="login-btn"
              :loading="loading"
              :disabled="loading"
              @click="handleLogin"
            >
              {{ loading ? '登录中...' : '登 录' }}
            </el-button>
          </el-form-item>
        </el-form>

        <div class="panel__divider"><span>初始演示账号</span></div>

        <div class="panel__accounts">
          <button type="button" class="account" @click="fillAccount('admin')">
            <span class="account__role account__role--admin">管理员</span>
            <span class="account__value">admin / 123456</span>
          </button>
          <button type="button" class="account" @click="fillAccount('service01')">
            <span class="account__role account__role--cs">客服</span>
            <span class="account__value">service01 / 123456</span>
          </button>
        </div>

        <p class="panel__tip">客服账号为只读模式，仅可查看数据，不能修改平台内容</p>
      </section>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { DataLine, Goods, Lock, Stamp, User, WarningFilled } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)
const errorMsg = ref('')
const form = reactive({ username: 'admin', password: '123456' })

const rules = {
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { min: 4, max: 20, message: '账号长度为 4-20 位', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度为 6-20 位', trigger: 'blur' }
  ]
}

const fillAccount = username => {
  form.username = username
  form.password = '123456'
  errorMsg.value = ''
  formRef.value?.clearValidate()
}

const handleLogin = async () => {
  if (loading.value) return
  errorMsg.value = ''
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const data = await userStore.login(form)
    if (data.role !== 'admin' && data.role !== 'customer_service') {
      await userStore.logout()
      errorMsg.value = '该账号无管理端访问权限'
      return
    }
    ElMessage.success(`欢迎回来，${data.nickname || data.roleLabel}`)
    router.push(route.query.redirect || '/dashboard')
  } catch (e) {
    errorMsg.value = e.message || '登录失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
  background: linear-gradient(135deg, #1b2a4a 0%, #243b6b 42%, #3d4f9e 100%);
  overflow: hidden;
}

.login-page__glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(10px);
  pointer-events: none;
}

.login-page__glow--one {
  width: 520px;
  height: 520px;
  top: -180px;
  left: -120px;
  background: radial-gradient(circle, rgba(64, 158, 255, 0.45), transparent 68%);
}

.login-page__glow--two {
  width: 620px;
  height: 620px;
  right: -220px;
  bottom: -260px;
  background: radial-gradient(circle, rgba(139, 92, 246, 0.42), transparent 68%);
}

.login-shell {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 1040px;
  display: grid;
  grid-template-columns: 1fr 420px;
  align-items: center;
  gap: 56px;
}

/* 左侧品牌区 */
.brand {
  color: #fff;
  padding-right: 12px;
}

.brand__logo {
  width: 64px;
  height: 64px;
  border-radius: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #409eff, #8b5cf6);
  box-shadow: 0 10px 24px rgba(64, 158, 255, 0.35);
  font-size: 30px;
  font-weight: 700;
  margin-bottom: 26px;
}

.brand__title {
  margin: 0 0 10px;
  font-size: 34px;
  font-weight: 700;
  letter-spacing: 1px;
  line-height: 1.3;
}

.brand__subtitle {
  margin: 0 0 34px;
  font-size: 13px;
  letter-spacing: 1.6px;
  color: rgba(255, 255, 255, 0.62);
  text-transform: uppercase;
}

.brand__features {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.brand__features li {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.88);
}

.brand__features .el-icon {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  background: rgba(255, 255, 255, 0.12);
  color: #9ecbff;
  font-size: 16px;
  flex: none;
}

.brand__footer {
  margin: 46px 0 0;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.42);
}

/* 右侧卡片 */
.panel {
  background: #fff;
  border-radius: 16px;
  padding: 34px 34px 26px;
  box-shadow: 0 24px 60px rgba(8, 20, 48, 0.35);
}

.panel__title {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: 700;
  color: #1f2d3d;
}

.panel__desc {
  margin: 0 0 22px;
  font-size: 13px;
  color: #909399;
}

.panel__form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.panel__form :deep(.el-input__wrapper) {
  height: 44px;
  border-radius: 8px;
  box-shadow: 0 0 0 1px #dcdfe6 inset;
  transition: box-shadow 0.18s;
}

.panel__form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #409eff inset;
}

.panel__error {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: -4px 0 14px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #fef0f0;
  color: #f56c6c;
  font-size: 12px;
}

.panel__submit :deep(.el-form-item__content) {
  margin-top: 4px;
}

.login-btn {
  width: 100%;
  height: 44px;
  border: none;
  border-radius: 24px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 2px;
  color: #fff;
  background: linear-gradient(135deg, #409eff 0%, #6a7bf7 55%, #8b5cf6 100%);
  box-shadow: 0 10px 22px rgba(64, 158, 255, 0.32);
  transition: transform 0.16s, box-shadow 0.16s, filter 0.16s;
}

.login-btn:hover {
  filter: brightness(1.06);
  transform: translateY(-1px);
  box-shadow: 0 14px 26px rgba(64, 158, 255, 0.4);
}

.login-btn:active {
  transform: translateY(0);
}

.login-btn.is-disabled,
.login-btn.is-loading {
  background: linear-gradient(135deg, #a9c9ff 0%, #b9c0fb 100%);
  box-shadow: none;
}

.panel__divider {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 6px 0 14px;
  color: #c0c4cc;
  font-size: 12px;
}

.panel__divider::before,
.panel__divider::after {
  content: '';
  height: 1px;
  flex: 1;
  background: #ebeef5;
}

.panel__accounts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.account {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 9px 12px;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  background: #fafbfc;
  cursor: pointer;
  text-align: left;
  transition: all 0.16s;
  font-family: inherit;
}

.account:hover {
  border-color: #b3d8ff;
  background: #f2f8ff;
}

.account__role {
  font-size: 11px;
  padding: 1px 7px;
  border-radius: 9px;
  line-height: 1.7;
}

.account__role--admin {
  color: #f56c6c;
  background: #fef0f0;
}

.account__role--cs {
  color: #409eff;
  background: #ecf5ff;
}

.account__value {
  font-size: 12px;
  color: #606266;
  font-family: Consolas, Monaco, monospace;
}

.panel__tip {
  margin: 16px 0 0;
  font-size: 12px;
  color: #b1b6bd;
  text-align: center;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.18s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@media (max-width: 960px) {
  .login-shell {
    grid-template-columns: 1fr;
    max-width: 420px;
    gap: 28px;
  }

  .brand {
    text-align: center;
    padding-right: 0;
  }

  .brand__logo {
    margin: 0 auto 18px;
  }

  .brand__title {
    font-size: 24px;
  }

  .brand__features,
  .brand__footer {
    display: none;
  }

  .brand__subtitle {
    margin-bottom: 0;
  }
}
</style>
