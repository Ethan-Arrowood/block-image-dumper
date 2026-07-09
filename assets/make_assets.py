#!/usr/bin/env python3
"""Generate the Modrinth icon and gallery image for block-image-dumper.

Uses real item renders (produced by this very mod) and the shulker-preview
block_sheet as source material. Re-runnable; writes icon.png + gallery.png here.
"""
import os
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
# Local checkout of https://github.com/Ethan-Arrowood/shulker-preview and the
# pack version folder inside it; override via env when your paths differ.
SP = os.path.expanduser(os.environ.get('SHULKER_PREVIEW_DIR', '~/dev/ethan-arrowood/shulker-preview'))
SP_VERSION = os.environ.get('SHULKER_PREVIEW_VERSION', '26.2')
BI = os.path.join(SP, 'block images')
SHEET = os.path.join(
    SP, SP_VERSION, 'resourcepack',
    'assets/tryashtar.shulker_preview/textures/block_sheet.png')
FONT_BLACK = '/System/Library/Fonts/Supplemental/Arial Black.ttf'
FONT_BOLD = '/System/Library/Fonts/Supplemental/Arial Bold.ttf'


def vgradient(size, top, bottom):
    w, h = size
    base = Image.new('RGB', size)
    px = base.load()
    for y in range(h):
        t = y / (h - 1)
        px[0, y] = tuple(round(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
    return base.resize(size)  # smear the single column across the width


def load_item(name, target):
    img = Image.open(os.path.join(BI, f'{name}.png')).convert('RGBA')
    # crisp integer-ish upscale of 64px pixel-art
    return img.resize((target, target), Image.NEAREST)


def rounded_slot(size, radius=18):
    """A vanilla-ish recessed inventory slot tile."""
    s = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(s)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=(139, 139, 139, 255))
    # inset bevel: dark top/left, light bottom/right
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, outline=(55, 55, 55, 255), width=4)
    d.line([(6, size - 5), (size - 6, size - 5)], fill=(235, 235, 235, 120), width=3)
    d.line([(size - 5, 6), (size - 5, size - 6)], fill=(235, 235, 235, 120), width=3)
    return s


def make_icon():
    S = 512
    icon = vgradient((S, S), (38, 42, 56), (18, 20, 28)).convert('RGBA')
    # subtle vignette
    vig = Image.new('L', (S, S), 0)
    ImageDraw.Draw(vig).ellipse([-S // 3, -S // 3, S + S // 3, S + S // 3], fill=90)
    icon = Image.composite(icon, Image.new('RGBA', (S, S), (8, 9, 13, 255)), vig)

    slot = 188
    gap = 18
    grid = slot * 2 + gap
    ox = (S - grid) // 2
    oy = (S - grid) // 2
    items = ['diamond', 'gold_ingot', 'amethyst_cluster', 'oak_log']
    tile = rounded_slot(slot)
    item_px = 128
    for i, name in enumerate(items):
        cx = ox + (i % 2) * (slot + gap)
        cy = oy + (i // 2) * (slot + gap)
        icon.alpha_composite(tile, (cx, cy))
        it = load_item(name, item_px)
        icon.alpha_composite(it, (cx + (slot - item_px) // 2, cy + (slot - item_px) // 2))

    icon.convert('RGB').save(os.path.join(HERE, 'icon.png'))
    print('wrote icon.png 512x512')


def make_gallery():
    W, H = 1920, 1080
    base = Image.new('RGBA', (W, H), (18, 20, 28, 255))
    sheet = Image.open(SHEET).convert('RGBA')
    # scale sheet to cover width, center-crop to H
    scale = W / sheet.width
    sw, sh = W, round(sheet.height * scale)
    sheet = sheet.resize((sw, sh), Image.LANCZOS)
    base.alpha_composite(sheet, (0, (H - sh) // 2))

    # darken for text contrast: moderate up top, ramping hard over the lower
    # third where the title sits (power curve keeps the artwork visible above).
    overlay = Image.new('L', (W, H), 0)
    op = overlay.load()
    for y in range(H):
        op[0, y] = min(255, int(95 + 170 * (y / (H - 1)) ** 1.9))
    overlay = overlay.resize((W, H))
    base = Image.composite(Image.new('RGBA', (W, H), (8, 9, 13, 255)), base, overlay)

    d = ImageDraw.Draw(base)
    title = ImageFont.truetype(FONT_BLACK, 132)
    sub = ImageFont.truetype(FONT_BOLD, 50)
    tag = ImageFont.truetype(FONT_BOLD, 38)
    d.text((110, H - 360), 'Block Image Dumper', font=title, fill=(255, 255, 255))
    d.text((116, H - 196),
            'Export every Minecraft item icon to a transparent PNG',
            font=sub, fill=(206, 214, 226))
    d.text((116, H - 126),
            'F7  items     ·     F8  decorated-pot, banner & shield overlays',
            font=tag, fill=(122, 198, 255))
    # accent bar
    d.rectangle([110, H - 250, 110 + 92, H - 250 + 8], fill=(122, 198, 255))

    base.convert('RGB').save(os.path.join(HERE, 'gallery.png'))
    print('wrote gallery.png 1920x1080')


if __name__ == '__main__':
    make_icon()
    make_gallery()
