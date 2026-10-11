"""Builds "Gridline", the Tron theme's display face: an original typeface in the spirit of Tron: Legacy's
lettering (wide monoline capitals, rounded corners, light-line cuts), drawn here from strokes so the app owns it
(MIT, like the rest of the code). Needs fontTools + shapely.

    python tools/grid_font.py app/src/main/res/font/theme_grid.ttf
"""
import math, sys
from fontTools.fontBuilder import FontBuilder
from fontTools.pens.ttGlyphPen import TTGlyphPen
from shapely.geometry import LineString, box
from shapely.geometry.polygon import orient
from shapely.ops import unary_union

H, S, RAD, GAP = 700, 62, 150, 52       # cap height, stroke, corner radius, light-cut width
c = S / 2


def fillet(pts, r, closed=False):
    """Polyline with every corner rounded to radius r (clamped to half the shorter neighbour)."""
    if closed: pts = pts + pts[:2]
    out = [pts[0]] if not closed else []
    for i in range(1, len(pts) - 1):
        (x0, y0), (x1, y1), (x2, y2) = pts[i - 1], pts[i], pts[i + 1]
        a = math.hypot(x0 - x1, y0 - y1); b = math.hypot(x2 - x1, y2 - y1)
        rr = min(r, a / 2, b / 2)
        if rr < 1: out.append((x1, y1)); continue
        u = ((x0 - x1) / a, (y0 - y1) / a); v = ((x2 - x1) / b, (y2 - y1) / b)
        p, q = (x1 + u[0] * rr, y1 + u[1] * rr), (x1 + v[0] * rr, y1 + v[1] * rr)
        for k in range(9):                  # quadratic corner through the vertex, sampled
            t = k / 8
            out.append(((1 - t) ** 2 * p[0] + 2 * t * (1 - t) * x1 + t * t * q[0],
                        (1 - t) ** 2 * p[1] + 2 * t * (1 - t) * y1 + t * t * q[1]))
    if not closed: out.append(pts[-1])
    else: out.append(out[0])
    return out


def glyph(W, strokes, cuts=(), r=RAD):
    """strokes: lists of (x, y) centre points, or ('c', [...]) for a closed loop, or ('s', [...]) sharp."""
    shapes = []
    for st in strokes:
        mode, pts = (st if isinstance(st[0], str) else ('o', st))
        line = pts if mode == 's' else fillet(pts, r, closed=(mode == 'c'))
        shapes.append(LineString(line).buffer(c, cap_style='flat', join_style='mitre', mitre_limit=3))
    g = unary_union(shapes)
    for (x0, y0, x1, y1) in cuts: g = g.difference(box(x0, y0, x1, y1))
    return W, g


def build():
    G = {}
    W = 640; L, R, B, T, M, X = c, W - c, c, H - c, H / 2, W / 2
    top_cut = (150, H - S - 2, 150 + GAP, H + 2)        # the slot in the top edge of round letters
    G['A'] = glyph(W, [[(L, B), (L, T), (R, T), (R, B)], ('s', [(L, M), (R, M)])])
    G['B'] = glyph(W, [('s', [(L, B), (L, T)]), [(L, T), (R - 40, T), (R - 40, M), (L, M)], [(L, M), (R, M), (R, B), (L, B)]], r=110)
    G['C'] = glyph(W, [[(R, T), (L, T), (L, B), (R, B)]], [top_cut])
    G['D'] = glyph(W, [('s', [(L, B), (L, T)]), [(L, T), (R, T), (R, B), (L, B)]], [top_cut])
    G['E'] = glyph(W, [[(R, T), (L, T), (L, B), (R, B)], ('s', [(L + S + GAP, M), (R - 70, M)])], r=90)
    G['F'] = glyph(W, [[(R, T), (L, T), (L, B)], ('s', [(L + S + GAP, M), (R - 70, M)])], r=90)
    G['G'] = glyph(W, [[(R, T), (L, T), (L, B), (R, B), (R, M), (X, M)]], [top_cut])
    G['H'] = glyph(W, [('s', [(L, B), (L, T)]), ('s', [(R, B), (R, T)]), ('s', [(L, M), (R, M)])])
    G['I'] = glyph(S, [('s', [(c, B), (c, T)])])
    G['J'] = glyph(W, [[(R, T), (R, B), (L, B), (L, B + 210)]])
    G['K'] = glyph(W, [('s', [(L, B), (L, T)]), ('s', [(L + S + GAP, M), (R, T)]), ('s', [(L + S + GAP, M), (R, B)])])
    G['L'] = glyph(W, [[(L, T), (L, B), (R, B)]], r=90)
    WM = 780; RM = WM - c
    G['M'] = glyph(WM, [[(L, B), (L, T), (RM, T), (RM, B)], ('s', [(WM / 2, T - S - GAP), (WM / 2, B + 120)])])
    G['N'] = glyph(W, [[(L, B), (L, T), (R, T), (R, B)]])
    G['O'] = glyph(W, [('c', [(L, B), (L, T), (R, T), (R, B)])], [top_cut])
    G['P'] = glyph(W, [('s', [(L, B), (L, T)]), [(L, T), (R, T), (R, M), (L, M)]], r=120)
    G['Q'] = glyph(W, [('c', [(L, B), (L, T), (R, T), (R, B)]), ('s', [(R - 130, B + 110), (R + 40, B - 50)])], [top_cut, (R - 220, -40, R - 220 + GAP, S + 2)])
    G['R'] = glyph(W, [('s', [(L, B), (L, T)]), [(L, T), (R, T), (R, M), (L, M)], ('s', [(R, M - S / 2 - GAP), (R, B)])], r=120)
    G['S'] = glyph(W, [[(R, T), (L, T), (L, M), (R, M), (R, B), (L, B)]], r=110)
    G['T'] = glyph(W, [('s', [(L - c, T), (R + c, T)]), ('s', [(X, T - S / 2 - GAP), (X, B)])])
    G['U'] = glyph(W, [[(L, T), (L, B), (R, B), (R, T)]])
    G['V'] = glyph(W, [('s', [(L, T), (X, B), (R, T)])])
    G['W'] = glyph(WM, [[(L, T), (L, B), (RM, B), (RM, T)], ('s', [(WM / 2, B + S + GAP), (WM / 2, T - 120)])])
    G['X'] = glyph(W, [('s', [(L, T), (R, B)]), ('s', [(L, B), (R, T)])])
    G['Y'] = glyph(W, [[(L, T), (L, M), (R, M), (R, T)], ('s', [(X, M - S / 2 - GAP), (X, B)])], r=120)
    G['Z'] = glyph(W, [('s', [(L, T), (R, T)]), ('s', [(R - 20, T - c), (L + 20, B + c)]), ('s', [(L, B), (R, B)])])
    WD = 560; RD = WD - c; XD = WD / 2
    G['0'] = glyph(WD, [('c', [(L, B), (L, T), (RD, T), (RD, B)]), ('s', [(XD, M - 90), (XD, M + 90)])])
    G['1'] = glyph(260, [('s', [(30, T), (260 - c, T), (260 - c, B)])])
    G['2'] = glyph(WD, [[(L, T), (RD, T), (RD, M), (L, M), (L, B), (RD, B)]], r=110)
    G['3'] = glyph(WD, [[(L, T), (RD, T), (RD, B), (L, B)], ('s', [(L + 90, M), (RD - S / 2 - GAP, M)])], r=110)
    G['4'] = glyph(WD, [[(L, T), (L, M), (RD, M)], ('s', [(RD, T), (RD, B)])], r=90)
    G['5'] = glyph(WD, [[(RD, T), (L, T), (L, M), (RD, M), (RD, B), (L, B)]], r=110)
    G['6'] = glyph(WD, [[(RD, T), (L, T), (L, B), (RD, B), (RD, M), (L, M)]], r=110)
    G['7'] = glyph(WD, [[(L, T), (RD, T), (RD, B)]], r=110)
    G['8'] = glyph(WD, [('c', [(L, B), (L, T), (RD, T), (RD, B)]), ('s', [(L, M), (RD, M)])], r=110)
    G['9'] = glyph(WD, [[(RD, M), (L, M), (L, T), (RD, T), (RD, B), (L, B)]], r=110)
    G['.'] = glyph(S, [('s', [(c, 0), (c, S)])])
    G[','] = glyph(S, [('s', [(c, S), (c, -90)])])
    G[':'] = glyph(S, [('s', [(c, 0), (c, S)]), ('s', [(c, M - 20), (c, M - 20 + S)])])
    G['-'] = glyph(360, [('s', [(0, M), (360, M)])])
    G['+'] = glyph(420, [('s', [(0, M), (420, M)]), ('s', [(210, M - 210), (210, M + 210)])])
    G['/'] = glyph(420, [('s', [(c, B - 40), (420 - c, T + 40)])])
    G["'"] = glyph(S, [('s', [(c, T + c), (c, T - 200)])])
    G['!'] = glyph(S, [('s', [(c, 0), (c, S)]), ('s', [(c, S + 80), (c, H)])])
    G['?'] = glyph(WD, [[(L, T), (RD, T), (RD, M), (XD, M), (XD, S + 80)], ('s', [(XD, 0), (XD, S)])], r=110)
    G['&'] = glyph(W, [[(R, B + 220), (R, B), (L, B), (L, M), (R - 110, M), (R - 110, T), (L + 60, T), (L + 60, M + 40)]], r=100)
    G['%'] = glyph(W, [('c', [(L, T - 200), (L, T), (L + 180, T), (L + 180, T - 200)]), ('s', [(R, T), (L, B)]),
                       ('c', [(R - 180, B), (R - 180, B + 200), (R, B + 200), (R, B)])], r=60)
    G['('] = glyph(220, [[(220 - c, T + 40), (c, T + 40), (c, B - 40), (220 - c, B - 40)]], r=150)
    G[')'] = glyph(220, [[(c, T + 40), (220 - c, T + 40), (220 - c, B - 40), (c, B - 40)]], r=150)
    G['|'] = glyph(S, [('s', [(c, -100), (c, H + 100)])])
    return G


def main(out):
    SB = 64                                    # side bearing, each side
    G = build()
    order = ['.notdef', 'space'] + [f'g{ord(k)}' for k in G]
    fb = FontBuilder(1000, isTTF=True)
    fb.setupGlyphOrder(order)
    cmap = {32: 'space', 0xA0: 'space'}
    for k in G:
        cmap[ord(k)] = f'g{ord(k)}'
        if k.isalpha(): cmap[ord(k.lower())] = f'g{ord(k)}'      # lower case reads as capitals
    fb.setupCharacterMap(cmap)
    glyphs, adv = {'.notdef': TTGlyphPen(None).glyph(), 'space': TTGlyphPen(None).glyph()}, {'.notdef': 500, 'space': 360}
    for k, (w, g) in G.items():
        pen = TTGlyphPen(None)
        for p in ([g] if g.geom_type == 'Polygon' else list(g.geoms)):
            p = orient(p, sign=-1.0)           # TrueType: outer contours clockwise, holes counter-clockwise
            for ring in [p.exterior] + list(p.interiors):
                pts = [(round(x + SB), round(y)) for x, y in ring.coords[:-1]]
                pts = [q for i, q in enumerate(pts) if q != pts[i - 1]]
                pen.moveTo(pts[0])
                for q in pts[1:]: pen.lineTo(q)
                pen.closePath()
        glyphs[f'g{ord(k)}'] = pen.glyph(); adv[f'g{ord(k)}'] = round(w + 2 * SB)
    fb.setupGlyf(glyphs)
    glyf = fb.font['glyf']
    fb.setupHorizontalMetrics({n: (adv[n], getattr(glyf[n], 'xMin', 0)) for n in order})
    fb.setupHorizontalHeader(ascent=900, descent=-250)
    fb.setupNameTable({'familyName': 'Gridline', 'styleName': 'Regular',
                       'copyright': 'Angra Streaming contributors, MIT License'})
    fb.setupOS2(sTypoAscender=900, sTypoDescender=-250, usWinAscent=900, usWinDescent=250, sCapHeight=H, sxHeight=H)
    fb.setupPost()
    fb.save(out)


if __name__ == '__main__':
    main(sys.argv[1])
