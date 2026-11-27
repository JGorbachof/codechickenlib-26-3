package codechicken.lib.gui.modular;

import codechicken.lib.gui.modular.lib.GuiProvider;
import codechicken.lib.gui.modular.lib.GuiRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * A simple ModularGui screen implementation.
 * This is simply a wrapper for a {@link ModularGui} that takes a {@link GuiProvider}
 * This should be suitable for most basic gui screens.
 * <p>
 * Created by brandon3055 on 19/08/2023
 */
public class ModularGuiScreen extends Screen {

    protected final ModularGui modularGui;

    public ModularGuiScreen(GuiProvider provider) {
        super(Component.empty());
        this.modularGui = new ModularGui(provider);
        this.modularGui.setScreen(this);
    }

    public ModularGuiScreen(GuiProvider builder, Screen parentScreen) {
        super(Component.empty());
        this.modularGui = new ModularGui(builder, parentScreen);
        this.modularGui.setScreen(this);
    }

    public ModularGui getModularGui() {
        return modularGui;
    }

    @NotNull
    @Override
    public Component getTitle() {
        return modularGui.getGuiTitle();
    }

    @Override
    public boolean isPauseScreen() {
        return modularGui.isPauseScreen();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return modularGui.closeOnEscape();
    }

    @Override
    protected void init() {
        modularGui.onScreenInit(minecraft, font, width, height);
    }


    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        if (modularGui.renderBackground()) {
            extractBackground(graphics, mouseX, mouseY, partialTicks);
        }
        GuiRender render = GuiRender.convert(graphics);
        modularGui.render(render, partialTicks);
        //Ensure we render overlay on top of things like JEI
        render.pose().pushPose();
        render.pose().translate(0, 0, 400);
        modularGui.renderOverlay(render, partialTicks);
        render.pose().popPose();
        render.flush();
    }

    @Override
    public void tick() {
        modularGui.tick();
    }

    @Override
    public void removed() {
        modularGui.onGuiClose();
    }

    //=== Input Pass-though ===//

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        modularGui.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return modularGui.mouseClicked(event.x(),event.y(), legacyButton(event.button())) || super.mouseClicked(event,doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return modularGui.mouseReleased(event.x(),event.y(),legacyButton(event.button())) || super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return modularGui.mouseScrolled(mouseX, mouseY, scrollX, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return modularGui.keyPressed(event.key(),event.keycode(),event.modifiers()) || super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        return modularGui.keyReleased(event.key(),event.keycode(),event.modifiers()) || super.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        boolean consumed=false;
        for(char ch:Character.toChars(event.codepoint())) consumed |= modularGui.charTyped(ch,0);
        return consumed || super.charTyped(event);
    }
    static int legacyButton(int button) {
        return switch(button) { case 1 -> 0; case 2 -> 2; case 3 -> 1; default -> button-1; };
    }}
