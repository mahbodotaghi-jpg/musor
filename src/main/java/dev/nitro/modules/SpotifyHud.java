package dev.nitro.modules;

import dev.nitro.module.Module;
import dev.nitro.settings.Setting;
import dev.nitro.util.ColorUtil;
import dev.nitro.util.Frosted;
import dev.nitro.util.NowPlaying;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Spotify HUD: shows the track playing in the local Spotify app (title, artist, album, artwork, progress).
 * No login needed; it reads the OS media session (Windows), AppleScript (macOS) or playerctl (Linux).
 * Polling happens on a background thread, see {@link NowPlaying}.
 */
public final class SpotifyHud extends Module {
    public enum Pos {
        TOP_LEFT("Top left"), TOP_RIGHT("Top right"), BOTTOM_LEFT("Bottom left"), BOTTOM_RIGHT("Bottom right");
        private final String label;
        Pos(String l) { label = l; }
        @Override public String toString() { return label; }
    }

    private static final Identifier ART_ID = Identifier.of("nitro", "spotify_art");

    private final Setting.Choice<Pos> pos = new Setting.Choice<>("Position", Pos.BOTTOM_LEFT, Pos.class);
    private final Setting.Num width = new Setting.Num("Width", 180, 130, 260, 5);
    private final Setting.Bool showArt = new Setting.Bool("Album artwork", true);
    private final Setting.Num artSize = new Setting.Num("Artwork size", 40, 24, 64, 2);
    private final Setting.Bool showAlbum = new Setting.Bool("Show album", true);
    private final Setting.Bool showProgress = new Setting.Bool("Progress bar", true);
    private final Setting.Bool hideIdle = new Setting.Bool("Hide when nothing plays", true);
    private final Setting.Bool hidePaused = new Setting.Bool("Hide when paused", false);
    private final Setting.Num poll = new Setting.Num("Update interval (s)", 2, 1, 10, 1);
    private final Setting.Color tint = new Setting.Color("Panel tint", 0x20242C);
    private final Setting.Num opacity = new Setting.Num("Panel opacity", 60, 20, 95, 5);
    private final Setting.Color accent = new Setting.Color("Accent", 0x1DB954);

    private final NowPlaying np = new NowPlaying();
    private int runningInterval = -1;
    private BufferedImage uploadedArt;
    private boolean textureRegistered;

    public SpotifyHud() {
        super("Spotify HUD", "Shows the song currently playing in Spotify.", Category.HUD);
        add(pos, width, showArt, artSize, showAlbum, showProgress, hideIdle, hidePaused, poll, tint, opacity, accent);
        artSize.showIf(showArt::on);
    }

    @Override
    public void onEnable() {
        runningInterval = poll.i();
        np.start(runningInterval);
    }

    @Override
    public void onDisable() {
        np.stop();
        runningInterval = -1;
        releaseTexture();
    }

    @Override
    public void onTick() {
        if (runningInterval != poll.i()) {
            runningInterval = poll.i();
            np.start(runningInterval);
        }
    }

    private void releaseTexture() {
        if (textureRegistered) {
            mc.getTextureManager().destroyTexture(ART_ID);
            textureRegistered = false;
        }
        uploadedArt = null;
    }

    /** Render thread: turn the background-loaded BufferedImage into a GPU texture (once per track). */
    private void syncTexture() {
        BufferedImage a = np.art();
        if (a == uploadedArt) return;
        uploadedArt = a;
        if (a == null) {
            releaseTexture();
            return;
        }
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(a, "png", bos);
            NativeImage img = NativeImage.read(new ByteArrayInputStream(bos.toByteArray()));
            mc.getTextureManager().registerTexture(ART_ID, new NativeImageBackedTexture(img));
            textureRegistered = true;
        } catch (Exception e) {
            releaseTexture();
        }
    }

    private static String time(long ms) {
        long s = Math.max(0, ms / 1000);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    @Override
    public void renderHud(DrawContext ctx) {
        if (mc.options.hudHidden) return;
        TextRenderer tr = mc.textRenderer;
        NowPlaying.Info i = np.info();

        boolean idle = i == null || (i.title.isEmpty() && i.artist.isEmpty());
        if (idle && hideIdle.on()) return;
        if (!idle && !i.playing && hidePaused.on()) return;

        syncTexture();

        int W = width.i();
        int art = showArt.on() ? artSize.i() : 0;
        boolean haveArt = art > 0 && textureRegistered && !idle;
        int textX = 8 + (art > 0 ? art + 6 : 0);
        int textW = W - textX - 6;

        int lines = idle ? 1 : 2 + (showAlbum.on() && !i.album.isEmpty() ? 1 : 0);
        int textH = lines * 11 + (!idle && showProgress.on() ? 9 : 0);
        int H = Math.max(art, textH) + 10;

        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight(), margin = 8;
        boolean right = pos.value == Pos.TOP_RIGHT || pos.value == Pos.BOTTOM_RIGHT;
        boolean top = pos.value == Pos.TOP_LEFT || pos.value == Pos.TOP_RIGHT;
        int x = right ? sw - W - margin : margin;
        int y = top ? margin : sh - H - margin;

        Frosted.draw(ctx, x, y, W, H, tint.rgb(), opacity.f() / 100f, accent.rgb());

        if (art > 0) {
            int ay = y + (H - art) / 2;
            if (haveArt) {
                ctx.getMatrices().push();
                ctx.getMatrices().translate(x + 8, ay, 0);
                float sc = art / (float) NowPlaying.ART_SIZE;
                ctx.getMatrices().scale(sc, sc, 1f);
                ctx.drawTexture(RenderLayer::getGuiTextured, ART_ID, 0, 0, 0f, 0f,
                        NowPlaying.ART_SIZE, NowPlaying.ART_SIZE, NowPlaying.ART_SIZE, NowPlaying.ART_SIZE);
                ctx.getMatrices().pop();
            } else {
                ctx.fill(x + 8, ay, x + 8 + art, ay + art, ColorUtil.argb(accent.rgb(), 0.25f));
            }
        }

        int tx = x + textX, ty = y + (H - textH) / 2 + 1;
        if (idle) {
            String s = tr.trimToWidth(np.status(), textW);
            ctx.drawText(tr, s, tx, ty, 0xFFB8B8C0, false);
            return;
        }

        ctx.drawText(tr, tr.trimToWidth(i.title, textW), tx, ty, 0xFFFFFFFF, false);
        ty += 11;
        ctx.drawText(tr, tr.trimToWidth(i.artist, textW), tx, ty, 0xFFB8B8C0, false);
        ty += 11;
        if (showAlbum.on() && !i.album.isEmpty()) {
            ctx.drawText(tr, tr.trimToWidth(i.album, textW), tx, ty, 0xFF8A8A94, false);
            ty += 11;
        }
        if (showProgress.on() && i.lenMs > 0) {
            long cur = i.estimatedPosMs();
            float frac = Math.max(0f, Math.min(1f, cur / (float) i.lenMs));
            int by = ty + 3;
            ctx.fill(tx, by, tx + textW, by + 2, 0xFF1B1B20);
            ctx.fill(tx, by, tx + (int) (textW * frac), by + 2, 0xFF000000 | accent.rgb());
            if (!i.playing) {
                String p = "paused " + time(cur);
                ctx.drawText(tr, p, x + W - 6 - tr.getWidth(p), y + 4, 0xFF8A8A94, false);
            }
        }
    }
}
