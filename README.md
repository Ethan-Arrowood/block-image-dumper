# Block Image Dumper

A Fabric **client** mod for Minecraft 26.2 that renders game icons to PNG files
on disk:

- **F7 — items.** Every registered item's inventory icon, as a transparent
  64×64 PNG (one file per item).
- **F8 — decorations.** Overlay images that a flat item icon can't express:
  per-face decorated-pot sherds, and tintable banner/shield pattern masks.

It was originally built to generate the block-render images for the
[shulker-preview](https://github.com/Ethan-Arrowood/shulker-preview) data pack,
but it has no dependency on that project — it just writes PNGs you can use for
anything (atlases, wikis, web tools, etc.).

## Download

- **[Modrinth](https://modrinth.com/mod/block-image-dumper)** (recommended)
- **[GitHub Releases](https://github.com/Ethan-Arrowood/block-image-dumper/releases)**

Requires [Fabric API](https://modrinth.com/mod/fabric-api). Client-side only.

## How it works

Minecraft 26.2's screenshot path force-sets every pixel's alpha to 255, so
transparency can't be read back directly. Both modes work around this by
rendering two icons side by side in a single frame and combining the two 64×64
captures:

- **Items** render the same icon over **black** and over **white**. For a pixel
  with straight colour `C` and alpha `A`, `observed = C·A + B·(1−A)`, so
  `A = 1 − (white − black)/255` and `C = black / A` — exact per-pixel alpha.
- **Pots** render a plain all-brick pot vs. a pot with one sherd on a single
  face; the pixels that differ are the sherd motif (kept opaque, everything
  else transparent).
- **Banners / shields** render the pattern in **white dye over a black base**
  vs. the plain black base; the brightness difference becomes a white,
  tintable mask (colour it per dye at use time).

## Prerequisites

| Requirement | Version |
|---|---|
| Java (to build) | 25 |
| Minecraft Java Edition | 26.2 |
| Fabric Loader | ≥ 0.19.2 |
| Fabric API | for 26.2 |

Minecraft 26.2 requires Java 25. The Gradle build uses a Java
[toolchain](https://docs.gradle.org/current/userguide/toolchains.html), so it
will locate an installed JDK 25 — or auto-provision one — regardless of the JDK
running Gradle.

## Build

```sh
./gradlew build        # Windows: gradlew.bat build
```

The mod JAR is written to `build/libs/block-image-dumper-1.1.0.jar`.

## Install

Drop the JAR — plus [Fabric API](https://modrinth.com/mod/fabric-api) — into
your Minecraft `mods/` folder for a Fabric 26.2 install:

| OS | Folder |
|---|---|
| macOS | `~/Library/Application Support/minecraft/mods/` |
| Windows | `%APPDATA%\.minecraft\mods\` |
| Linux | `~/.minecraft/mods/` |

## Usage

1. Launch Minecraft with the Fabric 26.2 profile and load any world (a world
   and player must be present).
2. Press **F7** to dump item icons, or **F8** to dump decoration overlays. Chat
   reports when the dump starts and finishes (`N saved, N failed`). The game may
   briefly freeze while it runs.

Output is written under the Minecraft game directory:

```
<minecraft>/block-images/
├── <item>.png                 # F7: one per registered item (64×64, RGBA)
├── pot/<pattern>.left.png     # F8: decorated-pot sherd, left  face
├── pot/<pattern>.right.png    # F8: decorated-pot sherd, right face
├── banner/<pattern>.png       # F8: banner pattern mask (white, tintable)
└── shield/<pattern>.png       # F8: shield pattern mask (white, tintable)
```

## License

[MIT](LICENSE)
