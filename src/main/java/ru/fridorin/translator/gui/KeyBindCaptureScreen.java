package ru.fridorin.translator.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;

public class KeyBindCaptureScreen extends Screen {
    private final Screen parent;
    private final KeyMapping keyMapping;

    public KeyBindCaptureScreen(Screen parent, KeyMapping keyMapping) {
        super(Component.literal("Key Binding"));
        this.parent = parent;
        this.keyMapping = keyMapping;
    }

    @Override
    //? if <26.1 {
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);
    //?} else {
    /*public void extractRenderState(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.extractBackground(graphics, mouseX, mouseY, delta);
    *///?}

        String mappingName = Component.translatable(keyMapping.getName()).getString();
        graphics.drawCenteredString(this.font, "Press any key to bind to: " + mappingName, this.width / 2, this.height / 2 - 20, 0xFFFFFF);
        graphics.drawCenteredString(this.font, "Press ESC to cancel", this.width / 2, this.height / 2 + 10, 0xAAAAAA);

        //? if <26.1 {
        super.render(graphics, mouseX, mouseY, delta);
        //?} else
        /*super.extractRenderState(graphics, mouseX, mouseY, delta);*/
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int keyCode = event.key();
        if (keyCode == InputConstants.KEY_ESCAPE) {
            this.minecraft.setScreen(parent);
            return true;
        }
        
        InputConstants.Key key = InputConstants.Type.KEYSYM.getOrCreate(keyCode);
        this.keyMapping.setKey(key);
        KeyMapping.resetMapping();
        this.minecraft.options.save();
        this.minecraft.setScreen(parent);
        return true;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        int button = event.button();
        InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(button);
        this.keyMapping.setKey(key);
        KeyMapping.resetMapping();
        this.minecraft.options.save();
        this.minecraft.setScreen(parent);
        return true;
    }
}
