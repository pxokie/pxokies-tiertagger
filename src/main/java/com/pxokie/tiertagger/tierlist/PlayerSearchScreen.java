package com.pxokie.tiertagger.tierlist;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.uku3lig.ukulib.config.option.widget.TextInputWidget;
import net.uku3lig.ukulib.config.screen.CloseableScreen;
import org.jetbrains.annotations.NotNull;

public class PlayerSearchScreen extends CloseableScreen {
    private TextInputWidget textField;
    private Button searchButton;

    public PlayerSearchScreen(Screen parent) {
        super("Player Search", parent);
    }

    @Override
    protected void init() {
        String username = I18n.get("pxtiertagger.search.user");
        this.textField = this.addWidget(new TextInputWidget(this.width / 2 - 100, 116, 200, 20,
                "", s -> {
        }, username, s -> s.matches("[a-zA-Z0-9_]{1,16}"), 16));

        this.searchButton = this.addRenderableWidget(
                Button.builder(Component.translatable("pxtiertagger.search"), button -> this.showProfile())
                        .bounds(this.width / 2 - 100, this.height / 4 + 96 + 12, 200, 20)
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose())
                        .bounds(this.width / 2 - 100, this.height / 4 + 120 + 12, 200, 20)
                        .build()
        );

        this.setInitialFocus(this.textField);
    }

    @Override
    public void tick() {
        super.tick();
        this.searchButton.active = this.textField.isValid();
    }

    private void showProfile() {
        if (!this.textField.isValid()) return;
        Minecraft.getInstance().setScreen(new AllTiersScreen(this, null, this.textField.getText(), null));
    }

    @Override
    public boolean keyPressed(@NotNull net.minecraft.client.input.KeyEvent event) {
        if ((event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) && this.textField.isFocused()) {
            this.showProfile();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void resize(int width, int height) {
        String string = this.textField.getText();
        this.init(width, height);
        this.textField.setText(string);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 16777215);
        this.textField.render(graphics, mouseX, mouseY, delta);
    }
}
