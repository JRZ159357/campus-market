// 文件路径: miniprogram/pages/report/index.js
const { submitReport, REPORT_REASONS, typeMeta } = require('../../api/report')
const { uploadImage } = require('../../utils/request')
const { imgUrl, money } = require('../../utils/format')

/** 与后端 ReportRule 保持一致，重复一份是为了让校验在提交前就发生、不多打一次请求 */
const CONTENT_MIN = 5
const CONTENT_MAX = 500
const MAX_IMAGES = 3

/**
 * 举报提交页。
 * <p>
 * 三类举报（商品 / 用户 / 评价）共用这一个页面，靠 URL 参数区分：
 * {@code ?type=1&targetId=123}。这是刻意的——三者的填写流程完全一样
 * （看对象 → 选原因 → 写描述 → 传证据），拆三个页面会让「加了新的校验规则」
 * 这件事需要改三处，迟早漏掉一处。
 * <p>
 * 被举报对象的信息由后端在提交时重新查询并写入快照，页面这里只做展示；
 * 因此即使商品在用户填写期间被下架，举报记录里仍然留着当时的完整内容。
 */
Page({
  data: {
    reportType: 1,
    typeMeta: typeMeta(1),
    targetId: null,
    loading: true,
    notFound: false,
    target: null,

    reasons: REPORT_REASONS,
    reasonIndex: -1,
    reasonSelected: null,

    content: '',
    charCount: 0,
    images: [],
    uploading: false,
    submitting: false,
    maxImages: MAX_IMAGES,
    maxLength: CONTENT_MAX,
    errors: {}
  },

  async onLoad(options) {
    if (!getApp().isLogin()) {
      wx.redirectTo({ url: '/pages/login/index' })
      return
    }
    const reportType = Number(options.type || 1)
    const targetId = Number(options.targetId || 0)
    if (!targetId) {
      this.setData({ loading: false, notFound: true })
      return
    }
    this.setData({ reportType, targetId, typeMeta: typeMeta(reportType) })
    await this.loadTarget()
  },

  /**
   * 加载被举报对象的展示信息。
   * <p>
   * 直接复用已有的商品详情 / 卖家主页 / 评价接口取展示数据，而不是让举报接口
   * 提供一个「预览」接口：少一个接口就少一处以后要维护的重复逻辑。
   * 这里只取展示用的标题与图片，快照仍由后端在提交时统一生成。
   */
  async loadTarget() {
    const { reportType, targetId } = this.data
    try {
      if (reportType === 1) {
        const { getProduct } = require('../../api/product')
        const detail = await getProduct(targetId)
        this.setData({
          loading: false,
          target: {
            kind: 'product',
            title: detail.title || '该商品',
            cover: imgUrl(detail.cover),
            extra: '¥' + money(detail.price) + ' · ' + (detail.statusLabel || '')
          }
        })
      } else if (reportType === 2) {
        const { getUserHome } = require('../../api/user')
        const home = await getUserHome(targetId)
        this.setData({
          loading: false,
          target: {
            kind: 'user',
            title: home.nickname || '该用户',
            cover: imgUrl(home.avatar),
            extra: '信誉分 ' + (home.creditScore || 100) + ' · ' + (home.creditLevelLabel || '')
          }
        })
      } else {
        // 评价详情接口需要「当前查看者」，用 pageUserReviews 拿列表再定位不划算，
        // 这里直接展示「评价 #id」，正文由提交后的快照固化，页面不强求展示全文
        this.setData({
          loading: false,
          target: {
            kind: 'review',
            title: '评价 #' + targetId,
            cover: '',
            extra: '举报对象：一条用户评价'
          }
        })
      }
    } catch (e) {
      // 对象已被删除时不能让用户对空气提交举报，给明确提示而不是报错
      this.setData({ loading: false, notFound: true })
    }
  },

  onReasonTap(e) {
    const index = Number(e.currentTarget.dataset.index)
    this.setData({
      reasonIndex: index,
      reasonSelected: this.data.reasons[index],
      'errors.reason': ''
    })
  },

  onContentInput(e) {
    const content = e.detail.value
    this.setData({
      content,
      charCount: content.length,
      'errors.content': ''
    })
  },

  async chooseImage() {
    const remain = this.data.maxImages - this.data.images.length
    if (remain <= 0) {
      wx.showToast({ title: '最多上传 ' + MAX_IMAGES + ' 张图片', icon: 'none' })
      return
    }
    const res = await wx.chooseMedia({
      count: remain,
      mediaType: ['image'],
      sizeType: ['compressed'],
      sourceType: ['album', 'camera']
    }).catch(() => null)
    if (!res || !res.tempFiles || !res.tempFiles.length) {
      return
    }
    this.setData({ uploading: true })
    try {
      const uploaded = []
      for (const file of res.tempFiles) {
        uploaded.push(await uploadImage(file.tempFilePath))
      }
      this.setData({ images: this.data.images.concat(uploaded) })
    } catch (e) {
      wx.showToast({ title: '图片上传失败', icon: 'none' })
    } finally {
      this.setData({ uploading: false })
    }
  },

  removeImage(e) {
    const index = Number(e.currentTarget.dataset.index)
    const images = this.data.images.slice()
    images.splice(index, 1)
    this.setData({ images })
  },

  previewImage(e) {
    const index = Number(e.currentTarget.dataset.index)
    wx.previewImage({
      current: imgUrl(this.data.images[index]),
      urls: this.data.images.map(imgUrl)
    })
  },

  /**
   * 前端校验。这里校验不是为了让服务端信任前端，而是为了让用户立刻知道错在哪：
   * 等一次往返再报错，体验差很多。真正的强制仍在服务端。
   */
  validate() {
    const errors = {}
    const content = (this.data.content || '').trim()
    if (!this.data.reasonSelected) {
      errors.reason = '请选择举报原因'
    }
    if (content.length < CONTENT_MIN) {
      errors.content = '详细描述至少 ' + CONTENT_MIN + ' 字，描述越具体客服核实越快'
    } else if (content.length > CONTENT_MAX) {
      errors.content = '详细描述最多 ' + CONTENT_MAX + ' 字'
    }
    this.setData({ errors })
    return !Object.keys(errors).length
  },

  async handleSubmit() {
    if (this.data.submitting || !this.validate()) {
      return
    }
    this.setData({ submitting: true })
    try {
      await submitReport({
        reportType: this.data.reportType,
        targetId: this.data.targetId,
        reason: this.data.reasonSelected.value,
        content: this.data.content.trim(),
        images: this.data.images
      })
      // 提交成功就带 reason 跳到「我的举报」第一页，
      // 而不是回上一页：用户此刻最想知道的是「提交成功了吗、去哪看进度」
      wx.showToast({ title: '举报已提交', icon: 'success' })
      setTimeout(() => {
        wx.redirectTo({ url: '/pages/report/my' })
      }, 800)
    } catch (e) {
      // 统一错误提示由 request 封装给出，这里不重复弹窗
    } finally {
      this.setData({ submitting: false })
    }
  },

  goMyReports() {
    wx.redirectTo({ url: '/pages/report/my' })
  }
})