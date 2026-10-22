// 文件路径: miniprogram/components/product-card/index.js
const { imgUrl, money } = require('../../utils/format')

/**
 * 商品瀑布流卡片：首页、分类页、搜索页、列表页共用。
 * 图片用 widthFix 保持原始比例（瀑布流不等高的来源），文字最多两行。
 */
Component({
  properties: {
    item: {
      type: Object,
      value: {}
    }
  },

  data: {
    cover: '',
    avatar: '',
    avatarText: 'U',
    priceText: '0.00',
    originText: ''
  },

  observers: {
    item(item) {
      if (!item) return
      this.setData({
        cover: imgUrl(item.cover),
        avatar: imgUrl(item.sellerAvatar),
        avatarText: (item.sellerNickname || 'U').slice(0, 1),
        priceText: money(item.price),
        originText: item.originalPrice ? money(item.originalPrice) : ''
      })
    }
  },

  methods: {
    onTap() {
      const id = this.data.item && this.data.item.id
      if (!id) return
      wx.navigateTo({ url: '/pages/product/detail?id=' + id })
    }
  }
})
