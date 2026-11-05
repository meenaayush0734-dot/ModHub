package com.modhub;

import com.modhub.screen.ModHubScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ModHubClient implements ClientModInitializer {
    public static final String MOD_ID = "modhub";

    /** Jar names present when the game started. Java mods cannot be hot-loaded, so changes need a restart. */
    private static Set<String> startupJars = Set.of();
    private static KeyBinding openKey;

    public static Path modsDir() {
        return FabricLoader.getInstance().getGameDir().resolve("mods");
    }

    public static Set<String> listJars() {
        try (Stream<Path> files = Files.list(modsDir())) {
            return files
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.endsWith(".jar"))
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            return Set.of();
        }
    }

    /** True when the mods folder differs from what was loaded at startup. */
    public static boolean restartRequired() {
        return !listJars().equals(startupJars);
    }

    @Override
    public void onInitializeClient() {
        startupJars = listJars();

        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key." + MOD_ID + ".open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                KeyBinding.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new ModHubScreen());
                }
            }
        });
    }
}
