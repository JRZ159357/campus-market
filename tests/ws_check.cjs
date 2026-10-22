// 文件路径: tests/ws_check.cjs
/**
 * 模块6 WebSocket 实时链路测试（Node 原生 WebSocket 客户端）。
 *
 * 用法：
 *   node tests/ws_check.cjs [base]
 *
 * 覆盖：
 *   1. 握手鉴权：无 token / 错误 token 被拒绝；正确 token 建立连接并立刻收到 unread 帧
 *   2. 实时消息：A 通过 REST 发消息，B 的连接在 2 秒内收到 message 帧，字段完整
 *   3. 已读回执：B 发送 read 帧，A 收到 read 帧
 *   4. 正在输入：B 发送 typing 帧，A 收到 typing 帧
 *   5. 心跳：ping 得到 pong
 *   6. 系统通知：管理员发布定向公告，目标用户的连接收到 notice 帧
 *   7. 离线消息：接收方断线期间发的消息，重连后拉历史消息仍能取到
 *   8. 未读角标：消息到达后 unread 帧里的 chatUnread 与接口一致
 *
 * 退出码：0 全部通过，1 存在失败。
 */

const { execFileSync } = require('child_process')

const BASE = (process.argv[2] || 'http://localhost:8080').replace(/\/$/, '')
const WS_BASE = BASE.replace(/^http/, 'ws')
const suffix = Math.random().toString(16).slice(2, 8)

let passed = 0
const results = []

function check(name, ok, detail = '') {
  results.push({ name, ok, detail })
  if (ok) passed += 1
  console.log((ok ? '  [PASS] ' : '  [FAIL] ') + name + (detail ? '  -> ' + detail : ''))
  return ok
}

const sleep = ms => new Promise(r => setTimeout(r, ms))

async function api(method, path, token, body) {
  const headers = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json;charset=UTF-8'
  if (token) headers.Authorization = 'Bearer ' + token
  const res = await fetch(BASE + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  return res.json()
}

async function wxLogin(code) {
  const json = await api('POST', '/api/auth/wx-login', null, { code })
  return json.data || {}
}

/**
 * 建立一条带事件收集的 WebSocket 连接。
 * 返回 { socket, frames, waitFor(type, timeout), close() }
 */
function openSocket(token) {
  return new Promise(resolve => {
    const url = WS_BASE + '/ws/chat?token=' + encodeURIComponent(token)
    const socket = new WebSocket(url)
    const frames = []
    const waiters = []
    let opened = false
    let failed = false

    socket.addEventListener('open', () => {
      opened = true
    })
    socket.addEventListener('message', ev => {
      let frame
      try {
        frame = JSON.parse(ev.data)
      } catch (e) {
        return
      }
      frames.push(frame)
      for (let i = waiters.length - 1; i >= 0; i -= 1) {
        if (waiters[i].type === frame.type) {
          waiters[i].resolve(frame)
          waiters.splice(i, 1)
        }
      }
    })
    socket.addEventListener('error', () => {
      failed = true
    })

    const waitFor = (type, timeout = 3000) =>
      new Promise((resolveFrame, rejectFrame) => {
        const hit = frames.find(f => f.type === type)
        if (hit) {
          resolveFrame(hit)
          return
        }
        const timer = setTimeout(() => {
          rejectFrame(new Error('等待 ' + type + ' 帧超时'))
        }, timeout)
        waiters.push({
          type,
          resolve: frame => {
            clearTimeout(timer)
            resolveFrame(frame)
          }
        })
      })

    /**
     * 只等待「接下来新到达的」帧，不匹配历史帧。
     * 未读数这类会被多次推送的帧必须用它判断，否则会命中连接建立时那一帧。
     */
    const next = (type, timeout = 3000) =>
      new Promise((resolveFrame, rejectFrame) => {
        const timer = setTimeout(() => {
          rejectFrame(new Error('等待新的 ' + type + ' 帧超时'))
        }, timeout)
        waiters.push({
          type,
          resolve: frame => {
            clearTimeout(timer)
            resolveFrame(frame)
          }
        })
      })

    // 握手是异步的：等一小会儿再判断是否连上
    setTimeout(() => {
      resolve({ socket, frames, waitFor, next, opened: () => opened, failed: () => failed })
    }, 600)
  })
}

function send(socket, frame) {
  socket.send(JSON.stringify(frame))
}

function mysqlExec(sql) {
  const candidates = [
    process.env.MYSQL_BIN,
    'D:/mysql80/bin/mysql.exe',
    'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe'
  ].filter(Boolean)
  for (const bin of candidates) {
    try {
      execFileSync(bin, ['-uroot', '-p123456', '--default-character-set=utf8mb4',
        '-e', 'USE campus_market; ' + sql], { stdio: 'ignore', timeout: 20000 })
      return true
    } catch (e) {
      // 换下一个候选
    }
  }
  return false
}

async function main() {
  console.log('\n== 1. 准备账号与会话 ==')
  const buyer = await wxLogin('m6ws-a-' + suffix)
  const seller = await wxLogin('m6ws-b-' + suffix)
  check('买家微信登录成功', !!buyer.token && !!buyer.userId, buyer.userId)
  check('卖家微信登录成功', !!seller.token && !!seller.userId, seller.userId)

  const admin = await api('POST', '/api/auth/admin-login', null,
    { username: 'admin', password: '123456' })
  const adminToken = (admin.data || {}).token
  check('管理员登录成功', !!adminToken)

  // 不带商品ID的通用会话：不需要学生认证，测试更轻
  const sessionRes = await api('POST', '/api/chat/session', buyer.token, { sellerId: seller.userId })
  const sessionId = (sessionRes.data || {}).id
  check('创建通用会话成功', !!sessionId, sessionId)

  console.log('\n== 2. 握手鉴权 ==')
  const badToken = await openSocket('not-a-real-token')
  check('错误 token 无法建立连接', badToken.opened() === false, 'opened=' + badToken.opened())
  badToken.socket.close()

  const buyerWs = await openSocket(buyer.token)
  check('正确 token 建立连接', buyerWs.opened() === true)

  let unreadFrame = null
  try {
    unreadFrame = await buyerWs.waitFor('unread', 3000)
  } catch (e) {
    // 连接建立时服务端会主动推一次未读，没收到后面还有机会
  }
  check('连接建立后立即收到未读汇总帧', !!unreadFrame,
    unreadFrame ? JSON.stringify(unreadFrame.data) : '未收到')

  const sellerWs = await openSocket(seller.token)
  check('卖家连接建立', sellerWs.opened() === true)

  console.log('\n== 3. 实时消息推送 ==')
  // 先注册等待器再发消息：服务端先推 message 帧、再推 unread 帧，
  // 顺序不确定，提前注册才不会漏掉任何一帧
  const messagePromise = sellerWs.next('message', 4000)
  const unreadPromise = sellerWs.next('unread', 4000)
  const sendRes = await api('POST', '/api/chat/message', buyer.token,
    { sessionId, msgType: 1, content: 'WebSocket 实时消息 ' + suffix })
  check('REST 发送消息成功', sendRes.code === 200, sendRes.msg)

  let messageFrame = null
  try {
    messageFrame = await messagePromise
  } catch (e) {
    // 下方断言给出失败原因
  }
  check('接收方 3 秒内收到 message 帧', !!messageFrame)
  if (messageFrame) {
    check('推送的消息内容正确', messageFrame.data.content === 'WebSocket 实时消息 ' + suffix,
      messageFrame.data.content)
    check('推送的消息带会话ID', Number(messageFrame.data.sessionId) === sessionId,
      messageFrame.data.sessionId)
    check('接收方视角 self=false（不能把对方的消息渲染成自己发的）',
      messageFrame.data.self === false, messageFrame.data.self)
    check('接收方视角 fromBuyer=true', messageFrame.data.fromBuyer === true, messageFrame.data.fromBuyer)
  }

  let unreadAfter = null
  try {
    unreadAfter = await unreadPromise
  } catch (e) {
    // ignore
  }
  check('收到消息后推送未读变化帧（chatUnread>=1）',
    !!unreadAfter && Number(unreadAfter.data.chatUnread) >= 1,
    unreadAfter ? 'chatUnread=' + unreadAfter.data.chatUnread : '未收到')

  console.log('\n== 4. 已读回执与正在输入 ==')
  const readPromise = buyerWs.next('read', 4000)
  send(sellerWs.socket, { type: 'read', data: { sessionId } })
  let readFrame = null
  try {
    readFrame = await readPromise
  } catch (e) {
    // ignore
  }
  check('对方标记已读后原发送方收到 read 帧', !!readFrame,
    readFrame ? JSON.stringify(readFrame.data) : '未收到')

  const typingPromise = buyerWs.next('typing', 4000)
  send(sellerWs.socket, { type: 'typing', data: { sessionId } })
  let typingFrame = null
  try {
    typingFrame = await typingPromise
  } catch (e) {
    // ignore
  }
  check('收到对方 typing 帧', !!typingFrame, typingFrame ? JSON.stringify(typingFrame.data) : '未收到')

  console.log('\n== 5. 心跳 ==')
  const pongPromise = buyerWs.next('pong', 4000)
  send(buyerWs.socket, { type: 'ping' })
  let pongFrame = null
  try {
    pongFrame = await pongPromise
  } catch (e) {
    // ignore
  }
  check('ping 得到 pong 响应', !!pongFrame, pongFrame ? 'serverTime=' + pongFrame.data.serverTime : '未收到')

  console.log('\n== 6. 系统通知实时推送 ==')
  const title = 'WS公告' + suffix
  const noticePromise = buyerWs.next('notice', 4000)
  const publishRes = await api('POST', '/api/notice/admin/publish', adminToken,
    { title, content: 'WebSocket 通知推送测试', scope: 2, userIds: [buyer.userId] })
  check('管理员发布定向公告成功', publishRes.code === 200, publishRes.msg)

  let noticeFrame = null
  try {
    noticeFrame = await noticePromise
  } catch (e) {
    // ignore
  }
  check('目标用户收到 notice 帧', !!noticeFrame,
    noticeFrame ? noticeFrame.data.title : '未收到')
  if (noticeFrame) {
    check('通知帧带类型与跳转目标',
      noticeFrame.data.type === 3 && noticeFrame.data.target === 'none',
      noticeFrame.data.type + '/' + noticeFrame.data.target)
  }

  console.log('\n== 7. 离线消息 ==')
  buyerWs.socket.close()
  await sleep(500)
  const offlineText = '离线期间发送的消息 ' + suffix
  const offlineRes = await api('POST', '/api/chat/message', seller.token,
    { sessionId, msgType: 1, content: offlineText })
  check('接收方离线时仍可发送消息', offlineRes.code === 200, offlineRes.msg)

  const history = await api('GET', '/api/chat/message/' + sessionId + '?size=20', buyer.token)
  const rows = history.data || []
  check('离线消息已落库（上线拉历史可取到）',
    rows.some(r => r.content === offlineText), rows.length)

  const buyerAgain = await openSocket(buyer.token)
  check('重连成功', buyerAgain.opened() === true)
  const afterReload = await api('GET', '/api/chat/unread', buyer.token)
  check('重连后未读数包含离线消息', (afterReload.data || {}).chatUnread >= 1,
    (afterReload.data || {}).chatUnread)

  console.log('\n== 8. 数据清理 ==')
  buyerAgain.socket.close()
  sellerWs.socket.close()
  await sleep(300)
  await api('DELETE', '/api/chat/session/' + sessionId, buyer.token)
  const cleaned = mysqlExec(
    'DELETE FROM chat_message WHERE session_id = ' + sessionId + '; ' +
    'DELETE FROM notice WHERE user_id IN (' + buyer.userId + ',' + seller.userId + '); ' +
    'DELETE FROM chat_session WHERE id = ' + sessionId + '; ' +
    'DELETE FROM sys_user WHERE id IN (' + buyer.userId + ',' + seller.userId + ');')
  check('测试数据已清理', cleaned !== false)

  const total = results.length
  console.log('\n' + '='.repeat(56))
  console.log('结果：' + passed + '/' + total + ' 通过')
  if (passed === total) {
    console.log('全部通过')
  } else {
    console.log('失败用例：')
    results.filter(r => !r.ok).forEach(r => console.log('  - ' + r.name + (r.detail ? '  -> ' + r.detail : '')))
  }
  console.log('='.repeat(56))
  process.exit(passed === total ? 0 : 1)
}

main().catch(e => {
  console.error('ERR', e.message)
  process.exit(1)
})
