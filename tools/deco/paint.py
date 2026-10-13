"""Painters for the deco textures. Each factory returns a function that draws onto a tex.T."""
from tex import T, C, col, shade


def _base(t, c, n=0.05):
    t.fill(c)
    if n:
        t.noise(n)


def solid(c, n=0.05):
    return lambda t: _base(t, c, n)


def panel(c, n=0.04, bev=True, rivet=None):
    def f(t):
        _base(t, c, n)
        if bev:
            t.bevel()
        if rivet:
            t.rivets(rivet)
    return f


def tiles(c, grout=None, n=2, nz=0.05):
    def f(t):
        _base(t, c, nz)
        t.grid(n, grout or shade(c, 0.7))
    return f


def stripes(c1, c2, w=4, flip=False):
    def f(t):
        t.diag(col(c1), col(c2), w, flip=flip)
        t.noise(0.04)
    return f


def trim(base, stripe, y0=6, y1=9):
    def f(t):
        _base(t, base, 0.03)
        t.fill(stripe, 0, y0, 16, y1)
        t.noise(0.03, 0, y0, 16, y1)
        t.hline(y0 - 1, shade(base, 0.8))
        t.hline(y1, shade(base, 0.8))
    return f


def concrete(c='concrete', cracks=0, ties=True, n=0.07):
    def f(t):
        _base(t, c, n)
        if ties:
            for (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)):
                t.set(x, y, shade(c, 0.6))
        x, y = t.rng.randrange(4, 12), 0
        for _ in range(cracks):
            while y < 16:
                t.set(x, y, shade(c, 0.45))
                y += 1
                x += t.rng.choice((-1, 0, 1))
            x, y = t.rng.randrange(2, 14), t.rng.randrange(0, 8)
    return f


def steel(c='steel', rust=0.0, rivet=True, seams=True):
    def f(t):
        _base(t, c, 0.05)
        if seams:
            t.hline(7, shade(c, 0.7))
            t.vline(0, shade(c, 0.8))
        if rivet:
            t.rivets(shade(c, 1.3), inset=1, step=4)
        if rust:
            for _ in range(int(80 * rust)):
                x, y = t.rng.randrange(16), t.rng.randrange(16)
                t.set(x, y, shade('rust', t.rng.uniform(0.8, 1.2)))
                if t.rng.random() < 0.5:
                    t.set(x, y + 1, shade('rust', 0.7))
    return f


def corrugated(c, rust=0.0):
    def f(t):
        for x in range(16):
            t.vline(x, shade(c, (1.12, 1.0, 0.85, 1.0)[x % 4]))
        t.noise(0.04)
        if rust:
            for _ in range(int(60 * rust)):
                t.set(t.rng.randrange(16), t.rng.randrange(16), shade('rust', t.rng.uniform(0.8, 1.2)))
    return f


def brick(c, mortar=(185, 180, 170), h=4, w=8, cracks=0):
    def f(t):
        _base(t, c, 0.1)
        for y in range(0, 16, h):
            t.hline(y, mortar)
            off = 0 if (y // h) % 2 == 0 else w // 2
            for x in range(off, 16 + w, w):
                t.vline(x % 16, mortar, y, y + h - 1)
        for _ in range(cracks * 6):
            t.set(t.rng.randrange(16), t.rng.randrange(16), shade(c, 0.4))
    return f


def planks(c, vertical=True):
    def f(t):
        _base(t, c, 0.04)
        for i in range(0, 16, 4):
            if vertical:
                t.vline(i, shade(c, 0.7))
            else:
                t.hline(i, shade(c, 0.7))
        for _ in range(14):
            x, y = t.rng.randrange(16), t.rng.randrange(16)
            t.set(x, y, shade(c, 0.85))
            t.set(x, y + 1 if vertical else y, shade(c, 0.85))
    return f


def foam(c):
    def f(t):
        for y in range(16):
            for x in range(16):
                cell = ((x // 4) + (y // 4)) % 2
                d = (x % 4) + (y % 4)
                t.set(x, y, shade(c, 0.7 + 0.1 * d if cell else 1.2 - 0.1 * d))
    return f


def padded(c):
    def f(t):
        _base(t, c, 0.02)
        for i in range(16):
            for k in range(0, 16, 8):
                t.set((i + k) % 16, i, shade(c, 0.82))
                t.set((k - i) % 16, i, shade(c, 0.82))
        for (x, y) in ((0, 0), (8, 8), (0, 8), (8, 0)):
            t.set(x, y, shade(c, 0.6))
    return f


def mesh(c, step=4, alpha_bg=True):
    def f(t):
        t.clear() if alpha_bg else t.fill('dgrey')
        for i in range(0, 16, step):
            t.hline(i, c)
            t.vline(i, c)
        t.noise(0.08)
    return f


def diamond_plate(c, rust=0.0):
    def f(t):
        _base(t, c, 0.04)
        for y in range(1, 16, 4):
            for x in range(1 + (y // 4 % 2) * 2, 16, 4):
                t.set(x, y, shade(c, 1.3))
                t.set(x + 1, y + 1, shade(c, 0.75))
        for _ in range(int(60 * rust)):
            t.set(t.rng.randrange(16), t.rng.randrange(16), shade('rust', t.rng.uniform(0.8, 1.2)))
    return f


def grate(c, holes=True):
    def f(t):
        t.fill(c)
        t.noise(0.06)
        if holes:
            for y in range(1, 16, 3):
                for x in range(1, 15):
                    t.px[x, y] = (0, 0, 0, 0)
                    t.px[x, y + 1] = (0, 0, 0, 0) if y + 1 < 15 else t.px[x, y + 1]
        t.border(shade(c, 0.8))
    return f


def ribs(c, step=2):
    def f(t):
        _base(t, c, 0.03)
        for y in range(0, 16, step):
            t.hline(y, shade(c, 0.75))
    return f


def speckle(c, dots, n=30):
    def f(t):
        _base(t, c, 0.03)
        for d in dots:
            t.speckle(d, n)
    return f


def epoxy(c):
    def f(t):
        _base(t, c, 0.02)
        for i in range(5):
            t.set(2 + i, 2 + i // 2, shade(c, 1.2))
        t.border(shade(c, 0.9))
    return f


def floor_paint(paint, kind):
    def f(t):
        t.clear()
        p = col(paint)
        if kind == 'edge':
            t.diag(p, 'black', 3, 0, 0, 16, 4)
        elif kind == 'arrow':
            t.arrow(p)
        elif kind == 'line':
            t.fill(p, 6, 0, 10, 16)
        t.noise(0.05)
    return f


def glass(tint=(200, 225, 235), alpha=90, frame='steel', wires=False, frost=False, cracks=False, tinted=False):
    def f(t):
        a = 200 if frost else alpha
        t.fill(tuple(col(tint)[:3]) + (a,))
        for y in range(16):
            for x in range(16):
                p = t.px[x, y]
                if frost:
                    t.px[x, y] = shade(p, 1 + t.rng.uniform(-0.04, 0.04))[:3] + (a,)
        if not frost and not tinted:
            for i in range(3):
                t.set(3 + i, 2 + i, (255, 255, 255, 150))
        if wires:
            for i in range(0, 16, 4):
                for j in range(16):
                    t.set(i + 2, j, (90, 90, 90, 255))
                    t.set(j, i + 2, (90, 90, 90, 255))
        if cracks:
            x, y = 7, 7
            for d in ((1, 1), (-1, 1), (1, -1), (-1, -1), (1, 0), (0, -1)):
                cx, cy = x, y
                for _ in range(t.rng.randrange(4, 9)):
                    cx += d[0] if t.rng.random() < 0.8 else 0
                    cy += d[1] if t.rng.random() < 0.8 else 0
                    t.set(cx, cy, (240, 240, 240, 230))
        if frame:
            t.rect(frame, 0, 0, 15, 15)
    return f


def bars(c, step=4):
    def f(t):
        t.clear()
        for x in range(1, 16, step):
            t.vline(x, c)
            t.vline(x + 1, shade(c, 0.75))
        t.hline(0, c)
        t.hline(15, c)
    return f


def chain_link(c):
    def f(t):
        t.clear()
        for y in range(16):
            for x in range(16):
                if (x + y) % 4 == 0 or (x - y) % 4 == 0:
                    t.set(x, y, shade(c, 1 + t.rng.uniform(-0.1, 0.1)))
    return f


def cloth(c, folds=True):
    def f(t):
        _base(t, c, 0.03)
        if folds:
            for x in range(0, 16, 4):
                t.vline(x, shade(c, 0.85))
    return f


def camo(c1, c2, c3):
    def f(t):
        t.clear()
        for y in range(16):
            for x in range(16):
                if (x + y) % 3 and t.rng.random() < 0.7:
                    t.set(x, y, t.rng.choice((col(c1), col(c2), col(c3))))
    return f


def sign(bg, fg, lines, border=None, icon=None, scale=1):
    """A plate with up to two short lines of text and an optional icon above them."""
    def f(t):
        _base(t, bg, 0.02)
        if border:
            t.rect(border, 0, 0, 15, 15)
        y = 2 if icon else (16 - (len(lines) * 6 * scale - scale)) // 2
        if icon:
            ICONS[icon](t, fg)
            y = 10
        for line in lines:
            t.ctext(line, y, fg, scale)
            y += 6 * scale
    return f


def icon_only(bg, fg, icon, border=None):
    def f(t):
        _base(t, bg, 0.02)
        if border:
            t.rect(border, 0, 0, 15, 15)
        ICONS[icon](t, fg, big=True)
    return f


def decal(paint, kind, alpha=255):
    """Paint on a transparent background: digits, stencilled words, splats."""
    def f(t):
        t.clear()
        p = col(paint)[:3] + (alpha,)
        if kind.startswith('digit:'):
            t.ctext(kind[6:], 3, p, 2)
        elif kind.startswith('words:'):
            lines = kind[6:].split('/')
            y = (16 - (len(lines) * 6 - 1)) // 2
            for line in lines:
                t.ctext(line, y, p)
                y += 6
        elif kind == 'splat':
            for _ in range(5):
                t.circle(t.rng.uniform(4, 12), t.rng.uniform(4, 12), t.rng.uniform(1.5, 3.5), p)
            for _ in range(4):
                x = t.rng.randrange(3, 13)
                for y in range(t.rng.randrange(6, 12), 16):
                    t.set(x, y, p)
        elif kind == 'pool':
            t.circle(8, 8, 6, p)
            for _ in range(5):
                t.circle(t.rng.uniform(2, 14), t.rng.uniform(2, 14), t.rng.uniform(1, 2.5), p)
        elif kind == 'tag':
            t.circle(8, 8, 6, p, filled=False)
            t.ctext('CI', 6, p)
            for i in range(4, 12):
                t.set(i, 12 + (i % 2), p)
        elif kind == 'tag2':
            t.text('CI', 1, 2, p, 2)
            for i in range(16):
                t.set(i, 14 - i // 3, p)
        elif kind == 'scorch':
            for y in range(16):
                for x in range(16):
                    d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** .5
                    if d < 7.5 and t.rng.random() < 1.1 - d / 7.5:
                        t.set(x, y, shade((25, 22, 20), 1 + t.rng.uniform(-.2, .2))[:3] + (int(255 * (1 - d / 9)),))
        elif kind == 'shards':
            for _ in range(14):
                x, y = t.rng.randrange(1, 15), t.rng.randrange(1, 15)
                t.set(x, y, (220, 235, 240, 220))
                t.set(x + 1, y, (190, 210, 220, 200))
        elif kind == 'papers':
            for _ in range(4):
                x, y = t.rng.randrange(0, 9), t.rng.randrange(0, 9)
                t.fill((236, 232, 222), x, y, x + 6, y + 7)
                for j in range(y + 1, y + 6, 2):
                    t.hline(j, (120, 120, 120), x + 1, x + 4)
        elif kind == 'goo':
            for _ in range(6):
                t.circle(t.rng.uniform(3, 13), t.rng.uniform(3, 13), t.rng.uniform(1.5, 4), p)
            t.noise(0.1)
        elif kind == 'holes':
            for _ in range(7):
                x, y = t.rng.randrange(2, 14), t.rng.randrange(2, 14)
                t.set(x, y, (25, 25, 25, 255))
                t.set(x + 1, y, (60, 60, 60, 200))
                t.set(x, y + 1, (60, 60, 60, 200))
        elif kind == 'guide':
            t.fill(p, 0, 6, 16, 10)
            t.hline(6, shade(p, 1.2))
        elif kind == 'wires':
            for x in (4, 7, 11):
                for y in range(16):
                    t.set(x + (y // 5) % 2, y, ((40, 40, 40), (150, 30, 30), (40, 60, 150))[x % 3])
            t.set(5, 15, (255, 240, 120))
            t.set(12, 14, (255, 240, 120))
        elif kind == 'barbed':
            for i in range(16):
                t.set(i, i, (130, 130, 130))
                t.set(15 - i, i, (130, 130, 130))
                if i % 3 == 0:
                    t.set(i, i - 1, (180, 180, 180))
                    t.set(15 - i, i + 1, (180, 180, 180))
        elif kind == 'leaves':
            for _ in range(70):
                t.set(t.rng.randrange(16), t.rng.randrange(16), shade(paint, t.rng.uniform(0.7, 1.2)))
    return f


# ---- icons (drawn in the top 9 pixels, or big across the whole plate) ----

def _logo(t, c, big=False):
    r = 6.5 if big else 4
    cy = 8 if big else 5
    t.circle(8, cy, r, c, filled=False)
    t.circle(8, cy, r - 2.5, c, filled=False)
    for dx, dy in ((0, -1), (-0.87, 0.5), (0.87, 0.5)):
        for k in range(int(r - 2), int(r + 1)):
            t.set(int(8 + dx * k), int(cy + dy * k), c)


def _trefoil(t, c, big=False):
    cy = 8 if big else 5
    t.circle(8, cy, 1.2, c)
    for (dx, dy) in ((0, -3.5), (-3, 2), (3, 2)):
        t.circle(8 + dx, cy + dy, 2.3 if big else 1.6, c)


def _bio(t, c, big=False):
    cy = 8 if big else 5
    for (dx, dy) in ((0, -3), (-2.6, 1.5), (2.6, 1.5)):
        t.circle(8 + dx, cy + dy, 3 if big else 2.2, c, filled=False)
    t.circle(8, cy, 1, c)


def _bolt(t, c, big=False):
    pts = [(9, 1), (8, 2), (7, 3), (6, 4), (7, 4), (8, 4), (9, 4), (8, 5), (7, 6), (6, 7), (7, 7), (8, 7)]
    for (x, y) in pts:
        t.set(x, y + (4 if big else 0), c)
        if big:
            t.set(x + 1, y + 4, c)


def _eye(t, c, big=False):
    cy = 8 if big else 5
    for x in range(3, 13):
        d = abs(x - 7.5)
        h = int(3 - d * 0.5)
        t.set(x, cy - h, c)
        t.set(x, cy + h, c)
    t.circle(8, cy, 1.6, c)


def _brain(t, c, big=False):
    cy = 8 if big else 5
    t.circle(6.5, cy, 3, c, filled=False)
    t.circle(9.5, cy, 3, c, filled=False)
    t.vline(8, c, cy - 2, cy + 2)


def _cross(t, c, big=False):
    cy = 8 if big else 5
    t.fill(c, 7, cy - 3, 9, cy + 3)
    t.fill(c, 5, cy - 1, 11, cy + 1)


def _fork(t, c, big=False):
    cy = 8 if big else 4
    for x in (6, 8, 10):
        t.vline(x, c, cy - 3, cy)
    t.hline(cy, c, 6, 10)
    t.vline(8, c, cy, cy + 4)


def _shield(t, c, big=False):
    cy = 8 if big else 5
    t.fill(c, 5, cy - 3, 11, cy + 1)
    for i in range(3):
        t.hline(cy + 1 + i, c, 6 + i, 10 - i)


def _flask(t, c, big=False):
    cy = 8 if big else 5
    t.vline(7, c, cy - 3, cy)
    t.vline(9, c, cy - 3, cy)
    for i in range(4):
        t.hline(cy + i, c, 7 - i, 9 + i)


def _person(t, c, big=False):
    t.circle(8, 2.5, 1.2, c)
    t.vline(8, c, 4, 6)
    t.hline(5, c, 6, 10)
    t.set(7, 7, c)
    t.set(9, 7, c)
    t.set(6, 8, c)
    t.set(10, 8, c)


ICONS = dict(logo=_logo, trefoil=_trefoil, bio=_bio, bolt=_bolt, eye=_eye, brain=_brain, cross=_cross, fork=_fork,
             shield=_shield, flask=_flask, person=_person)


# ---- machine and furniture faces ----

def screen(on=True, lines='green'):
    def f(t):
        t.fill('black')
        t.rect('dgrey', 0, 0, 15, 15)
        if on:
            lc = {'green': (70, 220, 120), 'amber': (240, 170, 40), 'static': None, 'white': (220, 230, 240)}[lines]
            if lc is None:
                for y in range(1, 15):
                    for x in range(1, 15):
                        v = t.rng.randrange(40, 255)
                        t.set(x, y, (v, v, v))
            else:
                for y in range(3, 13, 2):
                    t.hline(y, lc, 2, 2 + t.rng.randrange(4, 12))
        else:
            t.fill((22, 32, 28), 1, 1, 15, 15)
            t.set(3, 3, (60, 70, 66))
    return f


def broken_screen():
    def f(t):
        screen(False)(t)
        x, y = 8, 7
        for d in ((1, 1), (-1, 1), (1, -1), (-1, -1), (1, 0)):
            cx, cy = x, y
            for _ in range(6):
                cx += d[0]
                cy += d[1] if t.rng.random() < .7 else 0
                t.set(cx, cy, (190, 200, 200))
    return f


def server_front():
    def f(t):
        t.fill((26, 27, 30))
        for y in range(1, 16, 3):
            t.hline(y, (50, 52, 56), 1, 14)
            t.set(2, y, (60, 230, 90) if t.rng.random() < .7 else (240, 160, 40))
            t.set(4, y, (60, 140, 240) if t.rng.random() < .5 else (40, 40, 40))
        t.rect((70, 72, 76), 0, 0, 15, 15)
    return f


def drawers(c, n=3, handle='lgrey'):
    def f(t):
        _base(t, c, 0.03)
        step = 16 // n
        for i in range(n):
            y0 = i * step
            t.rect(shade(c, 0.7), 1, y0 + 1, 14, y0 + step - 1)
            t.hline(y0 + step // 2, handle, 6, 9)
    return f


def locker(c):
    def f(t):
        _base(t, c, 0.03)
        t.rect(shade(c, 0.7), 1, 0, 14, 15)
        for y in (2, 4, 6):
            t.hline(y, shade(c, 0.6), 4, 11)
        t.fill('lgrey', 11, 8, 13, 11)
    return f


def buttons(c='dgrey', lights=True):
    def f(t):
        _base(t, c, 0.04)
        t.bevel()
        for y in range(3, 13, 3):
            for x in range(2, 14, 3):
                t.set(x, y, t.rng.choice([(200, 40, 40), (40, 200, 60), (230, 200, 40), (180, 180, 180)]) if lights else 'lgrey')
    return f


def vending():
    def f(t):
        t.fill('red')
        t.fill((180, 210, 230), 1, 1, 11, 15)
        for y in range(2, 15, 3):
            for x in range(2, 11, 3):
                t.set(x, y, t.rng.choice([(230, 200, 40), (60, 120, 220), (200, 60, 60), (60, 180, 80)]))
                t.set(x + 1, y, shade(t.get(x, y), 0.8))
        t.fill('dgrey', 12, 6, 15, 10)
    return f


def crate(c='wood', band=None, stencil=True):
    def f(t):
        planks(c, False)(t)
        t.rect(shade(c, 0.6), 0, 0, 15, 15)
        for i in range(16):
            t.set(i, i, shade(c, 0.75))
        if band:
            t.hline(7, band)
            t.hline(8, band)
        if stencil:
            t.ctext('SCP', 6, (30, 30, 30))
    return f


def shipping(c):
    def f(t):
        corrugated(c)(t)
        t.hline(0, shade(c, 0.6))
        t.hline(15, shade(c, 0.6))
    return f


def pavers(c):
    def f(t):
        _base(t, c, 0.05)
        t.hline(0, shade(c, 0.7))
        t.hline(8, shade(c, 0.7))
        t.vline(0, shade(c, 0.7), 0, 7)
        t.vline(8, shade(c, 0.7), 8, 15)
    return f


def asphalt(line=None):
    def f(t):
        _base(t, (52, 52, 54), 0.12)
        t.speckle((90, 90, 90), 20)
        if line:
            t.fill(line, 7, 0, 9, 16)
    return f


def helipad():
    def f(t):
        asphalt()(t)
        t.fill('yellow', 3, 2, 5, 14)
        t.fill('yellow', 11, 2, 13, 14)
        t.fill('yellow', 5, 7, 11, 9)
    return f


def rubble():
    def f(t):
        _base(t, (120, 118, 114), 0.15)
        for _ in range(10):
            x, y = t.rng.randrange(16), t.rng.randrange(16)
            t.fill(shade((150, 148, 142), t.rng.uniform(.7, 1.2)), x, y, x + 2, y + 2)
        t.speckle((80, 70, 60), 12)
    return f


def overgrown(base):
    def f(t):
        base(t)
        for _ in range(45):
            x, y = t.rng.randrange(16), t.rng.randrange(16)
            t.set(x, y, shade((70, 120, 50), t.rng.uniform(.7, 1.2)))
        for x in (3, 11):
            for y in range(16):
                if t.rng.random() < .8:
                    t.set(x + (y // 4) % 2, y, (50, 100, 40))
    return f


def stained(base, c=(140, 120, 80), n=6):
    def f(t):
        base(t)
        for _ in range(n):
            cx, cy, r = t.rng.uniform(2, 14), t.rng.uniform(2, 14), t.rng.uniform(1.5, 4)
            for y in range(16):
                for x in range(16):
                    if (x - cx) ** 2 + (y - cy) ** 2 < r * r:
                        p = t.get(x, y)
                        t.set(x, y, tuple(int(a * 0.6 + b * 0.4) for a, b in zip(p[:3], c)))
    return f


def scorched(base):
    def f(t):
        base(t)
        for y in range(16):
            for x in range(16):
                d = ((x - 8) ** 2 + (y - 8) ** 2) ** .5
                p = t.get(x, y)
                k = max(0.15, min(1, d / 9 + t.rng.uniform(-.15, .15)))
                t.set(x, y, shade(p, k))
    return f


def with_overlay(base, over):
    def f(t):
        base(t)
        over(t)
    return f


def holes(t):
    for _ in range(7):
        x, y = t.rng.randrange(2, 14), t.rng.randrange(2, 14)
        t.set(x, y, (25, 25, 25))
        t.set(x + 1, y, (70, 70, 70))
        t.set(x, y + 1, (70, 70, 70))


def cracks(t, n=2):
    for _ in range(n):
        x, y = t.rng.randrange(3, 13), 0
        while y < 16:
            p = t.get(x % 16, y)
            t.set(x % 16, y, shade(p, 0.45))
            y += 1
            x += t.rng.choice((-1, 0, 1))


def lamp(c):
    def f(t):
        _base(t, c, 0.02)
        t.rect(shade(c, 0.85), 0, 0, 15, 15)
    return f


def vent(c='lgrey'):
    def f(t):
        _base(t, c, 0.03)
        for y in range(2, 14, 2):
            t.hline(y, shade(c, 0.45), 2, 13)
        t.border()
    return f


def drain(base):
    def f(t):
        base(t)
        t.fill((60, 62, 64), 4, 4, 12, 12)
        for y in range(5, 12, 2):
            t.hline(y, (25, 25, 25), 5, 10)
    return f


def seal(base):
    def f(t):
        base(t)
        t.circle(8, 8, 7, 'yellow', filled=False)
        t.circle(8, 8, 4, 'yellow', filled=False)
    return f


def tape_reels():
    def f(t):
        _base(t, (190, 186, 172), 0.03)
        t.circle(4.5, 5, 3, (40, 40, 40), filled=False)
        t.circle(11.5, 5, 3, (40, 40, 40), filled=False)
        t.fill((40, 40, 40), 2, 11, 14, 13)
        for x in range(3, 13, 2):
            t.set(x, 12, (230, 200, 60))
    return f


def board(c, ink=None, cork=False):
    def f(t):
        _base(t, c, 0.06 if cork else 0.01)
        t.rect((150, 150, 150) if not cork else (110, 80, 50), 0, 0, 15, 15)
        if cork:
            for _ in range(5):
                x, y = t.rng.randrange(1, 10), t.rng.randrange(1, 10)
                t.fill(t.rng.choice([(240, 240, 230), (240, 230, 140), (200, 230, 240)]), x, y, x + 5, y + 5)
                t.set(x + 2, y, (200, 40, 40))
        if ink:
            for y in (3, 6, 9, 12):
                t.hline(y, ink, 2, 2 + t.rng.randrange(5, 12))
    return f


def evac_map():
    def f(t):
        _base(t, (236, 232, 222), 0.01)
        t.rect((30, 30, 30), 0, 0, 15, 15)
        t.rect((60, 60, 60), 2, 2, 13, 13)
        t.vline(7, (60, 60, 60), 2, 13)
        t.hline(8, (60, 60, 60), 2, 13)
        for (x, y) in ((4, 5), (5, 5), (6, 5), (7, 6), (7, 7)):
            t.set(x, y, (40, 170, 70))
        t.set(10, 10, (200, 40, 40))
    return f


def poster(bg, fg, icon, words):
    return sign(bg, fg, [words], border=fg, icon=icon)


def clock():
    def f(t):
        t.clear()
        t.circle(8, 8, 7.5, (240, 240, 236))
        t.circle(8, 8, 7.5, (40, 40, 40), filled=False)
        t.vline(8, (20, 20, 20), 3, 8)
        t.hline(8, (20, 20, 20), 8, 11)
    return f


def jar(liquid, thing):
    def f(t):
        t.fill(liquid)
        t.noise(0.05)
        t.circle(8, 9, 3, thing)
        t.fill((150, 150, 150), 0, 0, 16, 2)
    return f


def organic(c):
    def f(t):
        _base(t, c, 0.12)
        t.speckle(shade(c, 0.6), 20)
        t.speckle(shade(c, 1.3), 10)
    return f


def wood_grain(c):
    return planks(c, False)
