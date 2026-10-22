"""演示用图片生成（纯标准库，不依赖 Pillow）。

测试脚本上传的 1×1 透明 PNG 对接口校验足够，但拿去截图就是一片空白 ——
34×34 的缩略图里塞一个透明像素，`object-fit: cover` 拉满后仍然全透明。
这里生成几张肉眼可见的色块图，专供演示数据使用。
"""
import struct
import zlib


def _chunk(tag, data):
    payload = tag + data
    return struct.pack(">I", len(data)) + payload + struct.pack(">I", zlib.crc32(payload) & 0xFFFFFFFF)


def _encode(width, height, rows):
    """rows: 每行一个 bytes，长度必须是 width*3。"""
    raw = b"".join(b"\x00" + r for r in rows)
    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n"
            + _chunk(b"IHDR", header)
            + _chunk(b"IDAT", zlib.compress(raw, 9))
            + _chunk(b"IEND", b""))


def _mix(c1, c2, t):
    return tuple(int(a + (b - a) * t) for a, b in zip(c1, c2))


def make_cover(kind=0, size=240):
    """生成一张 size×size 的演示封面图，kind 决定配色与图案。"""
    palettes = [
        ((66, 133, 244), (150, 200, 255), "ring"),    # 蓝：中心圆环
        ((250, 140, 60), (255, 220, 150), "stripe"),  # 橙：斜条纹
        ((40, 190, 160), (190, 245, 225), "grid"),    # 青：网格
        ((150, 110, 220), (225, 210, 250), "block"),  # 紫：圆角方块
        ((240, 98, 146), (255, 205, 225), "ring"),    # 粉：中心圆环
        ((90, 105, 130), (200, 210, 225), "grid"),    # 灰蓝：网格
    ]
    c1, c2, pattern = palettes[kind % len(palettes)]
    cx = cy = (size - 1) / 2.0
    rows = []
    for y in range(size):
        row = bytearray()
        for x in range(size):
            # 对角渐变打底
            t = (x + y) / (2.0 * (size - 1))
            r, g, b = _mix(c1, c2, t)
            hit = False
            if pattern == "ring":
                d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
                hit = size * 0.22 < d < size * 0.30
            elif pattern == "stripe":
                hit = ((x + y) // 26) % 2 == 0 and 30 < x < size - 30 and 30 < y < size - 30
            elif pattern == "grid":
                hit = (x % 40 < 4 or y % 40 < 4) and 20 < x < size - 20 and 20 < y < size - 20
            elif pattern == "block":
                m = size * 0.26
                hit = m < x < size - m and m < y < size - m
            if hit:
                # 图案用纯白压低饱和度，缩略图里也能看出形状
                r, g, b = (245, 247, 250)
            row += bytes((r, g, b))
        rows.append(bytes(row))
    return _encode(size, size, rows)


def make_avatar(kind=0, size=160):
    """头像：单色底 + 中心亮块，替代默认的灰色 el-avatar。"""
    base = [(120, 144, 156), (255, 152, 0), (76, 175, 80), (63, 81, 181)][kind % 4]
    rows = []
    cx = cy = (size - 1) / 2.0
    for y in range(size):
        row = bytearray()
        for x in range(size):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d < size * 0.30:
                row += bytes((255, 255, 255))
            elif d < size * 0.34:
                row += bytes(_mix(base, (255, 255, 255), 0.45))
            else:
                row += bytes(base)
        rows.append(bytes(row))
    return _encode(size, size, rows)


if __name__ == "__main__":
    for i in range(4):
        data = make_cover(i)
        print("cover", i, len(data), "bytes")
