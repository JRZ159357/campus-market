<!-- 文件路径: admin-web/src/views/home/index.vue -->
<template>
  <div class="page-container">
    <div class="page-card">
      <div class="head">
        <div class="head__text">
          <h2 class="head__title">首页配置</h2>
          <p class="head__desc">
            小程序首页的五个区域都在这里配置：轮播图、公告、金刚区、热门推荐、新品推荐。
            保存后立即生效（首页聚合缓存会被清除）。
          </p>
        </div>
        <div class="head__actions">
          <el-button :icon="Refresh" :loading="refreshing" @click="reloadAll">刷新</el-button>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="home-tabs">
        <!-- ==================== Tab1 轮播图 ==================== -->
        <el-tab-pane label="轮播图" name="banner">
          <div class="toolbar toolbar--split">
            <div class="toolbar__hint">
              共 <b>{{ bannerTotal }}</b> 张。按住卡片封面可拖动调整顺序，数字越小越靠前；
              启用且在有效期内的轮播图才会下发到小程序。
            </div>
            <el-button v-permission="'admin'" type="primary" :icon="Plus" @click="openBannerCreate">
              新增轮播图
            </el-button>
          </div>

          <div v-loading="loading.banner" class="banner-wrap">
            <div v-if="banners.length" ref="bannerGridRef" class="banner-grid">
              <div v-for="item in banners" :key="item.id" class="banner-item">
                <div class="banner-item__cover" title="按住拖动排序">
                  <img :src="item.imageUrl" :alt="item.title" />
                  <span class="banner-item__order">#{{ item.sort }}</span>
                  <el-tag v-if="!item.effective" size="small" type="info" class="banner-item__badge">
                    当前不展示
                  </el-tag>
                </div>
                <div class="banner-item__body">
                  <div class="banner-item__title" :title="item.title">{{ item.title }}</div>
                  <div class="banner-item__meta">
                    <el-tag size="small" effect="plain">{{ item.linkTypeLabel }}</el-tag>
                    <span class="banner-item__value">{{ item.linkValue || '—' }}</span>
                  </div>
                  <div class="banner-item__period">{{ periodText(item) }}</div>
                </div>
                <div class="banner-item__foot">
                  <el-switch
                    :model-value="item.status"
                    :active-value="1"
                    :inactive-value="0"
                    :disabled="!canWrite"
                    :loading="statusLoadingId === item.id"
                    @change="value => handleBannerStatus(item, value)"
                  />
                  <div class="banner-item__ops">
                    <el-button link type="primary" :icon="Edit" @click="openBannerEdit(item)">编辑</el-button>
                    <el-button link type="danger" :icon="Delete" @click="handleBannerDelete(item)">删除</el-button>
                  </div>
                </div>
              </div>
            </div>

            <el-empty
              v-else-if="!loading.banner"
              description="还没有轮播图，点击右上角新增。没有轮播图时小程序会展示降级横幅，不会白屏"
              :image-size="90"
            />
          </div>

          <div v-if="bannerTotal > bannerQuery.pageSize" class="pagination">
            <el-pagination
              v-model:current-page="bannerQuery.pageNum"
              :page-size="bannerQuery.pageSize"
              :total="bannerTotal"
              layout="total, prev, pager, next"
              background
              @current-change="loadBanners"
            />
          </div>
        </el-tab-pane>

        <!-- ==================== Tab2 公告配置 ==================== -->
        <el-tab-pane label="公告配置" name="notice">
          <div class="split">
            <el-form label-width="92px" class="split__form">
              <el-form-item label="公告文字">
                <el-input
                  v-model="config.noticeText"
                  type="textarea"
                  :rows="4"
                  maxlength="100"
                  show-word-limit
                  placeholder="例如：校园集市本周六在中心广场，欢迎来摆摊"
                />
                <div class="field-tip">
                  小程序首页顶部以橙色滚动条展示。留空时即使开关打开也不会显示空白条。
                </div>
              </el-form-item>

              <el-form-item label="是否显示">
                <el-switch v-model="config.noticeEnabled" :active-value="1" :inactive-value="0" />
                <span class="field-tip field-tip--inline">
                  {{ config.noticeEnabled === 1 ? '首页顶部展示公告栏' : '首页不展示公告栏' }}
                </span>
              </el-form-item>

              <el-form-item label="公告链接">
                <el-input
                  v-model.trim="config.noticeLink"
                  placeholder="https://example.com/activity（可空）"
                  clearable
                />
                <div class="field-tip">小程序内无法直接打开外部网页，点击时会复制链接并提示。</div>
              </el-form-item>

              <el-form-item>
                <el-button
                  v-permission="'admin'"
                  type="primary"
                  :loading="saving.notice"
                  @click="saveConfig('notice', '公告配置已保存')"
                >
                  保存配置
                </el-button>
              </el-form-item>
            </el-form>

            <div class="split__preview">
              <div class="preview-label">首页效果预览</div>
              <div class="phone">
                <div v-if="noticeVisible" class="notice-bar">
                  <span class="notice-bar__icon">📢</span>
                  <div class="notice-bar__track">
                    <span class="notice-bar__text">{{ config.noticeText }}</span>
                  </div>
                  <span class="notice-bar__close">✕</span>
                </div>
                <div v-else class="notice-empty">公告已关闭，首页不显示公告栏</div>
                <div class="phone__body">
                  <div class="phone__block">轮播图</div>
                  <div class="phone__block phone__block--short">金刚区</div>
                </div>
              </div>
              <div class="preview-tip">橙色背景 + 白字 + 小喇叭，可右滑关闭（本次会话不再显示）</div>
            </div>
          </div>
        </el-tab-pane>

        <!-- ==================== Tab3 金刚区 ==================== -->
        <el-tab-pane label="金刚区配置" name="category">
          <div class="split">
            <div class="split__form">
              <div class="toolbar">
                <div class="toolbar__hint">
                  拖动左侧手柄调整顺序，开关控制是否出现在首页。此处的顺序就是分类的全局排序。
                </div>
              </div>

              <div ref="catListRef" class="cat-list">
                <div v-for="item in categories" :key="item.id" class="cat-row">
                  <span class="drag-handle" title="拖动排序">⋮⋮</span>
                  <span class="cat-row__icon">{{ item.icon || '📦' }}</span>
                  <span class="cat-row__name">{{ item.name }}</span>
                  <el-tag v-if="item.status === 0" size="small" type="info" effect="plain">分类已禁用</el-tag>
                  <span class="cat-row__count">{{ item.productCount }} 件在售</span>
                  <el-switch v-model="item.isHomeShow" :active-value="1" :inactive-value="0" />
                </div>
              </div>

              <div class="cat-foot">
                <span class="field-tip">
                  首页显示 {{ shownCategories.length }} 个（不含自动补齐的「更多」入口）
                </span>
                <el-button
                  v-permission="'admin'"
                  type="primary"
                  :loading="saving.category"
                  @click="saveCategories"
                >
                  保存配置
                </el-button>
              </div>
            </div>

            <div class="split__preview">
              <div class="preview-label">首页金刚区预览</div>
              <div class="phone">
                <div class="cats">
                  <div v-for="item in shownCategories" :key="item.id" class="cats__item">
                    <div class="cats__icon">{{ item.icon || '📦' }}</div>
                    <div class="cats__label">{{ item.name }}</div>
                  </div>
                  <div class="cats__item">
                    <div class="cats__icon">⋯</div>
                    <div class="cats__label">更多</div>
                  </div>
                </div>
                <div class="phone__body">
                  <div class="phone__block">热门推荐</div>
                </div>
              </div>
              <div class="preview-tip">
                小程序按 4 列排布，前 7 个分类 + 「更多」；超过 7 个时自动折叠到「更多」
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- ==================== Tab4 热门推荐 ==================== -->
        <el-tab-pane label="热门推荐" name="hot">
          <div class="split">
            <div class="split__form">
              <el-form label-width="92px">
                <el-form-item label="推荐模式">
                  <el-radio-group v-model="config.hotMode">
                    <el-radio-button value="manual">手动选品</el-radio-button>
                    <el-radio-button value="auto">按热度自动</el-radio-button>
                  </el-radio-group>
                  <div class="field-tip">
                    自动模式按「浏览量 + 收藏量×3」排序；手动模式按下面的顺序展示。
                  </div>
                </el-form-item>

                <template v-if="config.hotMode === 'manual'">
                  <el-form-item label="搜索商品">
                    <div class="picker">
                      <div class="picker__search">
                        <el-input
                          v-model.trim="productKeyword"
                          placeholder="输入商品标题关键词，回车搜索（只搜已上架商品）"
                          clearable
                          @keyup.enter="handleProductSearch"
                        >
                          <template #append>
                            <el-button :icon="Search" @click="handleProductSearch" />
                          </template>
                        </el-input>
                      </div>

                      <div v-if="productOptions.length" class="picker__result">
                        <div
                          v-for="item in productOptions"
                          :key="item.id"
                          class="picker-row"
                        >
                          <img class="picker-row__cover" :src="item.cover" alt="" />
                          <div class="picker-row__text">
                            <div class="picker-row__title">{{ item.title }}</div>
                            <div class="picker-row__sub">¥{{ money(item.price) }} · 浏览 {{ item.viewCount }}</div>
                          </div>
                          <el-button
                            size="small"
                            :type="isPicked(item.id) ? 'info' : 'primary'"
                            :disabled="isPicked(item.id) || config.hotProductIds.length >= 20"
                            @click="addPick(item)"
                          >
                            {{ isPicked(item.id) ? '已选' : '添加' }}
                          </el-button>
                        </div>
                      </div>
                      <div v-else-if="searched" class="picker__empty">没有符合条件的已上架商品</div>
                    </div>
                  </el-form-item>

                  <el-form-item label="已选商品">
                    <div class="picked">
                      <div class="picked__head">
                        已选 <b>{{ config.hotProductIds.length }}</b> / 20 件，拖动卡片可调整顺序
                        <el-alert
                          v-if="missingPickCount > 0"
                          type="warning"
                          :closable="false"
                          show-icon
                          class="picked__alert"
                          :title="`有 ${missingPickCount} 件已选商品已下架或删除，首页不会展示；重新选品后保存即可清理`"
                        />
                      </div>
                      <div v-if="pickedProducts.length" ref="pickedRef" class="picked__list">
                        <div v-for="(item, index) in pickedProducts" :key="item.id" class="picked-card">
                          <img class="picked-card__cover" :src="item.cover" alt="" />
                          <div class="picked-card__body">
                            <div class="picked-card__order">{{ index + 1 }}</div>
                            <div class="picked-card__title" :title="item.title">{{ item.title }}</div>
                            <div class="picked-card__price">¥{{ money(item.price) }}</div>
                          </div>
                          <el-button
                            class="picked-card__remove"
                            link
                            type="danger"
                            :icon="Delete"
                            @click="removePick(item.id)"
                          />
                        </div>
                      </div>
                      <el-empty v-else description="还没有选品，搜索后点「添加」" :image-size="70" />
                    </div>
                  </el-form-item>
                </template>

                <el-form-item v-else label="推荐数量">
                  <el-input-number v-model="config.hotCount" :min="1" :max="20" controls-position="right" />
                  <span class="field-tip field-tip--inline">1-20 件，默认 10 件</span>
                </el-form-item>

                <el-form-item>
                  <el-button
                    v-permission="'admin'"
                    type="primary"
                    :loading="saving.hot"
                    @click="saveHot"
                  >
                    保存配置
                  </el-button>
                </el-form-item>
              </el-form>
            </div>

            <div class="split__preview">
              <div class="preview-label">
                热门推荐预览
                <span class="preview-label__sub">（按当前编辑中的配置）</span>
              </div>
              <div class="phone">
                <div class="phone__section-title">热门推荐</div>
                <div v-if="hotPreviewList.length" class="hot-row">
                  <div v-for="item in hotPreviewList" :key="item.id" class="hot-card">
                    <img class="hot-card__cover" :src="item.cover" alt="" />
                    <div class="hot-card__title">{{ item.title }}</div>
                    <div class="hot-card__price">¥{{ money(item.price) }}</div>
                  </div>
                </div>
                <el-empty v-else description="暂无已上架商品" :image-size="60" />
              </div>
              <div class="preview-tip">
                小程序为横向滚动卡片，一屏约 2.5 张；预览取前 {{ Math.min(hotPreviewList.length, 3) }} 张
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- ==================== Tab5 新品推荐 ==================== -->
        <el-tab-pane label="新品推荐" name="new">
          <div class="split">
            <el-form label-width="92px" class="split__form">
              <el-form-item label="推荐数量">
                <el-input-number v-model="config.newCount" :min="1" :max="20" controls-position="right" />
                <span class="field-tip field-tip--inline">1-20 件，默认 10 件</span>
                <div class="field-tip">
                  按上架时间倒序取最新 N 件已上架商品，不支持手动指定——
                  「最新发布」的含义由时间决定，人工挑选会立刻变成第二个热门推荐。
                </div>
              </el-form-item>

              <el-form-item>
                <el-button v-permission="'admin'" type="primary" :loading="saving.new" @click="saveNew">
                  保存配置
                </el-button>
                <el-button :icon="Refresh" @click="loadNewPreview">刷新预览</el-button>
              </el-form-item>
            </el-form>

            <div class="split__preview">
              <div class="preview-label">最新 {{ config.newCount }} 件商品</div>
              <div class="phone">
                <div class="phone__section-title">最新发布</div>
                <div v-if="newPreview.length" class="new-grid">
                  <div v-for="item in newPreview" :key="item.id" class="new-card">
                    <img class="new-card__cover" :src="item.cover" alt="" />
                    <div class="new-card__title">{{ item.title }}</div>
                    <div class="new-card__price">¥{{ money(item.price) }}</div>
                  </div>
                </div>
                <el-empty v-else description="暂无已上架商品" :image-size="60" />
              </div>
              <div class="preview-tip">双列网格，首屏展示 4 件，继续下拉自动加载更多</div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- ==================== 轮播图新增/编辑弹窗 ==================== -->
    <el-dialog
      v-model="bannerDialogVisible"
      :title="bannerForm.id ? '编辑轮播图' : '新增轮播图'"
      width="680px"
      destroy-on-close
      @closed="resetBannerForm"
    >
      <el-form ref="bannerFormRef" :model="bannerForm" :rules="bannerRules" label-width="92px">
        <el-form-item label="轮播图片" prop="imageUrl">
          <div class="upload-row">
            <el-upload
              :show-file-list="false"
              accept="image/*"
              :http-request="handleUpload"
              :before-upload="beforeUpload"
            >
              <div class="upload-box" v-loading="uploading">
                <img v-if="bannerForm.imageUrl" :src="bannerForm.imageUrl" alt="轮播图" />
                <div v-else class="upload-box__empty">
                  <el-icon><Plus /></el-icon>
                  <span>上传图片</span>
                </div>
              </div>
            </el-upload>
            <div class="upload-tip">
              <div>建议尺寸 750×420（16:9），jpg/png/webp，5MB 以内</div>
              <div>小程序按圆角裁切展示，标题会叠在图片左下角</div>
              <el-button v-if="bannerForm.imageUrl" link type="danger" @click="bannerForm.imageUrl = ''">
                移除图片
              </el-button>
            </div>
          </div>
        </el-form-item>

        <el-form-item label="标题" prop="title">
          <el-input v-model.trim="bannerForm.title" maxlength="80" show-word-limit placeholder="如：开学季二手教材专场" />
        </el-form-item>

        <el-form-item label="跳转类型" prop="linkType">
          <el-radio-group v-model="bannerForm.linkType" @change="handleLinkTypeChange">
            <el-radio-button :value="1">商品详情</el-radio-button>
            <el-radio-button :value="2">分类页</el-radio-button>
            <el-radio-button :value="3">外部链接</el-radio-button>
            <el-radio-button :value="4">无跳转</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="bannerForm.linkType === 1" label="选择商品" prop="linkValue">
          <el-select
            v-model="bannerForm.linkValue"
            filterable
            remote
            clearable
            reserve-keyword
            :remote-method="remoteSearchProducts"
            :loading="productSearching"
            placeholder="输入商品标题搜索已上架商品"
            style="width: 360px"
          >
            <el-option
              v-for="item in linkProductOptions"
              :key="item.id"
              :label="`#${item.id} ${item.title}`"
              :value="String(item.id)"
            />
          </el-select>
        </el-form-item>

        <el-form-item v-else-if="bannerForm.linkType === 2" label="目标分类" prop="linkValue">
          <el-select v-model="bannerForm.linkValue" placeholder="请选择分类" style="width: 260px">
            <el-option v-for="item in categoryOptions" :key="item.id" :label="item.name" :value="String(item.id)" />
          </el-select>
        </el-form-item>

        <el-form-item v-else-if="bannerForm.linkType === 3" label="链接地址" prop="linkValue">
          <el-input v-model.trim="bannerForm.linkValue" placeholder="https://example.com/activity" />
        </el-form-item>

        <el-form-item v-else label="跳转">
          <span class="field-tip">纯展示海报，点击轮播图不跳转。</span>
        </el-form-item>

        <el-form-item label="排序值">
          <el-input-number v-model="bannerForm.sort" :min="0" :max="9999" controls-position="right" />
          <span class="field-tip field-tip--inline">数值越小越靠前</span>
        </el-form-item>

        <el-form-item label="有效期">
          <el-date-picker
            v-model="bannerForm.period"
            type="datetimerange"
            range-separator="至"
            start-placeholder="立即生效"
            end-placeholder="长期有效"
            value-format="YYYY-MM-DD HH:mm:ss"
            :default-time="defaultTime"
            clearable
            style="width: 380px"
          />
          <div class="field-tip block">不填表示长期展示；过期后小程序自动不再下发，无需手动禁用。</div>
        </el-form-item>

        <el-form-item label="状态">
          <el-switch v-model="bannerForm.status" :active-value="1" :inactive-value="0" />
          <span class="field-tip field-tip--inline">
            {{ bannerForm.status === 1 ? '启用' : '禁用（保存后小程序不展示）' }}
          </span>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="bannerDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="bannerSaving" @click="handleBannerSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue'
import Sortable from 'sortablejs'
import { useUserStore } from '@/stores/user'
import { listCategories } from '@/api/category'
import { uploadImage } from '@/api/upload'
import {
  createBanner,
  deleteBanner,
  getCategorySort,
  getHomeConfig,
  hotProducts,
  latestProducts,
  listPickedProducts,
  pageBanners,
  saveCategorySort,
  saveHomeConfig,
  searchProducts,
  sortBanners,
  updateBanner,
  updateBannerStatus
} from '@/api/home'

const route = useRoute()
const userStore = useUserStore()
// 客服只读：本页路由已限制为管理员，这里再兜一层，避免将来放开路由后误操作
const canWrite = computed(() => userStore.isAdmin)

const activeTab = ref('banner')
const refreshing = ref(false)
const loading = reactive({ banner: false, config: false, category: false, hot: false, new: false })
const saving = reactive({ notice: false, category: false, hot: false, new: false })

const money = value => Number(value || 0).toFixed(2)

// ==================== 首页配置（公告 / 热门 / 新品 共用一份） ====================
/*
 * 三个 Tab 的字段在数据库里是同一条配置的 7 个 key，
 * 所以前端也只用一份对象：保存任意一个 Tab 都会提交整份配置。
 * 这样做的副作用是「改了公告没保存、去热门 Tab 点了保存」会把公告一起存上——
 * 这是可接受的（用户本来就想改公告），比维护三份草稿再合并要简单得多，
 * 也避免了「后端全量覆盖、前端只提交一部分」导致没提交的字段被清空。
 */
const config = reactive({
  noticeText: '',
  noticeLink: '',
  noticeEnabled: 1,
  hotMode: 'auto',
  hotProductIds: [],
  hotCount: 10,
  newCount: 10
})

const noticeVisible = computed(
  () => config.noticeEnabled === 1 && !!(config.noticeText || '').trim()
)

const loadConfig = async () => {
  loading.config = true
  try {
    const data = (await getHomeConfig()) || {}
    Object.assign(config, {
      noticeText: data.noticeText || '',
      noticeLink: data.noticeLink || '',
      noticeEnabled: data.noticeEnabled ?? 1,
      hotMode: data.hotMode || 'auto',
      hotProductIds: data.hotProductIds || [],
      hotCount: data.hotCount || 10,
      newCount: data.newCount || 10
    })
  } finally {
    loading.config = false
  }
}

const saveConfig = async (key, message) => {
  saving[key] = true
  try {
    await saveHomeConfig({ ...config, hotProductIds: finalHotIds() })
    ElMessage.success(message)
  } finally {
    saving[key] = false
  }
}

// ==================== Tab1 轮播图 ====================
const banners = ref([])
const bannerTotal = ref(0)
const statusLoadingId = ref(null)
const bannerQuery = reactive({ pageNum: 1, pageSize: 12 })
const bannerGridRef = ref()
const categoryOptions = ref([])

const loadBanners = async () => {
  loading.banner = true
  try {
    const data = (await pageBanners({ ...bannerQuery })) || {}
    banners.value = data.records || []
    bannerTotal.value = data.total || 0
    await nextTick()
    initBannerSortable()
  } catch (e) {
    banners.value = []
    bannerTotal.value = 0
  } finally {
    loading.banner = false
  }
}

const periodText = item => {
  if (!item.startTime && !item.endTime) return '长期有效'
  const brief = value => (value ? value.slice(0, 16) : '')
  return `${brief(item.startTime) || '立即'} ~ ${brief(item.endTime) || '长期'}`
}

const handleBannerStatus = async (row, value) => {
  statusLoadingId.value = row.id
  try {
    await updateBannerStatus(row.id, value)
    row.status = value
    // 后端会重新判断「启用 + 时间窗口」，这里只做一个乐观更新，
    // 真正的 effective 以下次刷新为准（避免本地算错时间窗口）
    row.effective = value === 1
    ElMessage.success(value === 1 ? '已启用' : '已禁用')
  } finally {
    statusLoadingId.value = null
  }
}

const handleBannerDelete = async row => {
  const confirmed = await ElMessageBox.confirm(
    `确定删除轮播图「${row.title}」吗？删除后小程序首页不再展示。`,
    '删除轮播图',
    { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }
  ).catch(() => false)
  if (!confirmed) return
  await deleteBanner(row.id)
  ElMessage.success('已删除')
  if (banners.value.length === 1 && bannerQuery.pageNum > 1) {
    bannerQuery.pageNum -= 1
  }
  await loadBanners()
}

// ---------- 拖拽排序 ----------
let bannerSortable = null

const initBannerSortable = () => {
  if (!canWrite.value || !bannerGridRef.value || bannerSortable) return
  bannerSortable = Sortable.create(bannerGridRef.value, {
    animation: 160,
    // 只有封面区域能拖：卡片下部有开关和按钮，整卡可拖会让点击变成误拖
    handle: '.banner-item__cover',
    ghostClass: 'drag-ghost',
    onEnd: async ({ oldIndex, newIndex }) => {
      if (oldIndex === newIndex) return
      const rows = [...banners.value]
      const [moved] = rows.splice(oldIndex, 1)
      rows.splice(newIndex, 0, moved)
      banners.value = rows
      // 当前页内从 1 开始重排；批量接口一次提交，避免 N 个请求只成功一半
      const items = rows.map((item, index) => ({ id: item.id, sort: index + 1 }))
      try {
        await sortBanners(items)
        ElMessage.success('排序已保存')
        bannerSortable?.destroy()
        bannerSortable = null
        await loadBanners()
      } catch (e) {
        bannerSortable?.destroy()
        bannerSortable = null
        await loadBanners()
      }
    }
  })
}

// ==================== 轮播图表单 ====================
const bannerDialogVisible = ref(false)
const bannerSaving = ref(false)
const uploading = ref(false)
const bannerFormRef = ref()
const defaultTime = [new Date(2000, 0, 1, 0, 0, 0), new Date(2000, 0, 1, 23, 59, 59)]

const bannerForm = reactive({
  id: null,
  title: '',
  imageUrl: '',
  linkType: 4,
  linkValue: '',
  sort: 1,
  status: 1,
  period: null
})

const validateLinkValue = (rule, value, callback) => {
  const type = bannerForm.linkType
  if (type === 4) return callback()
  if (!value) {
    return callback(new Error(type === 1 ? '请选择商品' : type === 2 ? '请选择分类' : '请填写外部链接'))
  }
  if (type === 3 && !/^https?:\/\//.test(value)) {
    return callback(new Error('链接需以 http:// 或 https:// 开头'))
  }
  callback()
}

const bannerRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  imageUrl: [{ required: true, message: '请上传轮播图片', trigger: 'change' }],
  linkType: [{ required: true, message: '请选择跳转类型', trigger: 'change' }],
  linkValue: [{ validator: validateLinkValue, trigger: 'change' }]
}

const beforeUpload = file => {
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('图片不能超过 5MB')
    return false
  }
  return true
}

const handleUpload = async options => {
  uploading.value = true
  try {
    bannerForm.imageUrl = await uploadImage(options.file)
    bannerFormRef.value?.validateField('imageUrl')
    ElMessage.success('图片已上传')
  } catch (e) {
    // 错误提示由请求拦截器统一处理
  } finally {
    uploading.value = false
  }
}

const handleLinkTypeChange = () => {
  // 切换类型后旧的跳转值一定不适用（商品ID 变成分类ID 就是个错误的值），
  // 不清空的话用户很容易漏填而保存出一个点了没反应的轮播图
  bannerForm.linkValue = ''
  bannerFormRef.value?.clearValidate('linkValue')
}

const resetBannerForm = () => {
  Object.assign(bannerForm, {
    id: null,
    title: '',
    imageUrl: '',
    linkType: 4,
    linkValue: '',
    sort: 1,
    status: 1,
    period: null
  })
  linkProductOptions.value = []
  bannerFormRef.value?.clearValidate()
}

const openBannerCreate = () => {
  resetBannerForm()
  bannerForm.sort = banners.value.length + 1
  bannerDialogVisible.value = true
}

const openBannerEdit = row => {
  Object.assign(bannerForm, {
    id: row.id,
    title: row.title,
    imageUrl: row.imageUrl,
    linkType: row.linkType,
    linkValue: row.linkValue || '',
    sort: row.sort,
    status: row.status,
    period: row.startTime && row.endTime ? [row.startTime, row.endTime] : null
  })
  if (row.linkType === 1 && row.linkValue) {
    remoteSearchProducts('')
  }
  bannerDialogVisible.value = true
}

const handleBannerSubmit = async () => {
  await bannerFormRef.value?.validate()
  bannerSaving.value = true
  try {
    const payload = {
      title: bannerForm.title,
      imageUrl: bannerForm.imageUrl,
      linkType: bannerForm.linkType,
      linkValue: bannerForm.linkType === 4 ? null : bannerForm.linkValue,
      sort: bannerForm.sort,
      status: bannerForm.status,
      startTime: bannerForm.period?.[0] || null,
      endTime: bannerForm.period?.[1] || null
    }
    if (bannerForm.id) {
      await updateBanner(bannerForm.id, payload)
      ElMessage.success('保存成功')
    } else {
      await createBanner(payload)
      ElMessage.success('新增成功')
    }
    bannerDialogVisible.value = false
    await loadBanners()
  } finally {
    bannerSaving.value = false
  }
}

// ---------- 商品搜索（用于跳转类型=商品详情） ----------
const linkProductOptions = ref([])
const productSearching = ref(false)

const remoteSearchProducts = async keyword => {
  productSearching.value = true
  try {
    const data = await searchProducts({ keyword: keyword || '', pageNum: 1, pageSize: 20 })
    linkProductOptions.value = data?.records || []
  } catch (e) {
    linkProductOptions.value = []
  } finally {
    productSearching.value = false
  }
}

// ==================== Tab3 金刚区 ====================
const categories = ref([])
const catListRef = ref()
let catSortable = null

const loadCategories = async () => {
  loading.category = true
  try {
    categories.value = (await getCategorySort()) || []
    await nextTick()
    initCatSortable()
  } catch (e) {
    categories.value = []
  } finally {
    loading.category = false
  }
}

const shownCategories = computed(() =>
  categories.value.filter(item => item.isHomeShow === 1 && item.status === 1)
)

const initCatSortable = () => {
  if (!canWrite.value || !catListRef.value || catSortable) return
  catSortable = Sortable.create(catListRef.value, {
    handle: '.drag-handle',
    animation: 150,
    ghostClass: 'drag-ghost'
    // 不在这里自动保存：金刚区是一次性全量提交，
    // 拖几下就发一次请求会把中间状态也写进数据库
  })
}

const saveCategories = async () => {
  if (!categories.value.length) return
  saving.category = true
  try {
    const items = categories.value.map((item, index) => ({
      id: item.id,
      isHomeShow: item.isHomeShow,
      sort: index + 1
    }))
    await saveCategorySort(items)
    ElMessage.success('金刚区配置已保存')
    await loadCategories()
  } finally {
    saving.category = false
  }
}

// ==================== Tab4 热门推荐 ====================
const productKeyword = ref('')
const productOptions = ref([])
const searched = ref(false)
const pickedProducts = ref([])
const hotPreview = ref([])
const pickedRef = ref()
let pickedSortable = null

const isPicked = id => config.hotProductIds.includes(id)

const missingPickCount = computed(() => {
  const shown = new Set(pickedProducts.value.map(item => item.id))
  return config.hotProductIds.filter(id => !shown.has(id)).length
})

/** 提交时的最终顺序：先按卡片顺序，再把「已失效但还留在配置里」的 ID 追加到末尾 */
const finalHotIds = () => {
  const ids = pickedProducts.value.map(item => item.id)
  const rest = config.hotProductIds.filter(id => !ids.includes(id))
  return [...ids, ...rest]
}

const handleProductSearch = async () => {
  const data = await searchProducts({ keyword: productKeyword.value, pageNum: 1, pageSize: 20 })
  productOptions.value = data?.records || []
  searched.value = true
}

const addPick = item => {
  if (isPicked(item.id) || config.hotProductIds.length >= 20) return
  config.hotProductIds.push(item.id)
  pickedProducts.value.push(item)
  nextTick(initPickedSortable)
}

const removePick = id => {
  config.hotProductIds = config.hotProductIds.filter(item => item !== id)
  pickedProducts.value = pickedProducts.value.filter(item => item.id !== id)
}

const initPickedSortable = () => {
  if (!canWrite.value || !pickedRef.value || pickedSortable) return
  pickedSortable = Sortable.create(pickedRef.value, {
    animation: 150,
    ghostClass: 'drag-ghost',
    onEnd: ({ oldIndex, newIndex }) => {
      if (oldIndex === newIndex) return
      const rows = [...pickedProducts.value]
      const [moved] = rows.splice(oldIndex, 1)
      rows.splice(newIndex, 0, moved)
      pickedProducts.value = rows
      // 列表顺序即配置顺序，拖完立刻同步，避免保存时用的是旧顺序
      const rest = config.hotProductIds.filter(id => !rows.some(r => r.id === id))
      config.hotProductIds = [...rows.map(r => r.id), ...rest]
    }
  })
}

const loadPicked = async () => {
  if (!config.hotProductIds.length) {
    pickedProducts.value = []
    return
  }
  loading.hot = true
  try {
    pickedProducts.value = (await listPickedProducts(config.hotProductIds)) || []
    await nextTick()
    initPickedSortable()
  } catch (e) {
    pickedProducts.value = []
  } finally {
    loading.hot = false
  }
}

const loadHotPreview = async () => {
  if (config.hotMode === 'manual') {
    return
  }
  try {
    const list = (await hotProducts()) || []
    hotPreview.value = list.slice(0, config.hotCount || 10)
  } catch (e) {
    hotPreview.value = []
  }
}

/** 预览列表：手动模式用已选商品，自动模式用热度榜前 N */
const hotPreviewList = computed(() => {
  if (config.hotMode === 'manual') return pickedProducts.value.slice(0, 6)
  return hotPreview.value.slice(0, 6)
})

const saveHot = async () => {
  if (config.hotMode === 'manual' && finalHotIds().length === 0) {
    ElMessage.warning('手动选品模式下至少选择一件商品')
    return
  }
  saving.hot = true
  try {
    await saveHomeConfig({ ...config, hotProductIds: finalHotIds() })
    ElMessage.success('热门推荐配置已保存')
    await loadPicked()
  } finally {
    saving.hot = false
  }
}

// ==================== Tab5 新品推荐 ====================
const newPreview = ref([])

const loadNewPreview = async () => {
  loading.new = true
  try {
    const data = await latestProducts(config.newCount || 10)
    newPreview.value = data?.records || []
  } catch (e) {
    newPreview.value = []
  } finally {
    loading.new = false
  }
}

const saveNew = async () => {
  saving.new = true
  try {
    await saveHomeConfig({ ...config, hotProductIds: finalHotIds() })
    ElMessage.success('新品推荐配置已保存')
    await loadNewPreview()
  } finally {
    saving.new = false
  }
}

// ==================== 生命周期与切换 ====================
const reloadAll = async () => {
  refreshing.value = true
  try {
    await Promise.all([loadConfig(), loadBanners(), loadCategories()])
    await loadPicked()
    await Promise.all([loadHotPreview(), loadNewPreview()])
  } finally {
    refreshing.value = false
  }
}

// 切 Tab 时按需加载该 Tab 依赖的数据：
// 一进页面就并发拉 5 份数据没必要，而热门/新品的预览本来就要跟着配置变
watch(activeTab, async name => {
  if (name === 'hot') {
    await Promise.all([loadPicked(), loadHotPreview()])
    await nextTick()
    initPickedSortable()
  } else if (name === 'new') {
    await loadNewPreview()
  } else if (name === 'category') {
    await nextTick()
    initCatSortable()
  }
})

// 自动模式的数量变化时，预览跟着变（本地切片，不重新请求）
watch(
  () => [config.hotMode, config.hotCount],
  () => {
    if (config.hotMode === 'auto') loadHotPreview()
  }
)

watch(
  () => config.newCount,
  () => {
    if (activeTab.value === 'new') loadNewPreview()
  }
)

onMounted(async () => {
  // 支持 /home?tab=category 直达某个 Tab：运营群里经常是「去首页配置-金刚区改一下」，
  // 只给一句话链接比让人自己找 Tab 更快
  const tab = String(route.query.tab || '')
  if (['banner', 'notice', 'category', 'hot', 'new'].includes(tab)) {
    activeTab.value = tab
  }
  await reloadAll()
  try {
    categoryOptions.value = (await listCategories({ all: true })) || []
  } catch (e) {
    categoryOptions.value = []
  }
})

onBeforeUnmount(() => {
  bannerSortable?.destroy()
  catSortable?.destroy()
  pickedSortable?.destroy()
  bannerSortable = null
  catSortable = null
  pickedSortable = null
})
</script>

<style scoped>
.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 12px;
}

.head__title {
  margin: 0 0 6px;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.head__desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: #909399;
  max-width: 760px;
}

.head__actions {
  flex: none;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.toolbar--split {
  justify-content: space-between;
}

.toolbar__hint {
  font-size: 13px;
  color: #909399;
  line-height: 1.6;
}

.field-tip {
  font-size: 12px;
  color: #a8abb2;
  line-height: 1.6;
  margin-top: 4px;
}

.field-tip--inline {
  margin: 0 0 0 10px;
  line-height: 32px;
}

.field-tip.block {
  display: block;
  width: 100%;
}

/* ---------- 轮播图卡片 ---------- */
.banner-wrap {
  min-height: 120px;
}

.banner-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(268px, 1fr));
  gap: 16px;
}

.banner-item {
  border: 1px solid #ebeef5;
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
  transition: box-shadow 0.2s;
}

.banner-item:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

.banner-item__cover {
  position: relative;
  height: 148px;
  background: #f5f7fa;
  cursor: grab;
  overflow: hidden;
}

.banner-item__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.banner-item__order {
  position: absolute;
  top: 8px;
  left: 8px;
  padding: 2px 8px;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: 12px;
}

.banner-item__badge {
  position: absolute;
  top: 8px;
  right: 8px;
}

.banner-item__body {
  padding: 10px 12px 0;
}

.banner-item__title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.banner-item__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}

.banner-item__value {
  font-size: 12px;
  color: #909399;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.banner-item__period {
  margin-top: 6px;
  font-size: 12px;
  color: #a8abb2;
}

.banner-item__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px 10px;
  margin-top: 6px;
  border-top: 1px dashed #f0f2f5;
}

.banner-item__ops {
  display: flex;
  gap: 4px;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

/* ---------- 左右分栏 ---------- */
.split {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}

.split__form {
  flex: 1;
  min-width: 0;
}

.split__preview {
  width: 360px;
  flex: none;
}

.preview-label {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 10px;
}

.preview-label__sub {
  font-weight: 400;
  color: #a8abb2;
}

.preview-tip {
  margin-top: 10px;
  font-size: 12px;
  color: #a8abb2;
  line-height: 1.6;
}

/* ---------- 手机预览 ---------- */
.phone {
  width: 320px;
  min-height: 200px;
  padding: 14px;
  border-radius: 20px;
  background: #f2f3f5;
  box-shadow: inset 0 0 0 1px #e6e8eb;
}

.phone__body {
  margin-top: 12px;
}

.phone__block {
  height: 92px;
  border-radius: 10px;
  background: linear-gradient(135deg, #ffd9c7, #ffe9df);
  color: #d98462;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 10px;
}

.phone__block--short {
  height: 66px;
  background: #fff;
  border: 1px dashed #e0e3e8;
  color: #b6b9be;
}

.phone__section-title {
  font-size: 12px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 8px;
}

/* ---------- 公告条 ---------- */
.notice-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 34px;
  padding: 0 10px;
  border-radius: 8px;
  background: #ff6b35;
  color: #fff;
  overflow: hidden;
}

.notice-bar__icon {
  flex: none;
  font-size: 14px;
}

.notice-bar__track {
  flex: 1;
  overflow: hidden;
  height: 20px;
  line-height: 20px;
}

.notice-bar__text {
  display: inline-block;
  white-space: nowrap;
  font-size: 12px;
  animation: notice-scroll 12s linear infinite;
}

.notice-bar__close {
  flex: none;
  font-size: 11px;
  opacity: 0.85;
}

@keyframes notice-scroll {
  0% {
    transform: translateX(100%);
  }
  100% {
    transform: translateX(-100%);
  }
}

.notice-empty {
  height: 34px;
  line-height: 34px;
  padding: 0 10px;
  border-radius: 8px;
  background: #fff;
  border: 1px dashed #e0e3e8;
  color: #b6b9be;
  font-size: 12px;
}

/* ---------- 金刚区 ---------- */
.cat-list {
  border: 1px solid #ebeef5;
  border-radius: 10px;
  overflow: hidden;
  max-height: 460px;
  overflow-y: auto;
}

.cat-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-bottom: 1px solid #f5f7fa;
  background: #fff;
}

.cat-row:last-child {
  border-bottom: none;
}

.drag-handle {
  cursor: grab;
  color: #c0c4cc;
  letter-spacing: -2px;
  font-size: 14px;
  user-select: none;
}

.cat-row__icon {
  width: 26px;
  text-align: center;
  font-size: 16px;
}

.cat-row__name {
  flex: 1;
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.cat-row__count {
  font-size: 12px;
  color: #a8abb2;
}

.cat-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
}

.cats {
  display: flex;
  flex-wrap: wrap;
  background: #fff;
  border-radius: 12px;
  padding: 14px 0 4px;
}

.cats__item {
  width: 25%;
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 12px;
}

.cats__icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: #fff4ef;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}

.cats__label {
  margin-top: 6px;
  font-size: 11px;
  color: #666;
  max-width: 62px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

/* ---------- 商品选择器 ---------- */
.picker {
  width: 100%;
}

.picker__result {
  margin-top: 8px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  max-height: 240px;
  overflow-y: auto;
}

.picker-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-bottom: 1px solid #f5f7fa;
}

.picker-row:last-child {
  border-bottom: none;
}

.picker-row__cover {
  width: 40px;
  height: 40px;
  border-radius: 6px;
  object-fit: cover;
  background: #f5f7fa;
}

.picker-row__text {
  flex: 1;
  min-width: 0;
}

.picker-row__title {
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.picker-row__sub {
  font-size: 12px;
  color: #a8abb2;
  margin-top: 2px;
}

.picker__empty {
  margin-top: 8px;
  padding: 14px;
  text-align: center;
  font-size: 12px;
  color: #a8abb2;
  border: 1px dashed #e0e3e8;
  border-radius: 8px;
}

.picked__head {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
  width: 100%;
}

.picked__alert {
  margin-top: 8px;
}

.picked__list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  width: 100%;
}

.picked-card {
  position: relative;
  width: 148px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
  cursor: grab;
}

.picked-card__cover {
  width: 100%;
  height: 88px;
  object-fit: cover;
  display: block;
  background: #f5f7fa;
}

.picked-card__body {
  padding: 6px 8px 8px;
}

.picked-card__order {
  display: inline-block;
  min-width: 16px;
  height: 16px;
  line-height: 16px;
  text-align: center;
  border-radius: 8px;
  background: #409eff;
  color: #fff;
  font-size: 11px;
  margin-bottom: 4px;
}

.picked-card__title {
  font-size: 12px;
  color: #303133;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.picked-card__price {
  margin-top: 2px;
  font-size: 12px;
  color: #ff6b35;
  font-weight: 600;
}

.picked-card__remove {
  position: absolute;
  top: 4px;
  right: 4px;
  padding: 2px;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 50%;
}

/* ---------- 热门 / 新品预览 ---------- */
.hot-row {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding-bottom: 4px;
}

.hot-card {
  width: 104px;
  flex: none;
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.hot-card__cover {
  width: 100%;
  height: 78px;
  object-fit: cover;
  display: block;
  background: #f5f7fa;
}

.hot-card__title {
  padding: 6px 6px 0;
  font-size: 11px;
  color: #303133;
  line-height: 1.4;
  height: 32px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.hot-card__price {
  padding: 4px 6px 6px;
  font-size: 12px;
  color: #ff6b35;
  font-weight: 600;
}

.new-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.new-card {
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.new-card__cover {
  width: 100%;
  height: 84px;
  object-fit: cover;
  display: block;
  background: #f5f7fa;
}

.new-card__title {
  padding: 6px 6px 0;
  font-size: 11px;
  color: #303133;
  line-height: 1.4;
  height: 32px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.new-card__price {
  padding: 4px 6px 8px;
  font-size: 12px;
  color: #ff6b35;
  font-weight: 600;
}

/* ---------- 上传 ---------- */
.upload-row {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.upload-box {
  width: 200px;
  height: 112px;
  border: 1px dashed #dcdfe6;
  border-radius: 8px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fafafa;
  cursor: pointer;
}

.upload-box img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.upload-box__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  color: #a8abb2;
  font-size: 12px;
}

.upload-tip {
  font-size: 12px;
  color: #a8abb2;
  line-height: 1.8;
}

:deep(.drag-ghost) {
  opacity: 0.45;
  border: 1px dashed #409eff;
}
</style>
