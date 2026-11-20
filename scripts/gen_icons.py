#!/usr/bin/env python3
"""Генерирует PNG-иконки лаунчера из SVG (адаптивная иконка: солнце + облако).

Используется в CI как запасной вариант для устройств < API 26
(adaptive icon XML покрывает API 26+).
"""
import os
import math
import struct
import zlib

SIZES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

BG_TOP = (42, 79, 168)     # #2A4FA8
BG_BOTTOM = (61, 111, 212) # #3D6FD4
SUN = (255, 215, 102)      # #FFD766
CLOUD = (255, 255, 255)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def render(size):
    px = [[(0, 0, 0, 0)] * size for _ in range(size)]
    s = size / 108.0  # дизайн в координатах 108x108

    sun_c = (54 * s, 38 * s)
    sun_r = 20 * s
    cloud_bumps = [
        (44 * s, 72 * s, 13 * s),
        (64 * s, 68 * s, 16 * s),
        (80 * s, 74 * s, 11 * s),
    ]
    cloud_rect = (32 * s, 72 * s, 92 * s, 84 * s)  # x0,y0,x1,y1

    for y in range(size):
        for x in range(size):
            # фон — вертикальный градиент + скругление под маску не нужна (launcher сам режет)
            col = lerp(BG_TOP, BG_BOTTOM, y / max(1, size - 1))
            # солнце
            dx, dy = x + 0.5 - sun_c[0], y + 0.5 - sun_c[1]
            if dx * dx + dy * dy <= sun_r * sun_r:
                col = SUN
            # облако
            in_cloud = False
            for cx, cy, cr in cloud_bumps:
                ddx, ddy = x + 0.5 - cx, y + 0.5 - cy
                if ddx * ddx + ddy * ddy <= cr * cr:
                    in_cloud = True
                    break
            x0, y0, x1, y1 = cloud_rect
            if not in_cloud and x0 <= x + 0.5 <= x1 and y0 <= y + 0.5 <= y1:
                in_cloud = True
            if in_cloud:
                col = CLOUD
            px[y][x] = (col[0], col[1], col[2], 255)
    return px


def write_png(path, size, pixels):
    raw = b""
    for row in pixels:
        raw += b"\x00" + b"".join(struct.pack("4B", *p) for p in row)

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", ihdr)
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )
    with open(path, "wb") as f:
        f.write(png)


def main():
    base = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")
    for name, size in SIZES.items():
        d = os.path.join(base, f"mipmap-{name}")
        os.makedirs(d, exist_ok=True)
        pixels = render(size)
        for fn in ("ic_launcher.png", "ic_launcher_round.png"):
            write_png(os.path.join(d, fn), size, pixels)
        print(f"generated mipmap-{name} ({size}x{size})")


if __name__ == "__main__":
    main()
