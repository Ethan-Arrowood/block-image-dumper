# Contributing

Thanks for your interest in improving Block Image Dumper! This is a small,
single-purpose Fabric **client** mod, so the workflow is lightweight.

## Project layout

```
src/client/java/dev/ethanarrowood/blockdumper/
├── BlockImageDumperMod.java          # entrypoint + all dump logic (F7 / F8)
└── mixin/
    ├── GuiExtractMixin.java          # injects our draws into Gui.extractRenderState
    └── GameRendererMixin.java        # fires the screenshot after the frame is on the GPU
src/client/resources/block-image-dumper.mixins.json
src/main/resources/fabric.mod.json
assets/                              # Modrinth icon / gallery / description (+ generator)
```

### How the rendering works (read this before touching the capture code)

Minecraft 26.1's screenshot path force-sets every pixel's alpha to `255`, so
transparency can't be read back directly. Both dump modes draw **two icons side
by side in one frame** and combine the two 64×64 captures:

- **F7 items** — the same icon over **black** (x=0) and **white** (x=64).
  `observed = C·A + B·(1−A)`, so `A = 1 − (white − black)/255` and `C = black/A`
  gives exact per-pixel alpha.
- **F8 pots** — a plain all-brick pot vs. a pot with one sherd on a single face;
  the pixels that differ are the sherd motif (kept opaque, rest transparent).
- **F8 banners / shields** — the pattern in **white dye over a black base** vs.
  the plain black base; the brightness difference becomes a tintable white mask.

Two timing details matter and are easy to regress:

- The screenshot must be taken **after** the full frame reaches the GPU, which is
  why `GameRendererMixin` injects at the return of `Minecraft.renderFrame`, not
  at `GameRenderer.render`.
- `onGuiExtract` sets `itemRenderedThisFrame`; `onFrameRendered` only screenshots
  when that flag is set, because `executePendingTasks()` runs between extract and
  the frame return and would otherwise capture an un-drawn frame.

## Prerequisites

- JDK **25** (Minecraft 26.1 requires it). The Gradle build uses a Java
  [toolchain](https://docs.gradle.org/current/userguide/toolchains.html), so it
  will locate an installed JDK 25 or auto-provision one — you don't need to set
  `JAVA_HOME` to 25 as long as one is discoverable.
- A Fabric 26.1 Minecraft install for testing.

## Build & test locally

```sh
./gradlew build                       # Windows: gradlew.bat build
```

The JAR lands in `build/libs/block-image-dumper-<version>.jar`. To test it:

1. Copy that JAR — plus [Fabric API](https://modrinth.com/mod/fabric-api) — into
   your Fabric 26.1 `mods/` folder.
2. Launch, load any world, and press **F7** (items) or **F8** (decorations).
3. Check the output under `<minecraft>/block-images/`.

There are no automated tests; the mod is validated by running the dump and
inspecting the PNGs. When changing capture/diff logic, eyeball a few outputs
(an opaque item, a translucent one like glass, a pot sherd, a banner pattern).

## Pull requests

- Keep changes focused and the diff small; explain the "why" in the description.
- Match the existing style (4-space indent, descriptive comments on the
  non-obvious rendering math).
- If you change behavior, note how you verified it in-game.
- Contributions are accepted under the project's [MIT license](LICENSE).

## Cutting a release

1. Bump `mod_version` in `gradle.properties`.
2. Commit and push to `main`.
3. Create a GitHub Release with a new tag (e.g. `v1.1.0`) and notes.
   The `release.yml` workflow builds on JDK 25 and attaches the JAR to that
   release automatically — no manual build/upload needed.
4. On [Modrinth](https://modrinth.com/mod/block-image-dumper), create a new
   version, upload that JAR, and set Loader = Fabric, Game version = the target
   Minecraft version, with Fabric API as a required dependency.

The Modrinth icon/gallery can be regenerated from real renders with
`python3 assets/make_assets.py` (requires a local checkout of the
[shulker-preview](https://github.com/Ethan-Arrowood/shulker-preview) block
images it composites from).
