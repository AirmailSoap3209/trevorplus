"""Generates the Trevor+ mod icon (assets/trevorplus/icon.png). Run: python3 tools/make_icon.py"""
from PIL import Image, ImageDraw, ImageFilter
S = 4                      # supersampling
N = 512 * S

def lerp(a, b, t): return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

# background: diagonal gradient, deep navy -> dark teal
bg = Image.new("RGB", (N, N))
px = bg.load()
c1, c2 = (14, 18, 30), (20, 74, 96)
for y in range(N):
    for x in range(N):
        px[x, y] = lerp(c1, c2, (x + y) / (2 * N))
mask = Image.new("L", (N, N), 0)
ImageDraw.Draw(mask).rounded_rectangle((0, 0, N - 1, N - 1), radius=int(N * 0.2), fill=255)
img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
img.paste(bg, (0, 0), mask)

d = ImageDraw.Draw(img)
# soft glow behind the paw
glow = Image.new("RGBA", (N, N), (0, 0, 0, 0))
gd = ImageDraw.Draw(glow)
gd.ellipse((N * 0.2, N * 0.22, N * 0.8, N * 0.82), fill=(85, 255, 255, 70))
glow = glow.filter(ImageFilter.GaussianBlur(N * 0.06))
img = Image.alpha_composite(img, glow)
d = ImageDraw.Draw(img)

AQUA = (85, 255, 255, 255)
def ell(cx, cy, rx, ry, fill, rot=0):
    layer = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    ImageDraw.Draw(layer).ellipse((cx - rx, cy - ry, cx + rx, cy + ry), fill=fill)
    if rot: layer = layer.rotate(rot, center=(cx, cy), resample=Image.BICUBIC)
    return layer

paw = Image.new("RGBA", (N, N), (0, 0, 0, 0))
# main pad (rounded triangle-ish blob)
paw = Image.alpha_composite(paw, ell(N * 0.47, N * 0.60, N * 0.19, N * 0.15, AQUA))
paw = Image.alpha_composite(paw, ell(N * 0.36, N * 0.66, N * 0.11, N * 0.10, AQUA))
paw = Image.alpha_composite(paw, ell(N * 0.58, N * 0.66, N * 0.11, N * 0.10, AQUA))
# toes
for cx, cy, rot in ((0.24, 0.45, 25), (0.38, 0.32, 8), (0.55, 0.31, -8), (0.69, 0.43, -25)):
    paw = Image.alpha_composite(paw, ell(N * cx, N * cy, N * 0.070, N * 0.095, AQUA, rot))
# drop shadow
sh = Image.new("RGBA", (N, N), (0, 0, 0, 0))
sh.paste((0, 0, 0, 120), (int(N * 0.012), int(N * 0.02)), paw.split()[3])
sh = sh.filter(ImageFilter.GaussianBlur(N * 0.012))
img = Image.alpha_composite(img, sh)
img = Image.alpha_composite(img, paw)

# gold "+" badge, lower right
d = ImageDraw.Draw(img)
cx, cy, r = N * 0.76, N * 0.76, N * 0.15
d.ellipse((cx - r - N * 0.012, cy - r - N * 0.012, cx + r + N * 0.012, cy + r + N * 0.012), fill=(14, 18, 30, 255))
d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(255, 170, 0, 255))
t, L = N * 0.032, r * 0.58
d.rounded_rectangle((cx - L, cy - t, cx + L, cy + t), radius=int(t), fill=(255, 255, 255, 255))
d.rounded_rectangle((cx - t, cy - L, cx + t, cy + L), radius=int(t), fill=(255, 255, 255, 255))

# thin border
d.rounded_rectangle((S * 2, S * 2, N - S * 3, N - S * 3), radius=int(N * 0.2), outline=(85, 255, 255, 90), width=S * 3)

out = img.resize((512, 512), Image.LANCZOS)
out.save("src/main/resources/assets/trevorplus/icon.png")
out.resize((128, 128), Image.LANCZOS).save("docs/icon-128.png")
print("ok")
