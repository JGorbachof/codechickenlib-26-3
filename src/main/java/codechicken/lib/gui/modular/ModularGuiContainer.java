package codechicken.lib.gui.modular;

import codechicken.lib.gui.modular.elements.GuiElement;
import codechicken.lib.gui.modular.lib.GuiRender;
import codechicken.lib.gui.modular.lib.container.ContainerGuiProvider;
import codechicken.lib.gui.modular.lib.container.ContainerScreenAccess;
import codechicken.lib.gui.modular.lib.geometry.GeoParam;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Modular container extraction; native container input and quick-craft state remain authoritative. */
public class ModularGuiContainer<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> implements ContainerScreenAccess<T> {
    public final ModularGui modularGui;
    private int dynamicWidth, dynamicHeight;
    private final java.util.Set<Integer> nativePresses=new java.util.HashSet<>();

    public ModularGuiContainer(T menu, Inventory inventory, ContainerGuiProvider<T> provider) {
        super(menu,inventory,Component.empty());
        provider.setMenuAccess(this);
        modularGui=new ModularGui(provider);
        modularGui.setScreen(this);
    }
    public ModularGui getModularGui(){return modularGui;}
    @Override public Component getTitle(){return modularGui.getGuiTitle();}
    @Override public boolean shouldCloseOnEsc(){return modularGui.closeOnEscape();}
    @Override protected void init(){super.init();modularGui.onScreenInit(minecraft,font,width,height);refreshLayout(modularGui.computeMouseX(),modularGui.computeMouseY());}

    private void refreshLayout(double mouseX,double mouseY){
        modularGui.refreshSlotLayout(mouseX,mouseY);
        var root=modularGui.getRoot();
        topPos=(int)root.getValue(GeoParam.TOP);
        leftPos=(int)root.getValue(GeoParam.LEFT);
        dynamicWidth=(int)root.getValue(GeoParam.WIDTH);
        dynamicHeight=(int)root.getValue(GeoParam.HEIGHT);
        if(isQuickCrafting && quickCraftSlots.removeIf(slot->!isSlotInteractive(slot))) recalculateQuickCraftRemaining();
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partialTicks){
        refreshLayout(mouseX,mouseY);
        if(modularGui.renderBackground()) super.extractBackground(graphics,mouseX,mouseY,partialTicks);
        var render=GuiRender.convert(graphics);
        modularGui.render(render,partialTicks);
        render.flush();
        super.extractContents(graphics,mouseX,mouseY,partialTicks);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.ScreenEvent.Render.Foreground(this,graphics,mouseX,mouseY,partialTicks));
        super.extractCarriedItem(graphics,mouseX,mouseY);
        render.nextStratum();
        if(!(modularGui.doesFloatingItemDisableToolTips() && !menu.getCarried().isEmpty()) &&
                !renderHoveredStackToolTip(render,mouseX,mouseY)) modularGui.renderOverlay(render,partialTicks);
        render.flush();
    }
    @Override public void extractBackground(GuiGraphicsExtractor graphics,int x,int y,float partial) {
        // Background is explicitly extracted ahead of modular geometry above.
    }
    @Override protected void extractLabels(GuiGraphicsExtractor graphics,int x,int y){}
    @Override protected void extractSlots(GuiGraphicsExtractor graphics,int x,int y){
        if(modularGui.vanillaSlotRendering()) super.extractSlots(graphics,x,y);
    }
    public boolean usesModularSlotRendering(){return !modularGui.vanillaSlotRendering();}
    @Override public int getImageWidth(){return dynamicWidth>0?dynamicWidth:super.getImageWidth();}
    @Override public int getImageHeight(){return dynamicHeight>0?dynamicHeight:super.getImageHeight();}
    @Override protected boolean hasClickedOutside(double x,double y,int left,int top){
        return x<left||y<top||x>=left+getImageWidth()||y>=top+getImageHeight();
    }

    protected boolean renderHoveredStackToolTip(GuiRender render,int x,int y){
        if(menu.getCarried().isEmpty() && hoveredSlot!=null && hoveredSlot.hasItem()){
            var handler=modularGui.getSlotHandler(hoveredSlot);
            if(handler!=null && (handler.blockMouseOver(handler,x,y)||!handler.isMouseOver())) return false;
            ItemStack stack=hoveredSlot.getItem();
            render.toolTipWithImage(getTooltipFromContainerItem(stack),stack.getTooltipImage(),stack,x,y);
            return true;
        }
        return false;
    }
    @Override public void renderSlot(GuiRender render,Slot slot){
        if(modularGui.vanillaSlotRendering()||!slot.isActive()) return;
        var handler=modularGui.getSlotHandler(slot);
        if(handler==null||!modularGui.isSlotHandlerEnabled(handler))return;
        render.withNativePose(()->{
            var graphics=render.graphics();
            graphics.pose().translate(leftPos,topPos);
            super.extractSlot(graphics,slot,slot.x+leftPos,slot.y+topPos);
        });
    }
    private boolean isSlotInteractive(Slot slot){
        if(!slot.isActive())return false;
        var handler=modularGui.getSlotHandler(slot);
        return handler==null?modularGui.vanillaSlotRendering():modularGui.isSlotHandlerEnabled(handler);
    }
    @Override public boolean isHovering(Slot slot,double x,double y){
        if(!isSlotInteractive(slot)||!super.isHovering(slot,x,y)) return false;
        GuiElement<?> handler=modularGui.getSlotHandler(slot);
        return handler==null ? modularGui.vanillaSlotRendering() : (modularGui.isSlotHandlerEnabled(handler) && handler.isMouseOver() && !handler.blockMouseOver(handler,x,y));
    }
    public @Nullable Slot findSlot(double x,double y){
        for(Slot slot:menu.slots) if(slot.isActive()&&isHovering(slot,x,y)) return slot;
        return null;
    }
    @Override protected void slotClicked(Slot slot,int id,int button,ContainerInput input){
        if(slot!=null&&!isSlotInteractive(slot))return;
        super.slotClicked(slot,id,button,input);
    }
    @Override protected void containerTick(){modularGui.tick();}
    @Override public void removed(){super.removed();modularGui.onGuiClose();}
    @Override public void mouseMoved(double x,double y){refreshLayout(x,y);modularGui.mouseMoved(x,y);super.mouseMoved(x,y);}
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){
        refreshLayout(event.x(),event.y());
        hoveredSlot=findSlot(event.x(),event.y());
        if(modularGui.mouseClicked(event.x(),event.y(),ModularGuiScreen.legacyButton(event.button()))){nativePresses.remove(event.button());return true;}
        nativePresses.add(event.button());return super.mouseClicked(event,doubleClick);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event){
        refreshLayout(event.x(),event.y());
        boolean handled=modularGui.mouseReleased(event.x(),event.y(),ModularGuiScreen.legacyButton(event.button()));
        boolean nativeRelease=nativePresses.remove(event.button())||isQuickCrafting;

        if(nativeRelease)handled=super.mouseReleased(event)||handled;
        return handled||!nativeRelease;
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy){
        refreshLayout(event.x(),event.y());
        return nativePresses.contains(event.button())&&super.mouseDragged(event,dx,dy);
    }
    @Override public boolean mouseScrolled(double x,double y,double sx,double sy){
        refreshLayout(x,y);hoveredSlot=findSlot(x,y);
        return modularGui.mouseScrolled(x,y,sx,sy)||super.mouseScrolled(x,y,sx,sy);
    }
    @Override public boolean keyPressed(KeyEvent event){
        double x=modularGui.computeMouseX(),y=modularGui.computeMouseY();
        refreshLayout(x,y);hoveredSlot=findSlot(x,y);
        return modularGui.keyPressed(event.key(),event.keycode(),event.modifiers())||super.keyPressed(event);
    }
    @Override public boolean keyReleased(KeyEvent event){
        return modularGui.keyReleased(event.key(),event.keycode(),event.modifiers())||super.keyReleased(event);
    }
    @Override public boolean charTyped(CharacterEvent event){
        boolean consumed=false;
        for(char ch:Character.toChars(event.codepoint())) consumed |= modularGui.charTyped(ch,0);
        return consumed||super.charTyped(event);
    }
}