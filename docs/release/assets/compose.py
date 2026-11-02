"""Compose Play Store screenshot cards from raw phone captures.

usage: compose.py <out_dir> [W H]   (default 1080x1920 phone cards)
Each card: gradient background in the Sage palette, headline + subline,
the phone screen (status/nav bars cropped) with rounded corners and a
shadow, bleeding off the bottom edge.
"""
import sys, os
from PIL import Image, ImageDraw, ImageFilter, ImageFont

W_IN = os.path.join(os.path.dirname(os.path.abspath(__file__)), "raw")
OUT = sys.argv[1]
CW, CH = (int(sys.argv[2]), int(sys.argv[3])) if len(sys.argv) > 3 else (1080, 1920)
os.makedirs(OUT, exist_ok=True)

FONT_B = "C:/Windows/Fonts/segoeuib.ttf"
FONT_R = "C:/Windows/Fonts/segoeui.ttf"

# name, dark?, headline, subline
CARDS = [
    ("01", "today_dark", True, "Your day, at a glance", "See what is due, tick it off, move on."),
    ("02", "matrix_dark", True, "Know what matters first", "Urgent and important, sorted into four boxes."),
    ("03", "focus_dark", True, "Focus on one task", "Timed rounds with breaks, started from the task."),
    ("04", "reminder_light", False, "Reminders that really ring", "Alarm sound, vibration and a notification, on the minute."),
    ("05", "today_light", False, "Light or dark, your pick", "One accent colour shapes the whole app."),
    ("06", "editor_light", False, "Everything on one screen", "Date, time, reminder, repeat, list and tags."),
    ("07", "upcoming_light", False, "Plan the week ahead", "Tomorrow and beyond, day by day."),
    ("08", "appearance_dark", True, "Make it yours", "Ten calm accents and plain or scenic backgrounds."),
]

DARK_TOP, DARK_BOT = (9, 26, 20), (24, 58, 44)
LIGHT_TOP, LIGHT_BOT = (226, 242, 233), (250, 253, 251)


def gradient(w, h, top, bot):
    g = Image.new("RGB", (1, h))
    for y in range(h):
        t = y / (h - 1)
        g.putpixel((0, y), tuple(int(top[i] + (bot[i] - top[i]) * t) for i in range(3)))
    return g.resize((w, h))


def rounded(im, r):
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, im.width - 1, im.height - 1), r, fill=255)
    out = im.convert("RGBA")
    out.putalpha(mask)
    return out


def fit_text(draw, text, font_path, size, max_w):
    while size > 20:
        f = ImageFont.truetype(font_path, size)
        if draw.textlength(text, font=f) <= max_w:
            return f
        size -= 2
    return ImageFont.truetype(font_path, size)


def card(num, name, dark, head, sub):
    src = Image.open(f"{W_IN}/{name}.png").convert("RGB")
    # crop the status bar (top 100 px) and the system nav bar (below 2270)
    scr = src.crop((0, 100, src.width, 2270))
    scale = CW / 1080
    bg = gradient(CW, CH, *(DARK_TOP, DARK_BOT) if dark else (LIGHT_TOP, LIGHT_BOT))
    d = ImageDraw.Draw(bg)
    fg = (236, 246, 240) if dark else (18, 36, 28)
    fg2 = (160, 200, 180) if dark else (70, 100, 86)
    hf = fit_text(d, head, FONT_B, int(78 * scale), CW - int(120 * scale))
    sf = fit_text(d, sub, FONT_R, int(40 * scale), CW - int(120 * scale))
    y = int(140 * scale)
    hw = d.textlength(head, font=hf)
    d.text(((CW - hw) / 2, y), head, font=hf, fill=fg)
    y += int(hf.size * 1.25)
    sw = d.textlength(sub, font=sf)
    d.text(((CW - sw) / 2, y), sub, font=sf, fill=fg2)
    # phone screen
    pw = int(CW * 0.84)
    ph = int(scr.height * pw / scr.width)
    scr = scr.resize((pw, ph), Image.LANCZOS)
    r = int(56 * scale)
    screen = rounded(scr, r)
    px, py = (CW - pw) // 2, int(390 * scale)
    # shadow
    sh = Image.new("RGBA", (pw + 160, ph + 160), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle((80, 80, 80 + pw, 80 + ph), r, fill=(0, 0, 0, 150 if dark else 70))
    sh = sh.filter(ImageFilter.GaussianBlur(40))
    bg = bg.convert("RGBA")
    bg.alpha_composite(sh, (px - 80, py - 60))
    # thin border so the screen edge reads on both backgrounds
    border = Image.new("RGBA", screen.size, (0, 0, 0, 0))
    ImageDraw.Draw(border).rounded_rectangle((0, 0, pw - 1, ph - 1), r, outline=(255, 255, 255, 40) if dark else (0, 0, 0, 30), width=3)
    screen.alpha_composite(border)
    bg.alpha_composite(screen, (px, py))
    out = bg.convert("RGB")
    out.save(f"{OUT}/{num}_{name}.png", optimize=True)
    print(num, name, out.size)


for c in CARDS:
    if os.path.exists(f"{W_IN}/{c[1]}.png"):
        card(*c)
    else:
        print("skip (no capture yet):", c[1])
