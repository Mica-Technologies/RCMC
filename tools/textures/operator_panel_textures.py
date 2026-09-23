"""
Draws the ride operator panel's two 16x16 textures from scratch: a brushed steel side and a control
desk with a green start button, an amber status lamp and a red emergency-stop mushroom.

Original pixel art generated here, per the asset rule in CLAUDE.md: nothing is traced or sampled.
Usage: python operator_panel_textures.py <textures/blocks dir>
"""
import struct
import sys
import zlib

import numpy as np


def write_png(path, rgba):
    h, w, _ = rgba.shape
    raw = b''.join(b'\x00' + rgba[y].astype(np.uint8).tobytes() for y in range(h))

    def chunk(kind, data):
        c = struct.pack('>I', len(data)) + kind + data
        return c + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)

    png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
    png += chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
    with open(path, 'wb') as f:
        f.write(png)
    print('wrote', path)


def steel(seed):
    """Horizontal brushing: each row a slightly different grey, with fine speckle."""
    rng = np.random.default_rng(seed)
    img = np.zeros((16, 16, 4))
    rows = 118 + rng.integers(-10, 11, size=16)
    for y in range(16):
        img[y, :, :3] = rows[y] + rng.integers(-4, 5, size=(16, 1))
    img[:, :, 2] += 8            # a faint blue cast, cold steel rather than stone
    img[:, :, 3] = 255
    # Darker edge band, so adjacent faces read as separate panels.
    img[0, :, :3] -= 28
    img[15, :, :3] -= 34
    img[:, 0, :3] -= 22
    img[:, 15, :3] -= 22
    return np.clip(img, 0, 255)


def disc(img, cx, cy, r, rim, face, highlight):
    for y in range(16):
        for x in range(16):
            d = ((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2) ** 0.5
            if d <= r:
                img[y, x, :3] = face
                if d > r - 0.9:
                    img[y, x, :3] = rim
            if ((x + 0.5 - (cx - r * 0.35)) ** 2 + (y + 0.5 - (cy - r * 0.35)) ** 2) ** 0.5 < r * 0.3:
                img[y, x, :3] = highlight


def desk():
    img = steel(7)
    img[1:15, 1:15, :3] = [46, 50, 58]           # the black control fascia
    img[1, 1:15, :3] = [70, 76, 86]
    disc(img, 4.5, 5.0, 2.4, [20, 60, 22], [58, 176, 64], [160, 240, 160])     # start
    disc(img, 4.5, 11.0, 1.7, [80, 60, 10], [236, 170, 34], [255, 236, 170])    # status lamp
    disc(img, 11.0, 8.0, 3.6, [70, 10, 10], [196, 30, 30], [255, 140, 140])     # e-stop mushroom
    for x in range(7, 15):                        # yellow e-stop surround, bottom edge
        img[13, x, :3] = [214, 186, 30]
    return np.clip(img, 0, 255)


if __name__ == '__main__':
    out = sys.argv[1].rstrip('/')
    write_png(f'{out}/operator_panel_side.png', steel(3))
    write_png(f'{out}/operator_panel_top.png', desk())
