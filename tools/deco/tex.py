"""Small pixel-art helpers for the deco block textures. Every texture is 16x16 RGBA and seeded by name,
so re-running the generator gives the same pixels."""
import random
import zlib
from PIL import Image

C = dict(
    white=(226, 223, 220), offwhite=(208, 203, 198), plastic=(232, 232, 232), grey=(128, 130, 134),
    lgrey=(170, 172, 175), dgrey=(70, 72, 76), black=(30, 31, 33), steel=(152, 158, 164), dsteel=(98, 103, 109),
    rust=(139, 82, 46), yellow=(224, 184, 36), red=(176, 36, 36), dred=(110, 20, 20), green=(62, 150, 72),
    dgreen=(40, 90, 48), blue=(52, 82, 152), lblue=(120, 170, 220), wood=(112, 76, 48), dwood=(70, 46, 30),
    concrete=(150, 150, 146), copper=(184, 112, 70), lead=(104, 107, 112), orange=(222, 122, 32),
    purple=(132, 62, 170), tan=(190, 170, 130), sage=(122, 143, 128), cyan=(84, 196, 206), cream=(236, 228, 200),
    security=(60, 95, 130), office=(198, 200, 194), ftile=(14, 31, 130), brick=(150, 72, 52), olive=(96, 104, 60),
    khaki=(176, 156, 104), glow=(255, 250, 220), screen=(30, 70, 50), screenon=(70, 220, 120),
)


def col(c):
    if isinstance(c, str):
        c = C[c]
    return tuple(c) + ((255,) if len(c) == 3 else ())


def shade(c, f):
    c = col(c)
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3],)


class T:
    """A texture being drawn."""

    def __init__(self, name, base='grey', alpha=255):
        self.name = name
        self.rng = random.Random(zlib.crc32(name.encode()))
        b = col(base)
        self.im = Image.new('RGBA', (16, 16), b[:3] + (alpha,))
        self.px = self.im.load()

    def set(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            self.px[x, y] = col(c)

    def get(self, x, y):
        return self.px[x, y]

    def fill(self, c, x0=0, y0=0, x1=16, y1=16):
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.set(x, y, c)
        return self

    def clear(self, x0=0, y0=0, x1=16, y1=16):
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.px[x, y] = (0, 0, 0, 0)
        return self

    def noise(self, amt=0.06, x0=0, y0=0, x1=16, y1=16):
        for y in range(y0, y1):
            for x in range(x0, x1):
                p = self.px[x, y]
                if p[3] == 0:
                    continue
                f = 1 + self.rng.uniform(-amt, amt)
                self.px[x, y] = tuple(max(0, min(255, int(v * f))) for v in p[:3]) + (p[3],)
        return self

    def rect(self, c, x0, y0, x1, y1):
        """Outline from (x0,y0) to (x1,y1) inclusive."""
        for x in range(x0, x1 + 1):
            self.set(x, y0, c)
            self.set(x, y1, c)
        for y in range(y0, y1 + 1):
            self.set(x0, y, c)
            self.set(x1, y, c)
        return self

    def border(self, c=None, f=0.75):
        if c is None:
            for i in range(16):
                for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
                    self.px[x, y] = shade(self.px[x, y], f)
            return self
        return self.rect(c, 0, 0, 15, 15)

    def bevel(self, light=1.15, dark=0.75):
        for i in range(16):
            self.px[i, 0] = shade(self.px[i, 0], light)
            self.px[0, i] = shade(self.px[0, i], light)
            self.px[i, 15] = shade(self.px[i, 15], dark)
            self.px[15, i] = shade(self.px[15, i], dark)
        return self

    def hline(self, y, c, x0=0, x1=15):
        for x in range(x0, x1 + 1):
            self.set(x, y, c)
        return self

    def vline(self, x, c, y0=0, y1=15):
        for y in range(y0, y1 + 1):
            self.set(x, y, c)
        return self

    def diag(self, c1, c2, w=4, x0=0, y0=0, x1=16, y1=16, flip=False):
        for y in range(y0, y1):
            for x in range(x0, x1):
                k = (x - y if flip else x + y) // w
                self.set(x, y, c1 if k % 2 == 0 else c2)
        return self

    def grid(self, n, c):
        step = 16 // n
        for i in range(0, 16, step):
            self.hline(i, c)
            self.vline(i, c)
        return self

    def rivets(self, c='lgrey', inset=1, step=None):
        pts = [(inset, inset), (15 - inset, inset), (inset, 15 - inset), (15 - inset, 15 - inset)]
        if step:
            pts = [(x, y) for x in range(inset, 16, step) for y in (inset, 15 - inset)]
        for (x, y) in pts:
            self.set(x, y, c)
        return self

    def speckle(self, c, n=12):
        for _ in range(n):
            self.set(self.rng.randrange(16), self.rng.randrange(16), c)
        return self

    def circle(self, cx, cy, r, c, filled=True):
        for y in range(16):
            for x in range(16):
                d = ((x + .5 - cx) ** 2 + (y + .5 - cy) ** 2) ** .5
                if (filled and d <= r) or (not filled and r - 1 < d <= r):
                    self.set(x, y, c)
        return self

    def text(self, s, x, y, c, scale=1):
        for ch in s:
            g = FONT.get(ch.upper(), FONT[' '])
            for gy, row in enumerate(g):
                for gx, bit in enumerate(row):
                    if bit == '#':
                        for sy in range(scale):
                            for sx in range(scale):
                                self.set(x + gx * scale + sx, y + gy * scale + sy, c)
            x += 4 * scale
        return self

    def ctext(self, s, y, c, scale=1):
        w = len(s) * 4 * scale - scale
        return self.text(s, (16 - w) // 2, y, c, scale)

    def arrow(self, c, direction='up', y0=3):
        rows = ['...#...', '..###..', '.#####.', '#######', '..###..', '..###..', '..###..', '..###..', '..###..']
        for j, row in enumerate(rows):
            for i, bit in enumerate(row):
                if bit == '#':
                    x, y = 4 + i, y0 + j
                    if direction == 'up':
                        self.set(x, y, c)
                    elif direction == 'down':
                        self.set(x, 15 - y, c)
                    elif direction == 'left':
                        self.set(y, x, c)
                    else:
                        self.set(15 - y, x, c)
        return self

    def save(self, path):
        self.im.save(path)


FONT = {
    ' ': ['...', '...', '...', '...', '...'],
    'A': ['.#.', '#.#', '###', '#.#', '#.#'], 'B': ['##.', '#.#', '##.', '#.#', '##.'],
    'C': ['.##', '#..', '#..', '#..', '.##'], 'D': ['##.', '#.#', '#.#', '#.#', '##.'],
    'E': ['###', '#..', '##.', '#..', '###'], 'F': ['###', '#..', '##.', '#..', '#..'],
    'G': ['.##', '#..', '#.#', '#.#', '.##'], 'H': ['#.#', '#.#', '###', '#.#', '#.#'],
    'I': ['###', '.#.', '.#.', '.#.', '###'], 'J': ['..#', '..#', '..#', '#.#', '.#.'],
    'K': ['#.#', '#.#', '##.', '#.#', '#.#'], 'L': ['#..', '#..', '#..', '#..', '###'],
    'M': ['#.#', '###', '###', '#.#', '#.#'], 'N': ['##.', '#.#', '#.#', '#.#', '#.#'],
    'O': ['.#.', '#.#', '#.#', '#.#', '.#.'], 'P': ['##.', '#.#', '##.', '#..', '#..'],
    'Q': ['.#.', '#.#', '#.#', '##.', '.##'], 'R': ['##.', '#.#', '##.', '#.#', '#.#'],
    'S': ['.##', '#..', '.#.', '..#', '##.'], 'T': ['###', '.#.', '.#.', '.#.', '.#.'],
    'U': ['#.#', '#.#', '#.#', '#.#', '###'], 'V': ['#.#', '#.#', '#.#', '#.#', '.#.'],
    'W': ['#.#', '#.#', '###', '###', '#.#'], 'X': ['#.#', '#.#', '.#.', '#.#', '#.#'],
    'Y': ['#.#', '#.#', '.#.', '.#.', '.#.'], 'Z': ['###', '..#', '.#.', '#..', '###'],
    '0': ['###', '#.#', '#.#', '#.#', '###'], '1': ['.#.', '##.', '.#.', '.#.', '###'],
    '2': ['##.', '..#', '.#.', '#..', '###'], '3': ['##.', '..#', '.#.', '..#', '##.'],
    '4': ['#.#', '#.#', '###', '..#', '..#'], '5': ['###', '#..', '##.', '..#', '##.'],
    '6': ['.##', '#..', '###', '#.#', '###'], '7': ['###', '..#', '.#.', '.#.', '.#.'],
    '8': ['###', '#.#', '###', '#.#', '###'], '9': ['###', '#.#', '###', '..#', '##.'],
    '-': ['...', '...', '###', '...', '...'], '!': ['.#.', '.#.', '.#.', '...', '.#.'],
    '[': ['##.', '#..', '#..', '#..', '##.'], ']': ['.##', '..#', '..#', '..#', '.##'],
}
