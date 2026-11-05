package com.modhub.screen;

import com.modhub.ModHubClient;
import com.modhub.api.ModrinthClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ModHubScreen extends Screen {
    private static final int PURPLE = 0xFF9D7BFF;
    private static final int BLUE = 0xFF4DA3FF;
    private static final int WHITE = 0xFFFFFFFF;

    private final ModrinthClient api = new ModrinthClient();
    private String query = "";
    private List<ModrinthClient.Hit> results = List.of();
    private String status = "Search Modrinth for Fabric mods for Minecraft " + ModrinthClient.GAME_VERSION;
    private TextFieldWidget searchField;

    public ModHubScreen() {
        super(Text.literal("ModHub"));
    }

    @Override
    protected void init() {
        int cx = width / 2;

        searchField = addDrawableChild(new TextFieldWidget(textRenderer, cx - 150, 28, 220, 20, Text.literal("Search")));
        searchField.setMaxLength(100);
        searchField.setText(query);

        addDrawableChild(ButtonWidget.builder(Text.literal("Search"), b -> search())
                .dimensions(cx + 76, 28, 74, 20).build());

        int y = 58;
        for (ModrinthClient.Hit hit : results) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Install"), b -> install(hit))
                    .dimensions(cx + 76, y, 74, 20).build());
            y += 24;
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Refresh Game"), b -> refreshGame())
                .dimensions(cx - 150, height - 28, 146, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
                .dimensions(cx + 4, height - 28, 146, 20).build());
    }

    private void search() {
        query = searchField.getText().trim();
        if (query.isEmpty()) {
            status = "Type something to search for.";
            return;
        }
        status = "Searching...";
        String q = query;
        CompletableFuture.supplyAsync(() -> {
            try {
                return api.search(q);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).whenComplete((list, err) -> MinecraftClient.getInstance().execute(() -> {
            if (err != null) {
                status = "Search failed: " + rootMessage(err);
            } else {
                results = list;
                status = list.size() + " result(s) for Minecraft " + ModrinthClient.GAME_VERSION;
                clearAndInit();
            }
        }));
    }

    private void install(ModrinthClient.Hit hit) {
        status = "Installing " + hit.title() + "...";
        CompletableFuture.runAsync(() -> {
            try {
                var path = api.download(hit, ModHubClient.modsDir());
                MinecraftClient.getInstance().execute(() ->
                        status = "Installed " + path.getFileName() + ". Restart Minecraft to load it.");
            } catch (Exception e) {
                MinecraftClient.getInstance().execute(() ->
                        status = "Install failed: " + rootMessage(e));
            }
        });
    }

    private void refreshGame() {
        MinecraftClient mc = MinecraftClient.getInstance();
        status = "Refreshing...";
        // Supported reload path: reloads resource packs, shaders and datapack-style assets.
        mc.reloadResources().thenRun(() -> mc.execute(() -> {
            if (ModHubClient.restartRequired()) {
                status = "Restart Required: the mods folder changed. Java mods only load at startup.";
            } else {
                status = "Refreshed. Resource packs reloaded.";
            }
        }));
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null) cur = cur.getCause();
        return cur.getMessage() == null ? cur.getClass().getSimpleName() : cur.getMessage();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int cx = width / 2;

        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("ModHub"), cx, 10, PURPLE);
        ctx.drawTextWithShadow(textRenderer, Text.literal(status), cx - 150, height - 44, BLUE);

        int y = 58;
        for (ModrinthClient.Hit hit : results) {
            String line = hit.title() + " by " + hit.author() + " (" + hit.downloads() + " dl)";
            if (line.length() > 44) line = line.substring(0, 41) + "...";
            ctx.drawTextWithShadow(textRenderer, Text.literal(line), cx - 150, y + 6, WHITE);
            y += 24;
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
