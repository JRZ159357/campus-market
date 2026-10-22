// 文件路径: admin-web/src/router/index.js
import { createRouter, createWebHashHistory } from 'vue-router'
import Layout from '@/layout/index.vue'

/**
 * 路由表即菜单来源：侧边栏根据 meta.title / meta.icon 递归生成。
 * meta.hidden = true 的路由不在菜单显示。
 * meta.roles = ['admin'] 时，仅这些角色可见（菜单过滤 + 路由守卫双重拦截）。
 */
export const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', hidden: true }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        // 仪表盘对管理员与客服都开放：客服排查纠纷时需要「这个商品是不是热点」
        // 这类上下文，把首页限定为管理员专属反而让客服没法判断问题的量级。
        // 后端 @RequireRole({admin, customer_service}) 是真正的权限边界
        meta: { title: '数据仪表盘', icon: 'DataLine' }
      },
      {
        path: 'student-auth',
        name: 'StudentAuth',
        component: () => import('@/views/student-auth/index.vue'),
        meta: { title: '学生认证审核', icon: 'Stamp' }
      },
      {
        path: 'user',
        name: 'UserManage',
        component: () => import('@/views/user/index.vue'),
        // 用户管理对管理员与客服都开放，客服登录后为只读（写按钮由 v-permission 移除）
        meta: { title: '用户管理', icon: 'User' }
      },
      {
        path: 'product/audit',
        name: 'ProductAudit',
        component: () => import('@/views/product/audit/index.vue'),
        meta: { title: '商品审核', icon: 'Goods' }
      },
      {
        path: 'order',
        name: 'Order',
        component: () => import('@/views/order/index.vue'),
        // 订单管理对管理员与客服都开放，客服登录后为只读（写按钮由 v-permission 移除）
        meta: { title: '订单管理', icon: 'Tickets' }
      },
      {
        path: 'customer-service',
        name: 'CustomerService',
        component: () => import('@/views/customer-service/index.vue'),
        // 客服工作台：管理员与客服都可进入，页面本身是只读监控
        meta: { title: '客服工作台', icon: 'ChatDotRound' }
      },
      {
        path: 'notice',
        name: 'Notice',
        component: () => import('@/views/notice/index.vue'),
        // 公告：管理员可发布/删除；客服只能看列表（发布按钮由 v-permission 移除）
        meta: { title: '通知管理', icon: 'Bell' }
      },
      {
        path: 'review',
        name: 'Review',
        component: () => import('@/views/review/index.vue'),
        // 评价管理：管理员与客服都能看，删除按钮仅管理员（v-permission + 后端 @RequireRole 双保险）
        meta: { title: '评价管理', icon: 'Star' }
      },
      {
        path: 'credit',
        name: 'Credit',
        component: () => import('@/views/credit/index.vue'),
        // 信誉分调整会写流水，属于高权限操作，只对管理员开放
        meta: { title: '信誉分管理', icon: 'Medal', roles: ['admin'] }
      },
      {
        path: 'report',
        name: 'ReportManage',
        component: () => import('@/views/report/index.vue'),
        // 举报管理对管理员与客服都开放：接单与处理是客服的本职工作，
        // 因此这两处不需要 roles 限制（后端 @RequireRole 与拦截器白名单是真正的边界）
        meta: { title: '举报管理', icon: 'Warning' }
      },
      {
        path: 'category',
        name: 'Category',
        component: () => import('@/views/category/index.vue'),
        meta: { title: '分类管理', icon: 'Files' }
      },
      {
        /*
         * 首页配置（模块12）：把原来单独的「轮播图」页扩成五个 Tab
         * （轮播图 / 公告 / 金刚区 / 热门推荐 / 新品推荐）。
         * 不放成五个菜单项：它们配置的是同一块屏幕，
         * 运营改完公告往往接着调金刚区，分成五个入口只会让人来回点。
         * roles 限定管理员：这些都是面向全站用户的门面配置，客服不需要动。
         */
        path: 'home',
        name: 'HomeConfig',
        component: () => import('@/views/home/index.vue'),
        meta: { title: '首页配置', icon: 'Picture', roles: ['admin'] }
      },
      {
        // 兼容老书签：原来叫 /banner，升级后指向首页配置的轮播图 Tab
        path: 'banner',
        name: 'BannerLegacy',
        redirect: '/home',
        meta: { hidden: true }
      },
      {
        /*
         * 系统管理（模块11 收尾）：从一个菜单项改成一个分组。
         *
         * 为什么改成子菜单而不是继续平铺：「管理端配置」类的入口已经有
         * 分类、首页配置、系统管理三个，再往顶层加「账号管理」「敏感词管理」
         * 会让侧边栏变成一长串平级项，运营每天用的商品/订单入口被挤到下面。
         * 分组之后「配置类」收在一起，日常操作区域更短。
         *
         * roles 只写在父级：Vue Router 的 route.meta 会把父子 meta 合并，
         * 子路由不带 roles 也会继承到 'admin'，因此客服手动敲
         * /system/account 同样会被守卫弹回仪表盘。
         */
        path: 'system',
        name: 'System',
        redirect: '/system/account',
        meta: { title: '系统管理', icon: 'Setting', roles: ['admin'] },
        children: [
          {
            path: 'account',
            name: 'AccountManage',
            component: () => import('@/views/system/account.vue'),
            meta: { title: '账号管理', icon: 'UserFilled' }
          },
          {
            path: 'sensitive-word',
            name: 'SensitiveWord',
            component: () => import('@/views/system/sensitive-word.vue'),
            meta: { title: '敏感词管理', icon: 'Filter' }
          },
          {
            path: 'log',
            name: 'OperationLog',
            component: () => import('@/views/system/log.vue'),
            meta: { title: '操作日志', icon: 'Document' }
          },
          {
            /*
             * 账号设置（原「系统管理」首页）不进侧边栏：
             * 它的核心内容「修改密码」在右上角下拉里已经有了，
             * 单独占一个菜单项属于重复入口。hidden 让它仍可直达，
             * 下拉菜单里给了入口，页面不至于变成孤儿。
             */
            path: 'profile',
            name: 'SystemProfile',
            component: () => import('@/views/system/index.vue'),
            meta: { title: '账号设置', icon: 'Setting', hidden: true }
          }
        ]
      },
      {
        // 兼容老书签与文档里的旧地址：操作日志搬到系统管理分组下之后，
        // /operation-log 仍然要能打开，否则收藏过的链接全部 404
        path: 'operation-log',
        name: 'OperationLogLegacy',
        redirect: '/system/log',
        meta: { hidden: true }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard'
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

const currentRole = () => {
  try {
    return JSON.parse(localStorage.getItem('userInfo') || 'null')?.role || ''
  } catch (e) {
    return ''
  }
}

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (to.path === '/login') {
    return next()
  }
  if (!token) {
    return next({ path: '/login', query: { redirect: to.fullPath } })
  }
  // 越权访问：无权限的角色直接回到仪表盘（防止手动改 URL 绕过菜单）
  const roles = to.meta?.roles
  if (Array.isArray(roles) && roles.length && !roles.includes(currentRole())) {
    return next('/dashboard')
  }
  next()
})

export default router
