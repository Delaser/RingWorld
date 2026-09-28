package dev.ringworld.client;

import dev.ringworld.world.RingWallStyle;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Direct material and preset selection; the parent owns the draft. */
final class RingWallChoiceScreen extends Screen {
    enum Kind { MATERIAL, PRESET }
    private final Screen parent;
    private final Kind kind;
    private final Consumer<RingWallStyle.Palette> paletteSelected;
    private final Consumer<RingWallStyle.Preset> presetSelected;

    RingWallChoiceScreen(Screen parent, Kind kind,
                         Consumer<RingWallStyle.Palette> paletteSelected,
                         Consumer<RingWallStyle.Preset> presetSelected) {
        super(Component.literal(kind == Kind.MATERIAL ? "Wall material" : "Wall style preset"));
        this.parent = parent;
        this.kind = kind;
        this.paletteSelected = paletteSelected;
        this.presetSelected = presetSelected;
    }

    @Override
    protected void init() {
        int w = Math.min(420, width - 16), left = (width - w) / 2;
        int half = (w - 20) / 2;
        for (int index = 0; index < 10; index++) {
            final int selected = index;
            String label = kind == Kind.MATERIAL
                    ? RingWallStyle.Palette.values()[index].label()
                    : RingWallStyle.Preset.values()[index].label();
            addRenderableWidget(Button.builder(Component.literal(label), button -> {
                if (kind == Kind.MATERIAL) paletteSelected.accept(RingWallStyle.Palette.values()[selected]);
                else presetSelected.accept(RingWallStyle.Preset.values()[selected]);
                RingMinecraftClientAccess.setScreen(minecraft, parent);
            }).bounds(left + 8 + (index % 2) * (half + 4), 45 + (index / 2) * 27,
                    half, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose())
                .bounds(width / 2 - 70, height - 29, 140, 20).build());
    }

    @Override
    public void onClose() { RingMinecraftClientAccess.setScreen(minecraft, parent); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int w = Math.min(420, width - 16), left = (width - w) / 2;
        g.fill(left, 7, left + w, height - 6, 0xE0172226);
        g.outline(left, 7, w, height - 13, 0xFF64776D);
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.centeredText(font, title, width / 2, 18, 0xFFFFFFFF);
    }
}
