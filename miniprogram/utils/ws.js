// 文件路径: miniprogram/utils/ws.js
const { BASE_URL, TOKEN_KEY } = require('../config')

/**
 * 聊天 WebSocket 客户端。
 * <p>
 * 为什么不用 wx.connectSocket 裸写：长连接至少有四件事要处理——
 * 心跳保活、断线重连、消息分发、连接状态展示。散在各页面里必然写四遍且互相打架，
 * 因此收敛成一个模块，全局只维持一条连接（小程序同一时间也只适合一条）。
 *
 * 关键设计：
 * 1. 心跳：连上后每 30 秒发一次 ping，服务端回 pong；服务端 90 秒收不到任何帧会主动断开，
 *    所以只要小程序在前台，连接就一直有效。
 * 2. 重连：非主动关闭时按 2s/4s/8s...最多 30s 退避重连，连续失败超过 5 次判定为不可用，
 *    自动切换到「轮询未读数」的降级模式（每 10 秒拉一次 /api/chat/unread）。
 * 3. 分发：监听者按 type 注册回调，页面 onLoad 时 on('message', fn)，onUnload 时 off。
 * 4. 状态：连接状态通过 onChange 广播，页面顶部可以显示小绿点 / 红点。
 */

const WS_PATH = '/ws/chat'
const PING_INTERVAL = 30000
const MAX_RETRY = 5
const POLL_INTERVAL = 10000

const listeners = {
  message: [],
  read: [],
  typing: [],
  notice: [],
  unread: [],
  status: []
}

let socketTask = null
let connected = false
let manualClosed = false
let retryCount = 0
let pingTimer = null
let reconnectTimer = null
let pollTimer = null

/**
 * WS 地址：http(s) → ws(s)，token 放查询参数（握手时服务端校验）。
 */
function buildUrl() {
  const token = wx.getStorageSync(TOKEN_KEY) || ''
  const base = BASE_URL.replace(/^http/, 'ws')
  return `${base}${WS_PATH}?token=${encodeURIComponent(token)}`
}

function emit(type, payload) {
  const fns = listeners[type] || []
  fns.slice().forEach(fn => {
    try {
      fn(payload)
    } catch (e) {
      console.error('[ws] listener error', type, e)
    }
  })
}

function setStatus(value) {
  connected = value === 'connected'
  emit('status', value)
}

function clearTimers() {
  if (pingTimer) {
    clearInterval(pingTimer)
    pingTimer = null
  }
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

/**
 * 降级：WebSocket 不可用时，用轮询未读数代替实时推送。
 * 只轮询「有没有新消息」，真正的内容仍然靠页面下拉刷新 / 进入会话时拉历史。
 */
function startPolling() {
  if (pollTimer) return
  pollTimer = setInterval(() => {
    const { request } = require('./request')
    request({ url: '/api/chat/unread' })
      .then(data => emit('unread', data))
      .catch(() => {})
  }, POLL_INTERVAL)
}

function connect() {
  const token = wx.getStorageSync(TOKEN_KEY)
  if (!token) {
    return
  }
  if (socketTask) {
    return
  }
  manualClosed = false
  setStatus('connecting')

  socketTask = wx.connectSocket({ url: buildUrl(), timeout: 8000 })

  socketTask.onOpen(() => {
    retryCount = 0
    stopPolling()
    setStatus('connected')
    // 心跳：30 秒一次，服务端 90 秒收不到任何帧会断开，正好允许漏两次
    pingTimer = setInterval(() => {
      send({ type: 'ping' })
    }, PING_INTERVAL)
  })

  socketTask.onMessage(res => {
    let frame
    try {
      frame = JSON.parse(res.data)
    } catch (e) {
      return
    }
    if (!frame || !frame.type) return
    if (frame.type !== 'pong') {
      emit(frame.type, frame.data)
    }
  })

  socketTask.onClose(() => {
    socketTask = null
    clearTimers()
    setStatus('closed')
    if (!manualClosed) {
      scheduleReconnect()
    }
  })

  socketTask.onError(() => {
    socketTask = null
    clearTimers()
    setStatus('closed')
    if (!manualClosed) {
      scheduleReconnect()
    }
  })
}

/**
 * 指数退避重连：2s、4s、8s、16s、30s 封顶；超过 MAX_RETRY 次后放弃并降级为轮询。
 */
function scheduleReconnect() {
  if (retryCount >= MAX_RETRY) {
    setStatus('failed')
    startPolling()
    return
  }
  const delay = Math.min(2000 * Math.pow(2, retryCount), 30000)
  retryCount += 1
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null
    connect()
  }, delay)
}

function send(frame) {
  if (!socketTask || !connected) {
    return false
  }
  try {
    socketTask.send({ data: JSON.stringify(frame) })
    return true
  } catch (e) {
    return false
  }
}

function close() {
  manualClosed = true
  clearTimers()
  stopPolling()
  if (socketTask) {
    socketTask.close({})
    socketTask = null
  }
  connected = false
  emit('status', 'closed')
}

function on(type, fn) {
  if (!listeners[type]) listeners[type] = []
  if (listeners[type].indexOf(fn) === -1) {
    listeners[type].push(fn)
  }
}

function off(type, fn) {
  const arr = listeners[type]
  if (!arr) return
  if (!fn) {
    arr.length = 0
    return
  }
  const index = arr.indexOf(fn)
  if (index > -1) arr.splice(index, 1)
}

function isConnected() {
  return connected
}

module.exports = { connect, close, send, on, off, isConnected }
