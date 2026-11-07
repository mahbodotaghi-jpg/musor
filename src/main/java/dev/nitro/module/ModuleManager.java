package dev.nitro.module;

import dev.nitro.Config;
import dev.nitro.modules.BaseFinder;
import dev.nitro.modules.Freecam;
import dev.nitro.modules.LightDetector;
import dev.nitro.modules.SpotifyHud;
import dev.nitro.modules.StorageEsp;
import dev.nitro.modules.TargetHud;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;

import java.util.ArrayList;
import java.util.List;

public final class ModuleManager {
    public static final ModuleManager INSTANCE = new ModuleManager();

    public final List<Module> modules = new ArrayList<>();
    private ClientWorld lastWorld;

    private ModuleManager() {}

    public void init() {
        modules.add(new LightDetector());
        modules.add(new BaseFinder());
        modules.add(new Freecam());
        modules.add(new StorageEsp());
        modules.add(new TargetHud());
        modules.add(new SpotifyHud());
        Config.load();
    }

    public void tick(MinecraftClient mc) {
        if (mc.world != lastWorld) {
            lastWorld = mc.world;
            for (Module m : modules) m.onWorldChanged();
        }
        if (mc.world == null || mc.player == null) return;

        long handle = mc.getWindow().getHandle();
        for (Module m : modules) {
            if (m.key >= 0 && mc.currentScreen == null) {
                boolean down = InputUtil.isKeyPressed(handle, m.key);
                if (down && !m.keyWasDown) m.toggle();
                m.keyWasDown = down;
            } else {
                m.keyWasDown = false;
            }
            if (m.enabled) m.onTick();
        }
    }

    public void renderWorld(WorldRenderContext ctx) {
        for (Module m : modules) if (m.enabled) m.renderWorld(ctx);
    }

    public void renderHud(DrawContext ctx) {
        for (Module m : modules) if (m.enabled) m.renderHud(ctx);
    }
}
