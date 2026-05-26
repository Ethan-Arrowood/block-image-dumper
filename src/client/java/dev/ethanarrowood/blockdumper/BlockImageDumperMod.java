package dev.ethanarrowood.blockdumper;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.PotDecorations;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Dumps item / decoration renders to PNGs for the shulker-preview pipeline.
 *
 *   F7 — every registered item's inventory icon, as a transparent 64x64 PNG.
 *   F8 — decoration overlays the item dump can't express: per-face decorated-pot
 *        sherds (block-images/pot/<p>.left|right.png) and tintable banner/shield
 *        pattern masks (block-images/banner|shield/<p>.png).
 *
 * Both modes share one mechanism: render two icons side by side in a single
 * frame and combine the two 64x64 captures.
 *   - Items: same icon over black (x=0) and white (x=64); reconstruct true alpha
 *     (the 26.1 screenshot path force-sets alpha=255, so we can't read it back).
 *   - Decorations: a "base" icon (x=0) and a "decorated" icon (x=64) that differ
 *     only by the decoration; the per-pixel difference isolates it.
 *       pots  -> opaque diff: output the changed (sherd) pixels, rest transparent
 *       banner/shield -> the pattern is rendered in WHITE dye over a black base,
 *         so the brightness difference is a white tintable mask the data pack
 *         colours per dye at render time.
 */
public class BlockImageDumperMod implements ClientModInitializer {

    // --- Shared state (all accessed only on the main/render thread) ---
    static boolean dumping = false;
    static boolean waitingForScreenshot = false;
    static boolean itemRenderedThisFrame = false;
    static boolean decorationMode = false;
    static int currentIndex = 0;
    static final List<Item> items = new ArrayList<>();
    static final List<DecorationJob> jobs = new ArrayList<>();
    static Path outputDir;
    static int originalGuiScale;
    static int successCount = 0;
    static int failedCount = 0;

    private static KeyMapping dumpKey;       // F7 — items
    private static KeyMapping decorationKey; // F8 — decorations

    /** A base vs decorated render pair, diffed to isolate one decoration. */
    static final class DecorationJob {
        final ItemStack base;
        final ItemStack decorated;
        final Path outPath;
        /** true = white tintable mask (banner/shield); false = opaque diff (pot). */
        final boolean maskMode;

        DecorationJob(ItemStack base, ItemStack decorated, Path outPath, boolean maskMode) {
            this.base = base;
            this.decorated = decorated;
            this.outPath = outPath;
            this.maskMode = maskMode;
        }
    }

    @Override
    public void onInitializeClient() {
        dumpKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.block-image-dumper.dump", GLFW.GLFW_KEY_F7, KeyMapping.Category.MISC));
        decorationKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.block-image-dumper.dump_decorations", GLFW.GLFW_KEY_F8, KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (dumpKey.consumeClick()) {
                if (!dumping && client.level != null && client.player != null) startItemDump(client);
            }
            while (decorationKey.consumeClick()) {
                if (!dumping && client.level != null && client.player != null) startDecorationDump(client);
            }
        });
    }

    // ------------------------------------------------------------------ //
    //  Item dump (F7)                                                      //
    // ------------------------------------------------------------------ //

    private static void startItemDump(Minecraft client) {
        outputDir = client.gameDirectory.toPath().resolve("block-images");
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            msg(client, "[dumper] ERROR creating output dir: " + e.getMessage());
            return;
        }

        items.clear();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item != Items.AIR) items.add(item);
        }
        if (items.isEmpty()) {
            msg(client, "[dumper] No items found!");
            return;
        }

        decorationMode = false;
        beginDump(client, items.size(), "items");
    }

    // ------------------------------------------------------------------ //
    //  Decoration dump (F8)                                                //
    // ------------------------------------------------------------------ //

    private static void startDecorationDump(Minecraft client) {
        outputDir = client.gameDirectory.toPath().resolve("block-images");
        try {
            Files.createDirectories(outputDir.resolve("pot"));
            Files.createDirectories(outputDir.resolve("banner"));
            Files.createDirectories(outputDir.resolve("shield"));
        } catch (IOException e) {
            msg(client, "[dumper] ERROR creating output dirs: " + e.getMessage());
            return;
        }

        jobs.clear();

        // Pots: each sherd on the two visible icon faces.  pot_decorations is
        // [back, left, right, front]; the data pack reads index 1 ("left") and
        // index 3 ("right"), the two faces visible in the 30/45 gui transform.
        ItemStack plainPot = potStack(Items.BRICK, Items.BRICK, Items.BRICK, Items.BRICK);
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null || !id.getPath().endsWith("_pottery_sherd")) continue;
            String pattern = id.getPath().substring(0, id.getPath().length() - "_pottery_sherd".length());
            jobs.add(new DecorationJob(plainPot,
                potStack(Items.BRICK, item, Items.BRICK, Items.BRICK),
                outputDir.resolve("pot").resolve(pattern + ".left.png"), false));
            jobs.add(new DecorationJob(plainPot,
                potStack(Items.BRICK, Items.BRICK, Items.BRICK, item),
                outputDir.resolve("pot").resolve(pattern + ".right.png"), false));
        }

        // Banners + shields: each pattern rendered in WHITE dye over a black base
        // so the brightness diff is a tintable white mask.
        Registry<BannerPattern> patterns = client.level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
        ItemStack plainBanner = new ItemStack(Items.BLACK_BANNER);
        ItemStack plainShield = shieldStack(null);
        for (ResourceKey<BannerPattern> key : patterns.registryKeySet()) {
            String name = key.identifier().getPath();
            Holder<BannerPattern> holder = patterns.wrapAsHolder(patterns.getValue(key));
            BannerPatternLayers layers = new BannerPatternLayers(
                List.of(new BannerPatternLayers.Layer(holder, DyeColor.WHITE)));

            ItemStack banner = new ItemStack(Items.BLACK_BANNER);
            banner.set(DataComponents.BANNER_PATTERNS, layers);
            jobs.add(new DecorationJob(plainBanner, banner,
                outputDir.resolve("banner").resolve(name + ".png"), true));

            jobs.add(new DecorationJob(plainShield, shieldStack(layers),
                outputDir.resolve("shield").resolve(name + ".png"), true));
        }

        if (jobs.isEmpty()) {
            msg(client, "[dumper] No decorations found!");
            return;
        }

        decorationMode = true;
        beginDump(client, jobs.size(), "decorations");
    }

    private static ItemStack potStack(Item back, Item left, Item right, Item front) {
        ItemStack pot = new ItemStack(Items.DECORATED_POT);
        pot.set(DataComponents.POT_DECORATIONS, new PotDecorations(back, left, right, front));
        return pot;
    }

    private static ItemStack shieldStack(BannerPatternLayers layers) {
        ItemStack shield = new ItemStack(Items.SHIELD);
        shield.set(DataComponents.BASE_COLOR, DyeColor.BLACK);
        if (layers != null) shield.set(DataComponents.BANNER_PATTERNS, layers);
        return shield;
    }

    // ------------------------------------------------------------------ //
    //  Shared lifecycle                                                    //
    // ------------------------------------------------------------------ //

    private static void beginDump(Minecraft client, int total, String what) {
        currentIndex = 0;
        successCount = 0;
        failedCount = 0;
        waitingForScreenshot = false;
        itemRenderedThisFrame = false;

        // Force GUI scale 4 so a 16x16 GUI-pixel icon -> exactly 64x64 screen px.
        originalGuiScale = client.options.guiScale().get();
        client.options.guiScale().set(4);
        client.resizeGui();

        dumping = true;
        msg(client, "[dumper] Dumping " + total + " " + what + " to: " + outputDir);
    }

    static void finishDump() {
        dumping = false;
        waitingForScreenshot = false;
        decorationMode = false;

        Minecraft client = Minecraft.getInstance();
        client.options.guiScale().set(originalGuiScale);
        client.resizeGui();

        msg(client, String.format("[dumper] Done: %d saved, %d failed. Output: %s",
            successCount, failedCount, outputDir));
    }

    // ------------------------------------------------------------------ //
    //  Called from GameRendererMixin after each fully-rendered frame.     //
    // ------------------------------------------------------------------ //

    public static void onFrameRendered() {
        if (!dumping || waitingForScreenshot) return;

        // Only screenshot if we actually drew this frame's icons during extract.
        if (!itemRenderedThisFrame) return;
        itemRenderedThisFrame = false;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            finishDump();
            return;
        }

        int total = decorationMode ? jobs.size() : items.size();
        if (currentIndex >= total) {
            finishDump();
            return;
        }

        waitingForScreenshot = true;
        if (decorationMode) captureDecoration(client);
        else captureItem(client);
    }

    private static void captureItem(Minecraft client) {
        Item item = items.get(currentIndex);
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null) {
            currentIndex++;
            waitingForScreenshot = false;
            failedCount++;
            return;
        }
        Path outPath = outputDir.resolve(id.getPath() + ".png");

        Screenshot.takeScreenshot(client.getMainRenderTarget(), fullImage -> {
            try {
                // The same item over BLACK at screen x=[0,64) and WHITE at
                // x=[64,128).  observed = C*A + B*(1-A); solving the two:
                //   A = 1 - (obsWhite - obsBlack)/255 ;  C = obsBlack / A.
                NativeImage out = new NativeImage(NativeImage.Format.RGBA, 64, 64, false);
                for (int y = 0; y < 64; y++) {
                    for (int x = 0; x < 64; x++) {
                        int blackPx = fullImage.getPixel(x, y);
                        int whitePx = fullImage.getPixel(x + 64, y);

                        int c0b = blackPx & 0xFF, c1b = (blackPx >> 8) & 0xFF, c2b = (blackPx >> 16) & 0xFF;
                        int c0w = whitePx & 0xFF, c1w = (whitePx >> 8) & 0xFF, c2w = (whitePx >> 16) & 0xFF;

                        double diff = ((c0w - c0b) + (c1w - c1b) + (c2w - c2b)) / 3.0;
                        if (diff < 0) diff = 0;
                        if (diff > 255) diff = 255;
                        double alpha = 1.0 - diff / 255.0;

                        int outPx;
                        if (alpha <= 0.004) {
                            outPx = 0;
                        } else {
                            int oc0 = clamp255((int) Math.round(c0b / alpha));
                            int oc1 = clamp255((int) Math.round(c1b / alpha));
                            int oc2 = clamp255((int) Math.round(c2b / alpha));
                            int oa = clamp255((int) Math.round(alpha * 255.0));
                            outPx = (oa << 24) | (oc2 << 16) | (oc1 << 8) | oc0;
                        }
                        out.setPixel(x, y, outPx);
                    }
                }
                out.writeToFile(outPath);
                out.close();
                successCount++;
            } catch (Exception e) {
                System.err.println("[dumper] Failed to save " + id + ": " + e.getMessage());
                failedCount++;
            } finally {
                fullImage.close();
                currentIndex++;
                waitingForScreenshot = false;
            }
        });
    }

    private static void captureDecoration(Minecraft client) {
        DecorationJob job = jobs.get(currentIndex);
        Path outPath = job.outPath;
        boolean maskMode = job.maskMode;

        Screenshot.takeScreenshot(client.getMainRenderTarget(), fullImage -> {
            try {
                // Base icon at screen x=[0,64), decorated at x=[64,128).
                NativeImage out = new NativeImage(NativeImage.Format.RGBA, 64, 64, false);
                for (int y = 0; y < 64; y++) {
                    for (int x = 0; x < 64; x++) {
                        int bPx = fullImage.getPixel(x, y);
                        int dPx = fullImage.getPixel(x + 64, y);

                        int rb = bPx & 0xFF, gb = (bPx >> 8) & 0xFF, bb = (bPx >> 16) & 0xFF;
                        int rd = dPx & 0xFF, gd = (dPx >> 8) & 0xFF, bd = (dPx >> 16) & 0xFF;

                        int outPx;
                        if (maskMode) {
                            // White tintable mask: alpha = how much brighter the
                            // white-dye pattern made this pixel vs the black base.
                            double diff = ((rd - rb) + (gd - gb) + (bd - bb)) / 3.0;
                            if (diff <= 8) {
                                outPx = 0;
                            } else {
                                int a = clamp255((int) Math.round(diff * 1.5));
                                outPx = (a << 24) | (255 << 16) | (255 << 8) | 255;
                            }
                        } else {
                            // Opaque diff: emit the decorated (sherd) pixels where
                            // they differ from the plain pot, transparent elsewhere.
                            int delta = Math.abs(rd - rb) + Math.abs(gd - gb) + Math.abs(bd - bb);
                            if (delta <= 12) {
                                outPx = 0;
                            } else {
                                outPx = (255 << 24) | (bd << 16) | (gd << 8) | rd;
                            }
                        }
                        out.setPixel(x, y, outPx);
                    }
                }
                out.writeToFile(outPath);
                out.close();
                successCount++;
            } catch (Exception e) {
                System.err.println("[dumper] Failed to save " + outPath + ": " + e.getMessage());
                failedCount++;
            } finally {
                fullImage.close();
                currentIndex++;
                waitingForScreenshot = false;
            }
        });
    }

    // ------------------------------------------------------------------ //
    //  Called from GuiExtractMixin after Gui.extractRenderState runs.     //
    // ------------------------------------------------------------------ //

    public static void onGuiExtract(GuiGraphicsExtractor graphics) {
        if (!dumping || waitingForScreenshot) return;

        if (decorationMode) {
            if (currentIndex >= jobs.size()) return;
            DecorationJob job = jobs.get(currentIndex);
            // Both over the same black background so the diff cancels it.
            graphics.fill(0, 0, 16, 16, 0xFF000000);
            graphics.item(job.base, 0, 0);
            graphics.fill(16, 0, 32, 16, 0xFF000000);
            graphics.item(job.decorated, 16, 0);
        } else {
            if (currentIndex >= items.size()) return;
            ItemStack stack = new ItemStack(items.get(currentIndex));
            // Black background at x=0, white at x=16, for alpha reconstruction.
            graphics.fill(0, 0, 16, 16, 0xFF000000);
            graphics.item(stack, 0, 0);
            graphics.fill(16, 0, 32, 16, 0xFFFFFFFF);
            graphics.item(stack, 16, 0);
        }

        itemRenderedThisFrame = true;
    }

    /** Clamp an int into the [0, 255] byte range. */
    private static int clamp255(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    private static void msg(Minecraft client, String text) {
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal(text));
        } else {
            System.out.println(text);
        }
    }
}
