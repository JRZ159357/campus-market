// 文件路径: miniprogram/pages/order/confirm.js
const { getProduct } = require('../../api/product')
const { listAddresses } = require('../../api/address')
const { createOrder } = require('../../api/order')
const { imgUrl, money } = require('../../utils/format')

Page({
  data: {
    loading: true,
    error: '',
    product: null,
    addresses: [],
    address: null,
    showAddress: false,
    submitting: false
  },

  onLoad(options) {
    this.productId = Number(options.productId)
    if (!this.productId) {
      this.setData({ loading: false, error: '缺少商品信息' })
      return
    }
    this.load()
  },

  onShow() {
    // 从「新增地址」页返回时刷新地址，并把新地址选中
    if (this.needReloadAddress) {
      this.needReloadAddress = false
      this.loadAddresses(true)
    }
  },

  async load() {
    this.setData({ loading: true, error: '' })
    try {
      const product = await getProduct(this.productId)
      if (product.isOwner) {
        this.setData({ loading: false, error: '不能购买自己发布的商品' })
        return
      }
      if (product.status !== 1) {
        this.setData({ loading: false, error: '该商品已售出或已下架' })
        return
      }
      this.setData({
        product: {
          ...product,
          coverUrl: imgUrl((product.images && product.images[0]) || ''),
          priceText: money(product.price)
        }
      })
      await this.loadAddresses(true)
    } catch (e) {
      this.setData({ error: e.message || '商品加载失败' })
    } finally {
      this.setData({ loading: false })
    }
  },

  /**
   * 加载地址列表。preferDefault=true 时优先选中默认地址；
   * 已选中的地址如果还在列表里，保持不变，避免刷新后跳回默认。
   */
  async loadAddresses(preferDefault) {
    try {
      const list = await listAddresses()
      let selected = this.data.address
      if (preferDefault || !selected) {
        selected = list.find(item => item.isDefault) || list[0] || null
      } else {
        selected = list.find(item => item.id === selected.id) || list[0] || null
      }
      this.setData({ addresses: list, address: selected })
    } catch (e) {
      // 提示由请求封装统一处理
    }
  },

  openAddress() {
    this.setData({ showAddress: true })
  },

  closeAddress() {
    this.setData({ showAddress: false })
  },

  chooseAddress(e) {
    const id = Number(e.currentTarget.dataset.id)
    const address = this.data.addresses.find(item => item.id === id)
    this.setData({ address: address || null, showAddress: false })
  },

  goAddAddress() {
    this.needReloadAddress = true
    this.setData({ showAddress: false })
    wx.navigateTo({ url: '/pages/address/edit' })
  },

  async submit() {
    if (this.data.submitting) return
    const address = this.data.address
    if (!address) {
      wx.showToast({ title: '请先选择交易地址', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    try {
      const result = await createOrder({ productId: this.productId, addressId: address.id })
      wx.showToast({ title: '下单成功', icon: 'success' })
      // redirectTo：下单后不允许返回到确认页重复提交
      setTimeout(() => {
        wx.redirectTo({ url: '/pages/order/detail?orderNo=' + result.orderNo })
      }, 600)
    } catch (e) {
      // 错误提示由请求封装统一处理
    } finally {
      this.setData({ submitting: false })
    }
  },

  goHome() {
    wx.switchTab({ url: '/pages/index/index' })
  },

  goProduct() {
    wx.navigateTo({ url: '/pages/product/detail?id=' + this.productId })
  }
})
