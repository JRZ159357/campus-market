<!-- 文件路径: admin-web/src/views/dashboard/index.vue -->
<template>
  <div class="page-container dashboard">
    <!-- 顶部标题栏 -->
    <div class="dash-header">
      <div class="dash-header__left">
        <h2 class="dash-header__title">数据仪表盘</h2>
        <span class="dash-header__desc">全平台运营概览 · 数据每 5 分钟自动更新</span>
      </div>
      <div class="dash-header__right">
        <span v-if="summary.statTime" class="dash-header__time">
          统计时间 {{ summary.statTime }}
        </span>
        <!-- 刷新会清空服务端缓存重新统计，属于写操作，客服只读账号看不到 -->
        <el-button
          v-permission="'admin'"
          :icon="Refresh"
          :loading="refreshing"
          @click="handleRefresh"
        >
          刷新数据
        </el-button>
      </div>
    </div>

    <!-- 第一行：6 个核心指标卡片 -->
    <el-row :gutter="16" class="metric-row">
      <el-col v-for="card in metricCards" :key="card.key" :xs="24" :sm="12" :md="8" :lg="4">
        <el-card shadow="never" class="metric-card" :body-style="{ padding: '18px' }">
          <el-skeleton v-if="loading" animated :rows="2" />
          <template v-else>
            <div class="metric-card__top">
              <span class="metric-card__icon" :style="{ background: card.bg, color: card.color }">
                <el-icon :size="20"><component :is="card.icon" /></el-icon>
              </span>
              <span class="metric-card__label">{{ card.label }}</span>
              <!-- 待审核数红色角标：在售商品卡上提示有多少件等着审 -->
              <el-badge
                v-if="card.badge"
                :value="card.badge"
                type="danger"
                class="metric-card__badge"
              />
            </div>
            <div
              class="metric-card__value"
              :class="{ 'metric-card__value--blink': card.blink }"
              :style="{ color: card.color }"
            >
              {{ card.value }}
            </div>
            <div class="metric-card__foot">
              <!-- 环比箭头：上升绿、下降红；上升是好是坏由卡片自行指定 reverse -->
              <span
                v-if="card.growth !== null"
                class="metric-card__growth"
                :class="growthClass(card)"
              >
                <el-icon :size="12">
                  <component :is="card.growth >= 0 ? 'CaretTop' : 'CaretBottom'" />
                </el-icon>
                {{ Math.abs(card.growth).toFixed(1) }}%
              </span>
              <span class="metric-card__hint">{{ card.hint }}</span>
            </div>
          </template>
        </el-card>
      </el-col>
    </el-row>

    <!-- 第二行：趋势图（2/3） + 订单状态环形图（1/3） -->
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :lg="16">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">成交额与订单趋势</span>
              <el-radio-group v-model="trendDays" size="small" @change="loadTrend">
                <el-radio-button :value="7">近7日</el-radio-button>
                <el-radio-button :value="30">近30日</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <el-skeleton v-if="loading" animated :rows="5" />
          <el-empty v-else-if="!trendPoints.length" description="暂无数据" :image-size="90" />
          <div v-else ref="trendRef" class="chart chart--trend" />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="8">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">订单状态分布</span>
              <span class="card-subtitle">共 {{ orderTotal }} 笔</span>
            </div>
          </template>
          <el-skeleton v-if="loading" animated :rows="5" />
          <el-empty v-else-if="!orderDist.length" description="暂无数据" :image-size="90" />
          <div v-else ref="orderRef" class="chart chart--pie" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 第三行：分类柱状图（1/2） + 角色饼图与信誉分分布（1/2） -->
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <span class="card-title">商品分类分布</span>
          </template>
          <el-skeleton v-if="loading" animated :rows="5" />
          <el-empty v-else-if="!categoryDist.length" description="暂无数据" :image-size="90" />
          <div v-else ref="categoryRef" class="chart chart--bar" />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <!-- 按需求「两个小图上下排列」：角色饼图在上、信誉分柱状图在下。
             并排会挤成两个窄图，分类名和区间标签都会被截断。 -->
        <el-card shadow="never" class="chart-card chart-card--half">
          <template #header>
            <span class="card-title">用户角色分布</span>
          </template>
          <el-skeleton v-if="loading" animated :rows="4" />
          <el-empty v-else-if="!roleDist.length" description="暂无数据" :image-size="70" />
          <div v-else ref="roleRef" class="chart chart--half" />
        </el-card>
        <el-card shadow="never" class="chart-card chart-card--half">
          <template #header>
            <span class="card-title">信誉分分布</span>
          </template>
          <el-skeleton v-if="loading" animated :rows="4" />
          <el-empty v-else-if="!creditDist.length" description="暂无数据" :image-size="70" />
          <div v-else ref="creditRef" class="chart chart--half" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 第四行：卖家销量榜（1/2） + 热门商品榜（1/2） -->
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <span class="card-title">卖家销量榜 TOP10</span>
          </template>
          <el-skeleton v-if="loading" animated :rows="5" />
          <el-empty v-else-if="!ranking.topSellers.length" description="暂无数据" :image-size="90" />
          <el-table v-else :data="ranking.topSellers" size="small" class="rank-table">
            <el-table-column label="排名" width="60" align="center">
              <template #default="{ $index }">
                <span class="rank-badge" :class="`rank-badge--${$index + 1}`">{{ $index + 1 }}</span>
              </template>
            </el-table-column>
            <el-table-column label="卖家" min-width="130">
              <template #default="{ row }">
                <div class="seller-cell">
                  <el-avatar :size="26" :src="row.avatar" class="seller-cell__avatar">
                    {{ (row.nickname || '?').slice(0, 1) }}
                  </el-avatar>
                  <span class="seller-cell__name text-ellipsis">{{ row.nickname || '未设置昵称' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="productCount" label="商品数" width="76" align="center" />
            <el-table-column prop="soldCount" label="已售" width="66" align="center" />
            <el-table-column label="成交额" width="96" align="right">
              <template #default="{ row }">¥{{ formatAmount(row.amount) }}</template>
            </el-table-column>
            <el-table-column label="信誉分" width="88" align="center">
              <template #default="{ row }">
                <el-tag size="small" effect="light" :style="{ color: row.creditColor, borderColor: row.creditColor }">
                  {{ row.creditScore }} {{ row.creditLevel }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <span class="card-title">热门商品 TOP10</span>
          </template>
          <el-skeleton v-if="loading" animated :rows="5" />
          <el-empty v-else-if="!ranking.topProducts.length" description="暂无数据" :image-size="90" />
          <el-table v-else :data="ranking.topProducts" size="small" class="rank-table">
            <el-table-column label="排名" width="60" align="center">
              <template #default="{ $index }">
                <span class="rank-badge" :class="`rank-badge--${$index + 1}`">{{ $index + 1 }}</span>
              </template>
            </el-table-column>
            <el-table-column label="商品" min-width="170">
              <template #default="{ row }">
                <div class="seller-cell">
                  <el-avatar :size="26" :src="row.cover" shape="square" class="seller-cell__avatar">
                    <el-icon><Picture /></el-icon>
                  </el-avatar>
                  <span class="seller-cell__name text-ellipsis" :title="row.title">{{ row.title }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="viewCount" label="浏览" width="76" align="center" />
            <el-table-column prop="favoriteCount" label="收藏" width="66" align="center" />
            <el-table-column label="价格" width="92" align="right">
              <template #default="{ row }">¥{{ formatAmount(row.price) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 第五行：最近动态 -->
    <el-card shadow="never" class="chart-card recent-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">最近动态</span>
          <span class="card-subtitle">最近 20 条操作日志</span>
        </div>
      </template>
      <el-skeleton v-if="loading" animated :rows="4" />
      <el-empty v-else-if="!recent.length" description="暂无数据" :image-size="90" />
      <el-timeline v-else class="recent-timeline">
        <el-timeline-item
          v-for="item in recent"
          :key="item.id"
          :timestamp="item.createTime"
          placement="top"
          size="normal"
          :type="timelineType(item.module)"
        >
          <div class="recent-item">
            <el-avatar :size="28" :src="item.operatorAvatar" class="recent-item__avatar">
              {{ (item.operatorName || '系').slice(0, 1) }}
            </el-avatar>
            <div class="recent-item__body">
              <div class="recent-item__title">
                <span class="recent-item__name">{{ item.operatorName || '系统' }}</span>
                <el-tag size="small" effect="plain" class="recent-item__tag">{{ item.moduleLabel }}</el-tag>
                <span class="recent-item__action">{{ item.actionLabel }}</span>
              </div>
              <div class="recent-item__detail">{{ item.detail || '—' }}</div>
            </div>
          </div>
        </el-timeline-item>
      </el-timeline>
    </el-card>
  </div>
</template>

<script setup>
/**
 * 数据仪表盘（模块 10）。
 *
 * 页面结构严格对应需求里的五行布局，代码里也按这五段分块，
 * 方便和论文中的「多源统计聚合与缓存策略」章节对照：
 *   第一行 6 个指标卡 → 第二行 趋势 + 订单分布
 *   第三行 分类 + 角色/信誉分 → 第四行 两个排行榜 → 第五行 最近动态
 *
 * ECharts 的使用有三个坑，这里都做了处理：
 *   1. 实例必须在 DOM 挂载后创建，否则 init() 拿到 0 宽高，图表画不出来。
 *      容器写在 v-else 分支里，因此必须先把 loading 置 false、再 await nextTick()，
 *      此时容器才真正进入 DOM；顺序颠倒会得到一个「数据有了但图是空白」的页面。
 *   2. 容器尺寸变化（侧边栏折叠、窗口缩放）后图表不会自动重绘，
 *      必须手动 resize，否则会出现「图表只有半屏」的现象。
 *   3. 组件卸载必须 dispose，否则路由来回切换会持续累积实例直到页面卡死。
 */
import { ref, computed, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Picture } from '@element-plus/icons-vue'
// ECharts 按需引入：全量 import 会把整个 echarts 打进 chunk（压缩后仍有 1MB+），
// 而本页只用到柱状图、折线图、饼图三种，指定引入后体积能降到三分之一以下
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  GraphicComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import {
  getDashboardSummary,
  getDashboardTrend,
  getDashboardDistribution,
  getDashboardRanking,
  getDashboardRecent,
  refreshDashboard
} from '@/api/dashboard'

echarts.use([
  BarChart,
  LineChart,
  PieChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  GraphicComponent,
  CanvasRenderer
])

// ==================== 主题色 ====================
const COLORS = {
  primary: '#409EFF',
  success: '#67C23A',
  warning: '#E6A23C',
  danger: '#F56C6C',
  purple: '#7C4DFF',
  cyan: '#13C2C2',
  orange: '#E6A23C'
}
// 图表统一配色序列，顺序与需求给出的主色体系一致
const PALETTE = ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C', '#7C4DFF', '#13C2C2', '#909399']

const loading = ref(true)
const refreshing = ref(false)
const trendDays = ref(7)

const summary = ref({})
const trendPoints = ref([])
const distribution = ref({})
const ranking = ref({ topSellers: [], topProducts: [], topCategories: [] })
const recent = ref([])

// 图表 DOM 引用
const trendRef = ref(null)
const orderRef = ref(null)
const categoryRef = ref(null)
const roleRef = ref(null)
const creditRef = ref(null)

// ECharts 实例表
const charts = {}

// ==================== 指标卡 ====================

/**
 * 六个指标卡。
 * growth 为 null 表示没有可比较的基数（前期为 0），此时不显示箭头。
 * reverse=true 表示「上升是坏事」——待处理举报越多说明平台越乱，
 * 绿色向上箭头会误导，必须反过来显示。
 */
const metricCards = computed(() => {
  const s = summary.value || {}
  return [
    {
      key: 'users',
      label: '总用户数',
      value: s.totalUsers ?? 0,
      growth: s.userGrowth ?? null,
      hint: `今日新增 ${s.newUsersToday ?? 0} · 近7日 ${s.newUsersWeek ?? 0}`,
      icon: 'User',
      color: COLORS.primary,
      bg: '#ecf5ff'
    },
    {
      key: 'products',
      label: '在售商品',
      value: s.onSaleProducts ?? 0,
      growth: s.productGrowth ?? null,
      badge: s.pendingAuditProducts || 0,
      hint: `待审核 ${s.pendingAuditProducts ?? 0} 件`,
      icon: 'Goods',
      color: COLORS.success,
      bg: '#f0f9eb'
    },
    {
      key: 'orders',
      label: '总订单',
      value: s.totalOrders ?? 0,
      growth: s.orderGrowth ?? null,
      hint: `已完成 ${s.completedOrders ?? 0} 笔`,
      icon: 'Tickets',
      color: COLORS.purple,
      bg: '#f5f0ff'
    },
    {
      key: 'gmv',
      label: '累计成交额',
      value: `¥${formatAmount(s.gmvTotal)}`,
      growth: s.gmvGrowth ?? null,
      hint: `今日成交 ¥${formatAmount(s.gmvToday)}`,
      icon: 'Money',
      color: COLORS.orange,
      bg: '#fdf6ec'
    },
    {
      key: 'reports',
      label: '待处理举报',
      value: s.pendingReports ?? 0,
      growth: s.reportGrowth ?? null,
      reverse: true,
      hint: `待处理退款 ${s.pendingRefunds ?? 0} 笔`,
      icon: 'Warning',
      color: COLORS.danger,
      bg: '#fef0f0',
      blink: (s.pendingReports ?? 0) > 0
    },
    {
      key: 'credit',
      label: '平均信誉分',
      value: s.avgCreditScore ?? 0,
      growth: null,
      hint: `${s.avgCreditLevel || '—'} · 活跃会话 ${s.activeSessions ?? 0}`,
      icon: 'Medal',
      color: COLORS.cyan,
      bg: '#e6fffb'
    }
  ]
})

/**
 * 环比箭头的配色。
 * 默认上升绿、下降红；但「待处理举报」这类反向指标要反过来，
 * 否则举报涨了反而显示绿色，运营会以为这是好事。
 */
const growthClass = card => {
  const up = card.growth >= 0
  const good = card.reverse ? !up : up
  return good ? 'metric-card__growth--up' : 'metric-card__growth--down'
}

// ==================== 分布数据 ====================

const roleDist = computed(() => distribution.value.userRoleDist || [])
const categoryDist = computed(() => distribution.value.productCategoryDist || [])
const orderDist = computed(() => distribution.value.orderStatusDist || [])
const creditDist = computed(() => distribution.value.creditScoreDist || [])
const orderTotal = computed(() => orderDist.value.reduce((sum, item) => sum + (item.value || 0), 0))

// ==================== 数据加载 ====================

const loadAll = async () => {
  loading.value = true
  try {
    // 五个接口互不依赖，并发拉取能把首屏等待时间压到最慢的那一个
    const [sum, trend, dist, rank, rec] = await Promise.all([
      getDashboardSummary(),
      getDashboardTrend(trendDays.value),
      getDashboardDistribution(),
      getDashboardRanking(),
      getDashboardRecent()
    ])
    summary.value = sum || {}
    trendPoints.value = (trend && trend.points) || []
    distribution.value = dist || {}
    ranking.value = {
      topSellers: (rank && rank.topSellers) || [],
      topProducts: (rank && rank.topProducts) || [],
      topCategories: (rank && rank.topCategories) || []
    }
    recent.value = rec || []
    // 关键顺序：先关掉 loading，再渲染图表。
    // 图表容器写在 v-else 分支里，loading 为 true 时它压根不在 DOM 中，
    // 此时拿到的 ref 是 null，echarts.init 会被跳过——表现就是
    // 「数据都出来了，图表却是一片空白，而且不报任何错」。
    loading.value = false
    await nextTick()
    renderAll()
  } catch (e) {
    // 错误提示已由 request 拦截器统一弹出，这里不再重复弹
  } finally {
    loading.value = false
  }
}

const loadTrend = async () => {
  try {
    const trend = await getDashboardTrend(trendDays.value)
    trendPoints.value = (trend && trend.points) || []
    await nextTick()
    renderTrend()
  } catch (e) {
    // 忽略，交由全局提示
  }
}

const handleRefresh = async () => {
  refreshing.value = true
  try {
    await refreshDashboard()
    await loadAll()
    ElMessage.success('缓存已清除，数据已更新')
  } catch (e) {
    // 忽略
  } finally {
    refreshing.value = false
  }
}

// ==================== 图表渲染 ====================

/** 初始化或复用某个图表实例。容器不存在时直接返回，避免在切换 Tab 时报错。 */
const getChart = (key, dom) => {
  if (!dom) return null
  if (!charts[key]) {
    charts[key] = echarts.init(dom)
  }
  return charts[key]
}

const renderAll = () => {
  renderTrend()
  renderOrder()
  renderCategory()
  renderRole()
  renderCredit()
}

/**
 * 趋势图：成交额柱状 + 订单数折线，双 Y 轴。
 * 两个量纲差很多（成交额是元，订单数是个数），放同一个 Y 轴会让订单数那条线
 * 被压成贴着底的一条直线，所以必须双轴。
 */
const renderTrend = () => {
  const chart = getChart('trend', trendRef.value)
  if (!chart) return
  const dates = trendPoints.value.map(p => p.date)
  const gmv = trendPoints.value.map(p => Number(p.gmv || 0))
  const orders = trendPoints.value.map(p => Number(p.orders || 0))
  chart.setOption({
    color: PALETTE,
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: params => {
        if (!params || !params.length) return ''
        let html = `${params[0].axisValue}<br/>`
        params.forEach(p => {
          const val = p.seriesName === '成交额' ? `¥${formatAmount(p.value)}` : `${p.value} 笔`
          html += `${p.marker}${p.seriesName}：${val}<br/>`
        })
        return html
      }
    },
    legend: { data: ['成交额', '订单数'], right: 0, top: 0, itemWidth: 12, itemHeight: 8 },
    grid: { left: 8, right: 8, bottom: 4, top: 36, containLabel: true },
    xAxis: {
      type: 'category',
      data: dates,
      axisLabel: {
        color: '#909399',
        // 30 日窗口下标签太密会糊成一片，间隔显示
        interval: trendDays.value === 30 ? 4 : 0
      },
      axisLine: { lineStyle: { color: '#e4e7ed' } }
    },
    yAxis: [
      {
        type: 'value',
        name: '成交额(元)',
        nameTextStyle: { color: '#909399', fontSize: 11 },
        axisLabel: {
          color: '#909399',
          formatter: value => (value >= 10000 ? `${value / 10000}万` : value)
        },
        splitLine: { lineStyle: { color: '#f0f2f5' } }
      },
      {
        type: 'value',
        name: '订单数',
        nameTextStyle: { color: '#909399', fontSize: 11 },
        axisLabel: { color: '#909399' },
        splitLine: { show: false }
      }
    ],
    series: [
      {
        name: '成交额',
        type: 'bar',
        yAxisIndex: 0,
        data: gmv,
        barMaxWidth: 26,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#409EFF' },
            { offset: 1, color: '#79bbff' }
          ]),
          borderRadius: [4, 4, 0, 0]
        }
      },
      {
        name: '订单数',
        type: 'line',
        yAxisIndex: 1,
        data: orders,
        smooth: true,
        symbolSize: 6,
        lineStyle: { width: 2, color: COLORS.warning },
        itemStyle: { color: COLORS.warning }
      }
    ]
  })
}

/** 订单状态环形图，中心显示总数。 */
const renderOrder = () => {
  const chart = getChart('order', orderRef.value)
  if (!chart) return
  const data = orderDist.value.map(item => ({
    name: item.label || item.name,
    value: item.value || 0
  }))
  chart.setOption({
    color: PALETTE,
    tooltip: { trigger: 'item', formatter: '{b}：{c} 笔（{d}%）' },
    legend: {
      orient: 'vertical',
      right: 4,
      top: 'center',
      itemWidth: 10,
      itemHeight: 8,
      textStyle: { fontSize: 12, color: '#606266' }
    },
    series: [
      {
        type: 'pie',
        radius: ['45%', '68%'],
        center: ['38%', '50%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        emphasis: {
          label: { show: true, fontSize: 14, fontWeight: 600, formatter: '{b}\n{c} 笔' }
        },
        data
      }
    ],
    graphic: [
      {
        type: 'text',
        left: '33%',
        top: '44%',
        style: {
          text: `${orderTotal.value}\n总订单`,
          textAlign: 'center',
          fill: '#303133',
          fontSize: 15,
          fontWeight: 600,
          lineHeight: 20
        }
      }
    ]
  })
}

/** 商品分类分布：横向柱状图。 */
const renderCategory = () => {
  const chart = getChart('category', categoryRef.value)
  if (!chart) return
  // 横向柱状图要把数组反过来，否则排在最前面的分类会显示在最下面
  const sorted = [...categoryDist.value].sort((a, b) => (a.value || 0) - (b.value || 0))
  chart.setOption({
    color: [COLORS.primary],
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: '{b}：{c} 件' },
    grid: { left: 8, right: 24, bottom: 4, top: 12, containLabel: true },
    xAxis: {
      type: 'value',
      axisLabel: { color: '#909399' },
      splitLine: { lineStyle: { color: '#f0f2f5' } }
    },
    yAxis: {
      type: 'category',
      data: sorted.map(item => item.label || item.name),
      axisLabel: { color: '#606266', fontSize: 12 },
      axisLine: { lineStyle: { color: '#e4e7ed' } }
    },
    series: [
      {
        type: 'bar',
        data: sorted.map(item => item.value || 0),
        barMaxWidth: 20,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: '#a0cfff' },
            { offset: 1, color: '#409EFF' }
          ]),
          borderRadius: [0, 4, 4, 0]
        },
        label: { show: true, position: 'right', color: '#909399', fontSize: 11 }
      }
    ]
  })
}

/** 用户角色分布：饼图。 */
const renderRole = () => {
  const chart = getChart('role', roleRef.value)
  if (!chart) return
  const data = roleDist.value
    .filter(item => (item.value || 0) > 0)
    .map(item => ({ name: item.label || item.name, value: item.value || 0 }))
  chart.setOption({
    color: PALETTE,
    tooltip: { trigger: 'item', formatter: '{b}：{c} 人（{d}%）' },
    legend: { bottom: 0, itemWidth: 10, itemHeight: 8, textStyle: { fontSize: 11, color: '#606266' } },
    series: [
      {
        type: 'pie',
        radius: ['40%', '66%'],
        center: ['50%', '44%'],
        itemStyle: { borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        emphasis: { label: { show: true, fontSize: 13, fontWeight: 600, formatter: '{b}\n{c} 人' } },
        data
      }
    ]
  })
}

/** 信誉分区间分布：柱状图。 */
const renderCredit = () => {
  const chart = getChart('credit', creditRef.value)
  if (!chart) return
  chart.setOption({
    color: [COLORS.cyan],
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: '{b} 分：{c} 人' },
    grid: { left: 8, right: 12, bottom: 4, top: 24, containLabel: true },
    xAxis: {
      type: 'category',
      data: creditDist.value.map(item => item.label || item.name),
      axisLabel: { color: '#909399', fontSize: 10, interval: 0 },
      axisLine: { lineStyle: { color: '#e4e7ed' } }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#909399', fontSize: 10 },
      splitLine: { lineStyle: { color: '#f0f2f5' } }
    },
    series: [
      {
        type: 'bar',
        data: creditDist.value.map(item => item.value || 0),
        barMaxWidth: 22,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#5cdbd3' },
            { offset: 1, color: '#13C2C2' }
          ]),
          borderRadius: [4, 4, 0, 0]
        }
      }
    ]
  })
}

// ==================== 工具 ====================

/** 金额展示：大额自动转成「万」单位，避免卡片上出现一长串数字。 */
const formatAmount = value => {
  const num = Number(value || 0)
  if (!Number.isFinite(num)) return '0'
  if (num >= 10000) return `${(num / 10000).toFixed(1)}万`
  return num.toFixed(2).replace(/\.00$/, '')
}

/** 时间线圆点颜色按模块区分，扫一眼就知道这条动态属于哪类操作。 */
const timelineType = module => {
  return {
    report: 'danger',
    user: 'warning',
    product: 'primary',
    review: 'success',
    order: 'primary',
    student_auth: 'info'
  }[module] || 'info'
}

// ==================== 生命周期 ====================

/** 窗口尺寸变化时重绘所有图表，否则会出现图表只占一半的问题。 */
const handleResize = () => {
  Object.values(charts).forEach(chart => {
    if (chart) chart.resize()
  })
}

onMounted(() => {
  loadAll()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  // 必须显式销毁实例：不销毁的话，路由来回切换会不断累积 ECharts 实例，
  // 内存持续上涨，最后整个页面卡死
  Object.values(charts).forEach(chart => {
    if (chart) chart.dispose()
  })
  Object.keys(charts).forEach(key => delete charts[key])
})

// 分布与排行榜的重绘统一由 loadAll() 里的 renderAll() 负责。
// 这里不再额外 watch(distribution)：distribution 只在 loadAll 中赋值一次，
// 再 watch 一遍等于每次加载把 4 张图渲染两遍（幂等但白费 CPU）。
</script>

<style scoped>
.dashboard {
  padding: 16px;
}

.dash-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}

.dash-header__title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.dash-header__desc {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}

.dash-header__right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.dash-header__time {
  font-size: 12px;
  color: #909399;
}

/* ============ 指标卡 ============ */
.metric-row {
  margin-bottom: 16px;
}

.metric-card {
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 21, 41, 0.06);
  margin-bottom: 16px;
}

.metric-card__top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.metric-card__icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}

.metric-card__label {
  font-size: 13px;
  color: #909399;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-card__badge {
  flex: none;
}

.metric-card__value {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.3;
  margin-bottom: 8px;
  font-variant-numeric: tabular-nums;
}

/**
 * 待处理举报 > 0 时数字闪烁，提醒运营「有工单压着」。
 * 用 opacity 而不是改 color：18px 粗体数字在红底上闪烁，
 * 改色会显得脏，改透明度是更常见的告警表达。
 * 只在有积压时才闪（由 card.blink 控制），归零即停。
 */
.metric-card__value--blink {
  animation: metric-blink 1.4s ease-in-out infinite;
}

@keyframes metric-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.42; }
}

/* 尊重系统「减少动态效果」偏好：宁可只显示静态红色，也不强行闪 */
@media (prefers-reduced-motion: reduce) {
  .metric-card__value--blink {
    animation: none;
  }
}

.metric-card__foot {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 18px;
}

.metric-card__growth {
  display: inline-flex;
  align-items: center;
  gap: 1px;
  font-size: 12px;
  font-weight: 600;
  flex: none;
}

.metric-card__growth--up {
  color: #67c23a;
}

.metric-card__growth--down {
  color: #f56c6c;
}

.metric-card__hint {
  font-size: 12px;
  color: #c0c4cc;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ============ 图表卡 ============ */
.chart-row {
  margin-bottom: 0;
}

.chart-card {
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 21, 41, 0.06);
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.card-subtitle {
  font-size: 12px;
  color: #909399;
}

.chart {
  width: 100%;
}

.chart--trend {
  height: 320px;
}

.chart--pie {
  height: 320px;
}

.chart--bar {
  /* 与右侧「角色 + 信誉分」两张上下排列的卡片总高近似对齐：
     左侧 = 440 + 卡片头与内边距(~98) ≈ 538
     右侧 = 2 × (170 + 98) + 16(卡间距) ≈ 552
     高度不追求像素级一致（受字体渲染影响），差十几像素视觉上看不出来 */
  height: 440px;
}

.chart--half {
  /* 170px 是饼图的下限：再矮，图例占掉一行后饼的直径不足 90px，
     四个角色的扇区就分不清了 */
  height: 170px;
}

/* ============ 排行榜 ============ */
.rank-table {
  width: 100%;
}

.rank-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  background: #f4f4f5;
  color: #909399;
}

.rank-badge--1 {
  background: #fef0f0;
  color: #f56c6c;
}

.rank-badge--2 {
  background: #fdf6ec;
  color: #e6a23c;
}

.rank-badge--3 {
  background: #ecf5ff;
  color: #409eff;
}

.seller-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.seller-cell__avatar {
  background: linear-gradient(135deg, #409eff, #8b5cf6);
  color: #fff;
  font-size: 12px;
  flex: none;
}

.seller-cell__name {
  min-width: 0;
  font-size: 13px;
  color: #303133;
}

/* ============ 最近动态 ============ */
.recent-card {
  margin-bottom: 16px;
}

.recent-timeline {
  padding-left: 4px;
  max-height: 420px;
  overflow-y: auto;
}

.recent-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.recent-item__avatar {
  background: linear-gradient(135deg, #409eff, #8b5cf6);
  color: #fff;
  font-size: 13px;
  flex: none;
}

.recent-item__body {
  min-width: 0;
  flex: 1;
}

.recent-item__title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 3px;
}

.recent-item__name {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.recent-item__tag {
  transform: scale(0.9);
  transform-origin: left center;
}

.recent-item__action {
  font-size: 12px;
  color: #606266;
}

.recent-item__detail {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
  word-break: break-all;
}
</style>
