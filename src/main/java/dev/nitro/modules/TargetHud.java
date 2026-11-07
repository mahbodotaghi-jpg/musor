package dev.nitro.modules;

import dev.nitro.module.Module;
import dev.nitro.settings.Setting;
import dev.nitro.util.ColorUtil;
import dev.nitro.util.Frosted;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/** Target HUD: name, health, armor and held items of the player you are looking at (or the nearest one). */
public final class TargetHud extends Module {
    public enum Mode {
        CROSSHAIR("Crosshair"), NEAREST("Nearest player");
        private final String label;
        Mode(String l) { label = l; }
        @Override public String toString() { return label; }
    }

    private final Setting.Choice<Mode> mode = new Setting.Choice<>("Target", Mode.CROSSHAIR, Mode.class);
    private final Setting.Num range = new Setting.Num("Nearest range", 24, 4, 128, 1);
    private final Setting.Num hold = new Setting.Num("Hold time (s)", 2, 0, 10, 0.5);
    private final Setting.Bool head = new Setting.Bool("Show head", true);
    private final Setting.Bool armor = new Setting.Bool("Show armor & items", true);
    private final Setting.Bool distance = new Setting.Bool("Show distance", true);
    private final Setting.Num offX = new Setting.Num("X offset", 24, -400, 400, 1);
    private final Setting.Num offY = new Setting.Num("Y offset", 24, -300, 300, 1);
    private final Setting.Color tint = new Setting.Color("Panel tint", 0x20242C);
    private final Setting.Num opacity = new Setting.Num("Panel opacity", 60, 20, 95, 5);
    private final Setting.Color accent = new Setting.Color("Accent", 0x6EA8FF);

    private PlayerEntity target;
    private long lastSeen;

    public TargetHud() {
        super("Target HUD", "Shows health, name and armor of the targeted player.", Category.HUD);
        add(mode, range, hold, head, armor, distance, offX, offY, tint, opacity, accent);
        range.showIf(() -> mode.value == Mode.NEAREST);
    }

    @Override
    public void onDisable() {
        target = null;
    }

    @Override
    public void onWorldChanged() {
        target = null;
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) {
            target = null;
            return;
        }
        PlayerEntity found = null;
        if (mode.value == Mode.CROSSHAIR) {
            if (mc.targetedEntity instanceof PlayerEntity p && valid(p)) found = p;
        } else {
            double best = range.d() * range.d();
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (!valid(p)) continue;
                double d = mc.player.squaredDistanceTo(p);
                if (d < best) {
                    best = d;
                    found = p;
                }
            }
        }
        long now = System.currentTimeMillis();
        if (found != null) {
            target = found;
            lastSeen = now;
        } else if (target != null && (!valid(target) || now - lastSeen > hold.d() * 1000.0)) {
            target = null;
        }
    }

    private boolean valid(PlayerEntity p) {
        return p != mc.player && p.isAlive() && !p.isRemoved() && !p.isSpectator();
    }

    @Override
    public void renderHud(DrawContext ctx) {
        PlayerEntity p = target;
        if (p == null || mc.player == null || mc.options.hudHidden) return;
        TextRenderer tr = mc.textRenderer;

        boolean items = armor.on();
        final int W = 150;
        int h = 33 + (items ? 20 : 0);
        int x = ctx.getScaledWindowWidth() / 2 + offX.i();
        int y = ctx.getScaledWindowHeight() / 2 + offY.i();

        Frosted.draw(ctx, x, y, W, h, tint.rgb(), opacity.f() / 100f, accent.rgb());

        int tx = x + 8;
        if (head.on() && p instanceof AbstractClientPlayerEntity ap) {
            PlayerSkinDrawer.draw(ctx, ap.getSkinTextures(), x + 8, y + 5, 24);
            tx = x + 38;
        }

        float hp = p.getHealth(), max = Math.max(1f, p.getMaxHealth()), abs = p.getAbsorptionAmount();
        String hpText = String.format("%.1f", hp) + (abs > 0 ? " +" + String.format("%.0f", abs) : "");

        String name = tr.trimToWidth(p.getName().getString(), W - (tx - x) - 36);
        ctx.drawText(tr, name, tx, y + 5, 0xFFFFFFFF, false);
        ctx.drawText(tr, hpText, x + W - 6 - tr.getWidth(hpText), y + 5, abs > 0 ? 0xFFFFD34D : 0xFFB8B8C0, false);

        float frac = Math.max(0f, Math.min(1f, hp / max));
        int bx = tx, bw = x + W - 8 - tx, by = y + 17;
        ctx.fill(bx, by, bx + bw, by + 5, 0xFF1B1B20);
        ctx.fill(bx, by, bx + (int) (bw * frac), by + 5, 0xFF000000 | ColorUtil.lerp(0xE05555, 0x55C46A, frac));
        if (abs > 0) ctx.fill(bx, by + 5, bx + Math.min(bw, (int) (bw * abs / max)), by + 6, 0xFFFFD34D);

        if (distance.on()) {
            String d = String.format("%.1fm", mc.player.distanceTo(p));
            ctx.drawText(tr, d, tx, y + 25, 0xFFB8B8C0, false);
        }

        if (items) {
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
                    EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
            int ix = x + 8, iy = y + 33;
            for (EquipmentSlot s : slots) {
                ItemStack st = p.getEquippedStack(s);
                if (!st.isEmpty()) {
                    ctx.drawItem(st, ix, iy);
                    ctx.drawStackOverlay(tr, st, ix, iy);
                }
                ix += 20;
            }
        }
    }
}
