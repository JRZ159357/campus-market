// 文件路径: miniprogram/api/address.js
const { request } = require('../utils/request')

/** 我的地址列表（默认地址置顶） */
const listAddresses = () => request({ url: '/api/address/list' })

/** 新增地址 */
const createAddress = data => request({ url: '/api/address', method: 'POST', data })

/** 修改地址 */
const updateAddress = (id, data) => request({ url: '/api/address/' + id, method: 'PUT', data })

/** 删除地址 */
const deleteAddress = id => request({ url: '/api/address/' + id, method: 'DELETE' })

/** 设为默认地址 */
const setDefaultAddress = id => request({ url: '/api/address/' + id + '/default', method: 'PUT' })

module.exports = { listAddresses, createAddress, updateAddress, deleteAddress, setDefaultAddress }
