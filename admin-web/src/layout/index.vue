<!-- 文件路径: admin-web/src/layout/index.vue -->
<template>
  <el-container class="layout">
    <!-- 左侧固定侧边栏 -->
    <el-aside :width="collapsed ? '64px' : '220px'" class="layout__aside">
      <div class="logo">
        <div class="logo__mark">校</div>
        <span v-show="!collapsed" class="logo__text">校园二手交易平台</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        :collapse-transition="false"
        router
        background-color="#1f2d3d"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
        class="layout__menu"
      >
        <!--
          菜单支持一级项与「分组」两种形态（模块11 起「系统管理」是分组）。
          分组用 el-sub-menu 折叠，展开后是子菜单项；
          没有 children 的仍然渲染成平级项。
        -->
        <template v-for="item in menus" :key="item.path">
          <el-sub-menu v-if="item.children && item.children.length" :index="item.path">
            <template #title>
              <el-icon><component :is="item.meta.icon" /></el-icon>
              <span>{{ item.meta.title }}</span>
            </template>
            <el-menu-item v-for="child in item.children" :key="child.path" :index="child.path">
              <el-icon v-if="child.meta.icon"><component :is="child.meta.icon" /></el-icon>
              <template #title>{{ child.meta.title }}</template>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="item.path">
            <el-icon><component :is="item.meta.icon" /></el-icon>
            <template #title>{{ item.meta.title }}</template>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <!-- 顶部导航 -->
      <el-header class="layout__header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="collapsed = !collapsed">
            <Fold v-if="!collapsed" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path">
              {{ item.meta.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="header-right">
          <el-tag
            v-if="userStore.userInfo"
            size="small"
            effect="light"
            class="role-tag"
            :class="userStore.isAdmin ? 'role-tag--admin' : 'role-tag--cs'"
          >
            {{ userStore.roleLabel || userStore.role }}
          </el-tag>
          <el-dropdown trigger="click" @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="32" :src="userStore.userInfo?.avatar" class="user-avatar">
                {{ (userStore.userInfo?.nickname || 'A').slice(0, 1) }}
              </el-avatar>
              <span class="user-name">{{ userStore.userInfo?.nickname || '管理员' }}</span>
              <el-icon class="user-arrow"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-if="userStore.isAdmin" command="profile" :icon="Setting">
                  账号设置
                </el-dropdown-item>
                <el-dropdown-item command="password" :icon="Key">修改密码</el-dropdown-item>
                <el-dropdown-item command="logout" divided :icon="SwitchButton">
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 客服只读提示条 -->
      <div v-if="userStore.isReadOnly" class="readonly-bar">
        <el-icon><Lock /></el-icon>
        <span>当前为客服只读模式：可查看全部数据，新增 / 修改 / 删除 / 审核等操作已禁用</span>
      </div>

      <!-- 主内容区 -->
      <el-main class="layout__main">
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>

    <ChangePasswordDialog v-model="passwordVisible" />
  </el-container>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { Key, Lock, Setting, SwitchButton } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { routes } from '@/router'
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const collapsed = ref(false)
const passwordVisible = ref(false)

// 菜单来自路由表，避免"加了路由忘了加菜单"；
// meta.roles 存在时按当前角色过滤（如系统管理仅管理员可见）
const menus = computed(() => {
  const layoutRoute = routes.find(r => r.path === '/' && r.children)
  return (layoutRoute?.children || [])
    .filter(item => !item.meta?.hidden)
    .filter(item => !item.meta?.roles || item.meta.roles.includes(userStore.role))
    .map(item => {
      // 子路由的 path 是相对的（'account'），拼成绝对路径后
      // 才能既做菜单 index，又能被 el-menu 的 router 模式正确跳转
      const base = '/' + item.path
      const children = (item.children || [])
        .filter(child => !child.meta?.hidden)
        .filter(child => !child.meta?.roles || child.meta.roles.includes(userStore.role))
        .map(child => ({ ...child, path: base + '/' + child.path }))
      return { ...item, path: base, children }
    })
})

const activeMenu = computed(() => route.path)

const breadcrumbs = computed(() => route.matched.filter(item => item.meta?.title))

const handleCommand = async command => {
  if (command === 'profile') {
    router.push('/system/profile')
    return
  }
  if (command === 'password') {
    passwordVisible.value = true
    return
  }
  if (command !== 'logout') return
  await ElMessageBox.confirm('确认退出登录吗？', '提示', {
    type: 'warning',
    confirmButtonText: '退出',
    cancelButtonText: '取消'
  })
  await userStore.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<style scoped>
.layout {
  height: 100vh;
}

.layout__aside {
  background: #1f2d3d;
  transition: width 0.2s;
  overflow-x: hidden;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  color: #fff;
  font-weight: 600;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  white-space: nowrap;
}

.logo__mark {
  width: 30px;
  height: 30px;
  flex: none;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  background: linear-gradient(135deg, #409eff, #8b5cf6);
}

.logo__text {
  font-size: 15px;
  overflow: hidden;
}

.layout__menu {
  border-right: none;
}

.layout__header {
  height: 60px;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.collapse-btn {
  font-size: 18px;
  cursor: pointer;
  color: #5a5e66;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.role-tag {
  border-radius: 10px;
  font-weight: 500;
}

.role-tag--admin {
  color: #f56c6c;
  background: #fef0f0;
  border-color: #fbc4c4;
}

.role-tag--cs {
  color: #409eff;
  background: #ecf5ff;
  border-color: #b3d8ff;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
  padding: 4px 8px;
  border-radius: 20px;
  transition: background 0.16s;
}

.user-info:hover {
  background: #f5f7fa;
}

.user-avatar {
  background: linear-gradient(135deg, #409eff, #8b5cf6);
  color: #fff;
  font-size: 14px;
}

.user-name {
  font-size: 14px;
  color: #303133;
}

.user-arrow {
  font-size: 12px;
  color: #909399;
}

.readonly-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 20px;
  font-size: 13px;
  color: #8a6d3b;
  background: #fdf6ec;
  border-bottom: 1px solid #faecd8;
}

.readonly-bar .el-icon {
  color: #e6a23c;
  font-size: 15px;
}

.layout__main {
  padding: 0;
  background: var(--cm-page-bg);
  overflow-y: auto;
}

.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.18s ease;
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.fade-slide-leave-to {
  opacity: 0;
}
</style>
