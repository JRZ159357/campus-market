// 文件路径: miniprogram/api/follow.js
const { request } = require('../utils/request')

/** 关注 / 取消关注（同一接口切换） */
const toggleFollow = userId => request({ url: '/api/follow/' + userId, method: 'POST' })

/** 我关注的人（分页） */
const pageFollowing = (pageNum = 1, pageSize = 10) =>
  request({ url: `/api/follow/following?pageNum=${pageNum}&pageSize=${pageSize}` })

/** 关注我的人（粉丝列表，分页） */
const pageFollowers = (pageNum = 1, pageSize = 10) =>
  request({ url: `/api/follow/followers?pageNum=${pageNum}&pageSize=${pageSize}` })

/** 是否已关注某用户 */
const checkFollow = userId => request({ url: '/api/follow/check/' + userId })

module.exports = { toggleFollow, pageFollowing, pageFollowers, checkFollow }
