"""Pixel-art icons for the Chysalis add-ons: a 32x32 design scaled 4x to 128x128.
Same badge as Chysalis' icon (dark rounded square with a lighter rim), one colour per add-on."""
import sys
from PIL import Image

N = 32


def hexc(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


class Canvas:
    def __init__(self):
        self.px = [[(0, 0, 0, 0)] * N for _ in range(N)]

    def set(self, x, y, c):
        if 0 <= x < N and 0 <= y < N:
            self.px[y][x] = c

    def rect(self, x0, y0, x1, y1, c):  # inclusive
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, c)

    def get(self, x, y):
        return self.px[y][x]

    def save(self, path, scale=4):
        im = Image.new('RGBA', (N, N))
        for y in range(N):
            for x in range(N):
                im.putpixel((x, y), self.px[y][x])
        im.resize((N * scale, N * scale), Image.NEAREST).save(path)


def badge(c, fill, rim, inner):
    r = 4
    for y in range(N):
        for x in range(N):
            # rounded corners
            cx = min(x, N - 1 - x)
            cy = min(y, N - 1 - y)
            if cx < r and cy < r and (r - cx - 0.5) ** 2 + (r - cy - 0.5) ** 2 > r * r:
                continue
            edge = cx == 0 or cy == 0 or (cx < r and cy < r and (r - cx - 0.5) ** 2 + (r - cy - 0.5) ** 2 > (r - 1.2) ** 2)
            c.set(x, y, rim if edge else fill)
    # soft inner vignette ring
    for y in range(1, N - 1):
        for x in range(1, N - 1):
            cx = min(x, N - 1 - x)
            cy = min(y, N - 1 - y)
            if (cx == 1 or cy == 1) and c.get(x, y) == fill:
                c.set(x, y, inner)


def outline(c, color, inside):
    """1px outline around all pixels for which inside(colour) is true."""
    src = [row[:] for row in c.px]
    for y in range(N):
        for x in range(N):
            if inside(src[y][x]):
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                xx, yy = x + dx, y + dy
                if 0 <= xx < N and 0 <= yy < N and inside(src[yy][xx]):
                    if src[y][x][3] > 0:
                        c.set(x, y, color)
                    break


# ---------------------------------------------------------------- Unfurl: a chiseled door swinging open

def unfurl(path):
    c = Canvas()
    BG, RIM, INNER = hexc('#2c1b40'), hexc('#6a4a92'), hexc('#3a2654')
    badge(c, BG, RIM, INNER)
    FRAME, FRAME_D = hexc('#9a6a44'), hexc('#5a3820')
    GLOW1, GLOW2, GLOW3 = hexc('#fffbe6'), hexc('#ffeaa0'), hexc('#ffcf5a')
    WOOD, WOOD_L, WOOD_D, WOOD_DD = hexc('#b9561a'), hexc('#e0843a'), hexc('#8a3a0e'), hexc('#5a2306')
    OUT = hexc('#140a1e')
    # floor line
    c.rect(4, 27, 27, 27, hexc('#4b3466'))
    # doorway (light pours out)
    x0, x1, y0, y1 = 13, 24, 5, 26
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            d = abs(x - (x0 + x1) / 2) / ((x1 - x0) / 2)
            c.set(x, y, GLOW1 if d < 0.35 else GLOW2 if d < 0.75 else GLOW3)
    # light spill on the floor
    for i, x in enumerate(range(x0 - 1, x1 + 3)):
        c.set(x, 27, GLOW3 if i % 2 == 0 else hexc('#c98a3a'))
    # frame
    c.rect(x0 - 1, y0 - 1, x1 + 1, y0 - 1, FRAME)
    c.rect(x0 - 1, y0 - 1, x0 - 1, y1, FRAME)
    c.rect(x1 + 1, y0 - 1, x1 + 1, y1, FRAME)
    c.rect(x0 - 2, y0 - 2, x1 + 2, y0 - 2, FRAME_D)
    c.rect(x0 - 2, y0 - 2, x0 - 2, y1, FRAME_D)
    c.rect(x1 + 2, y0 - 2, x1 + 2, y1, FRAME_D)
    # open door panel: hinge at the left jamb, free edge swung towards the viewer (perspective trapezoid)
    hx, fx = x0 - 2, 4
    for x in range(fx, hx + 1):
        t = (hx - x) / (hx - fx)          # 0 at hinge, 1 at free edge
        top = round(y0 - 1 - 3 * t)
        bot = round(y1 + 0 + 1 * t)
        for y in range(top, bot + 1):
            col = WOOD
            if x == hx - 1 or x == fx + 1:
                col = WOOD_D
            if (x - fx) % 3 == 0 and x not in (fx, hx):
                col = WOOD_L if (x - fx) % 6 == 0 else WOOD_D
            c.set(x, y, col)
        # chiseled panel insets
        c.set(x, round(top + (bot - top) * 0.33), WOOD_DD)
        c.set(x, round(top + (bot - top) * 0.66), WOOD_DD)
    # knob on the free edge
    ky = 17
    c.rect(fx + 1, ky, fx + 2, ky + 1, hexc('#ffe27a'))
    c.set(fx + 1, ky + 1, hexc('#c8901e'))
    outline(c, OUT, lambda p: p in (WOOD, WOOD_L, WOOD_D, WOOD_DD, hexc('#ffe27a'), hexc('#c8901e')))
    c.save(path)


# ---------------------------------------------------------------- Propagate: a torch settling onto a carved step

def propagate(path):
    c = Canvas()
    BG, RIM, INNER = hexc('#17352a'), hexc('#4f9a6c'), hexc('#1f4636')
    badge(c, BG, RIM, INNER)
    ST, ST_L, ST_D, ST_TOP = hexc('#8d939c'), hexc('#b3b8c0'), hexc('#646a74'), hexc('#cfd3d9')
    OUT = hexc('#0c1a14')
    # carved block: high left part, carved-down right step
    bx0, bx1, ytop_hi, ytop_lo, ybot = 4, 27, 12, 20, 27
    step_x = 13
    for x in range(bx0, bx1 + 1):
        top = ytop_hi if x < step_x else ytop_lo
        for y in range(top, ybot + 1):
            col = ST
            if y == top:
                col = ST_TOP
            elif (x * 7 + y * 3) % 11 == 0:
                col = ST_D
            elif (x * 5 + y * 13) % 17 == 0:
                col = ST_L
            if x == bx1 or y == ybot:
                col = ST_D
            c.set(x, y, col)
    # chisel grooves on the step's riser (voxel look)
    for y in range(ytop_hi + 1, ytop_lo):
        c.set(step_x, y, ST_D)
    for x in range(step_x, bx1 + 1, 4):
        c.set(x, ytop_lo + 1, ST_D)
    outline(c, OUT, lambda p: p in (ST, ST_L, ST_D, ST_TOP))
    # real torch standing on the carved step (big, readable)
    tx = 21
    stick_top = ytop_lo - 8
    c.rect(tx, stick_top, tx + 1, ytop_lo - 1, hexc('#9a6430'))
    c.rect(tx + 1, stick_top, tx + 1, ytop_lo - 1, hexc('#5e3a17'))
    c.rect(tx - 1, stick_top - 4, tx + 2, stick_top - 1, hexc('#ff9a2a'))
    c.rect(tx, stick_top - 4, tx + 1, stick_top - 1, hexc('#ffd84a'))
    c.rect(tx, stick_top - 3, tx, stick_top - 2, hexc('#fffbe0'))
    c.rect(tx, stick_top - 5, tx + 1, stick_top - 5, hexc('#ff9a2a'))
    outline(c, OUT, lambda p: p in (hexc('#9a6430'), hexc('#5e3a17'), hexc('#ff9a2a'), hexc('#ffd84a'), hexc('#fffbe0')))
    # warm light on the step
    for x in range(tx - 4, tx + 6):
        if c.get(x, ytop_lo) == ST_TOP:
            c.set(x, ytop_lo, hexc('#f6e7a6'))
    # down arrow onto the carved step: "things settle onto the surface"
    AR = hexc('#ffffff')
    ax = 16
    c.rect(ax, 5, ax + 1, 13, AR)
    for i in range(4):
        c.rect(ax - 3 + i, 13 + i, ax + 4 - i, 13 + i, AR)
    outline(c, hexc('#0c1a14'), lambda p: p == AR)
    c.save(path)


if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else '.'
    unfurl(out + '/unfurl_icon.png')
    propagate(out + '/propagate_icon.png')
