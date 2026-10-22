// 文件路径: miniprogram/utils/waterfall.js

/**
 * 把一维列表拆分到左右两列。
 * <p>
 * 小程序的图片高度只有渲染后才确定，JS 拿不到实时高度，
 * 这里用「当前条数少的列优先」做均衡：卡片高度差异主要来自图片比例，
 * 按条数均衡比奇偶交替更不容易出现某一列明显更短的情况。
 * 若后续需要精确瀑布流，可在 product_image 表存图片宽高后按高度贪心分配。
 */
function splitColumns(list, left = [], right = []) {
  const l = left.slice()
  const r = right.slice()
  ;(list || []).forEach(item => {
    if (l.length <= r.length) {
      l.push(item)
    } else {
      r.push(item)
    }
  })
  return { left: l, right: r }
}

module.exports = { splitColumns }
