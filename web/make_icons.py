#!/usr/bin/env python3
"""Generate PWA icons without external dependencies or checked-in binaries."""
from pathlib import Path
import math
import struct
import zlib

OUTPUT = Path(__file__).parent / "icons"
OUTPUT.mkdir(exist_ok=True)

def distance(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    t = max(0, min(1, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)))
    return math.hypot(px - (ax + t * dx), py - (ay + t * dy))

def make(size):
    rows = []
    for y in range(size):
        line = bytearray([0])
        ny = (y + .5) / size
        for x in range(size):
            nx = (x + .5) / size
            rx, ry = min(nx, 1 - nx), min(ny, 1 - ny)
            background = (13, 16, 32)
            # rounded gradient tile with generous Apple touch-icon margin
            inside = (0.07 <= nx <= .93 and .07 <= ny <= .93)
            corner = .20
            if inside and rx < corner and ry < corner:
                inside = (rx - corner) ** 2 + (ry - corner) ** 2 <= (corner - .07) ** 2
            if inside:
                t = (nx + ny) / 2
                color = (int(245 * (1-t) + 107*t),
                         int(88 * (1-t) + 79*t),
                         int(158 * (1-t) + 238*t))
                bg = color
            else:
                bg = background
            # distinctive V monogram, two straight, thick strokes
            v = min(distance(nx,ny,.29,.29,.50,.72),
                    distance(nx,ny,.50,.72,.71,.29))
            dot = (nx - .725)**2 + (ny-.716)**2
            if inside and (v < .048 or dot < .0019):
                bg = (255, 251, 254)
            line.extend(bg)
        rows.append(bytes(line))
    raw = b"".join(rows)
    def chunk(kind, content):
        return struct.pack(">I", len(content)) + kind + content + struct.pack(">I", zlib.crc32(kind + content) & 0xffffffff)
    png = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    (OUTPUT / f"icon-{size}.png").write_bytes(png)

for size in (180, 192, 512):
    make(size)
print("Generated iPhone and PWA icons")
