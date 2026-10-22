// 文件路径: miniprogram/config.js

/**
 * 环境配置。
 * 开发：微信开发者工具 → 详情 → 本地设置 → 勾选"不校验合法域名"，
 *      即可直接访问 http://localhost:8080。
 * 上线：改为后端 https 域名，并在小程序后台配置 request 合法域名。
 */
const ENV = 'dev'

const CONFIG = {
  dev: {
    BASE_URL: 'http://localhost:8080'
  },
  prod: {
    BASE_URL: 'https://your-domain.com'
  }
}

const TOKEN_KEY = 'cm_token'
const USER_KEY = 'cm_user'

module.exports = {
  BASE_URL: CONFIG[ENV].BASE_URL,
  TOKEN_KEY,
  USER_KEY,
  PRIMARY: '#FF6B35'
}
