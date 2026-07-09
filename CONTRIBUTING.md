# Contributing

Thanks for your interest in improving Block Image Dumper! This is a small,
single-purpose Fabric **client** mod, so the workflow is lightweight.

## Project layout

```
src/client/java/dev/ethanarrowood/blockdumper/
├── BlockImageDumperMod.java          # entrypoint + all dump logic (F7 / F8)
└── mixin/
    ├── HudExtractMixin.java          # injects our draws into Hud.extractRenderState
    └── GameRendererMixin.java        # fires the screenshot after the frame is on the GPU
src/client/resources/block-image-dumper.mixins.json
src/main/resources/fabric.mod.json
assets/                              # Modrinth icon / gallery / description (+ generator)
```

### How the rendering works (read this before touching the capture code)

Minecraft 26.2's screenshot path force-sets every pixel's alpha to `255`, so
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

- JDK **25** (Minecraft 26.2 requires it). The Gradle build uses a Java
  [toolchain](https://docs.gradle.org/current/userguide/toolchains.html), so it
  will locate an installed JDK 25 or auto-provision one — you don't need to set
  `JAVA_HOME` to 25 as long as one is discoverable.
- A Fabric 26.2 Minecraft install for testing.

## Build & test locally

```sh
./gradlew build                       # Windows: gradlew.bat build
```

The JAR lands in `build/libs/block-image-dumper-<version>.jar`. To test it:

1. Copy that JAR — plus [Fabric API](https://modrinth.com/mod/fabric-api) — into
   your Fabric 26.2 `mods/` folder.
2. Launch, load any world, and press **F7** (items) or **F8** (decorations).
3. Check the output under `<minecraft>/block-images/`.

There are no automated tests; the mod is validated by running the dump and
inspecting the PNGs. When changing capture/diff logic, eyeball a few outputs
(an opaque item, a translucent one like glass, a pot sherd, a banner pattern).

## Porting to a new Minecraft version

The mod is small, so a version port is usually just dependency bumps — but do
them all, and verify the mixin targets before assuming the port works:

1. **Bump `gradle.properties`:**
   - `minecraft_version` — the new game version.
   - `fabric_api_version` — find the build for that game version on
     [Modrinth](https://modrinth.com/mod/fabric-api/versions) or via
     `curl 'https://api.modrinth.com/v2/project/fabric-api/version?game_versions=%5B%22<mc>%22%5D'`.
   - `loader_version` — latest from `https://meta.fabricmc.net/v2/versions/loader`.
   - `loom_version` — only if the build breaks; latest is in
     `https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml`.
   - `mod_version` — bump it.
2. **Update `fabric.mod.json`:** the `"minecraft": "~<version>"` constraint and
   the version named in the description.
3. **Update version references** in `README.md`, this file,
   `assets/modrinth-description.md`, and code comments.
4. **Verify the mixin targets still exist.** The game jar ships unobfuscated,
   so you can check without launching: extract
   `net/minecraft/client/gui/Hud.class` and `net/minecraft/client/Minecraft.class`
   from `<minecraft>/versions/<v>/<v>.jar` and run `javap -p` on them (use a
   `javap` new enough for the game's class-file version — Gradle's provisioned
   JDK under `~/.gradle/jdks/` works). Confirm the exact **declared** methods
   `extractRenderState(GuiGraphicsExtractor, DeltaTracker)` and
   `renderFrame(boolean)`. Don't trust a raw-bytes/`strings` search for the
   name and descriptor separately: the constant pool can contain a matching
   descriptor that belongs to a *different* method (this is exactly how the
   26.1→26.2 move of extractRenderState from Gui to Hud slipped past a
   strings-level check and only surfaced as a mixin crash at launch). If a
   target moved or changed, update `@Mixin(...)` / `@Inject(method = ...)` in
   `src/client/java/.../mixin/`.
5. **Runtime-verify.** Signatures existing isn't proof they're still *called*
   (e.g. 26.2 added an `extractRenderStateWithTooltipAndSubtitles` sibling).
   Launch, press F7 and F8, and eyeball the outputs per the testing notes
   above — especially a translucent item, since the whole dual-render alpha
   trick depends on the screenshot path's behavior.

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
