# Block Image Dumper

A Fabric **client** mod for Minecraft 26.1 that exports the game's rendered icons straight to PNG files — no manual screenshotting, cropping, or transparency cleanup.

- **F7 — items.** Writes every registered item's inventory icon as a clean, transparent **64×64 PNG** (one file per item).
- **F8 — decorations.** Exports the overlays a flat icon can't capture: per-face **decorated-pot sherds** and tintable **banner & shield pattern masks**.

## Why would I want this?

Data packs and resource packs that preview container contents — like the [**shulker-preview**](https://github.com/Ethan-Arrowood/shulker-preview) data pack — need a pixel-accurate image of every item exactly as it appears in the inventory, including block-type items that have no flat sprite of their own. Producing ~1,600 of those by hand is impractical. This mod generates the entire set in seconds, with correct transparency and decoration overlays, ready to drop into a build pipeline.

It has no dependency on any particular pack, though — it just writes PNGs you can use for atlases, wikis, web tools, or anything else.

## How it works

Minecraft 26.1's screenshot path force-sets every pixel opaque, so transparency can't be read back directly. The mod works around this by rendering two icons side by side in a single frame and combining the captures:

- **Items** are drawn over **black** and over **white**; comparing the two reconstructs exact per-pixel alpha.
- **Pots** compare a plain pot against a one-sherd pot to isolate the sherd motif.
- **Banners & shields** render the pattern in **white dye over a black base**, so the brightness difference becomes a tintable white mask.

## Usage

1. Install this mod alongside [**Fabric API**](https://modrinth.com/mod/fabric-api).
2. Load any world, then press **F7** (items) or **F8** (decorations).
3. Find your PNGs in `<minecraft>/block-images/`.

Client-side only · MIT licensed · [Source & releases on GitHub](https://github.com/Ethan-Arrowood/block-image-dumper)
