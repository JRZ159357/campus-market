// 文件路径: miniprogram/pages/profile/about.js
const APP_VERSION = '1.0.0'

Page({
  data: {
    version: APP_VERSION,
    features: [
      { icon: '🎓', title: '学生认证', desc: '仅认证学生可发布商品，校园内真实可信' },
      { icon: '⭐', title: '信誉分体系', desc: '交易评价直接影响信誉分，等级越高越受信任' },
      { icon: '💬', title: '实时私聊', desc: '买卖双方可直接沟通，约定交付细节' },
      { icon: '🛡️', title: '平台审核', desc: '商品与认证双重审核，违规内容强制下架' }
    ]
  },

  onShareAppMessage() {
    return {
      title: '校园二手交易平台',
      path: '/pages/index/index'
    }
  },

  contactService() {
    wx.showModal({
      title: '联系客服',
      content: '客服工作时间 9:00 - 21:00\n邮箱：support@campus-market.local',
      showCancel: false,
      confirmText: '知道了'
    })
  }
})
