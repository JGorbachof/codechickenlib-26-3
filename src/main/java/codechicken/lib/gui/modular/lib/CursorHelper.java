package codechicken.lib.gui.modular.lib;

import codechicken.lib.CodeChickenLib;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.cursor.CursorType;
import codechicken.lib.internal.mixin.CursorTypeAccessor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.BufferUtils;
import static org.lwjgl.sdl.SDLMouse.*;
import static org.lwjgl.sdl.SDLSurface.*;
import static org.lwjgl.sdl.SDLPixels.SDL_PIXELFORMAT_RGBA32;

import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Created by brandon3055 on 11/5/20.
 */
public class CursorHelper {
    public static final Logger LOGGER = LogManager.getLogger();

    public static final Identifier DRAG = Identifier.fromNamespaceAndPath(CodeChickenLib.MOD_ID, "textures/gui/cursors/drag.png");
    public static final Identifier RESIZE_H = Identifier.fromNamespaceAndPath(CodeChickenLib.MOD_ID, "textures/gui/cursors/resize_h.png");
    public static final Identifier RESIZE_V = Identifier.fromNamespaceAndPath(CodeChickenLib.MOD_ID, "textures/gui/cursors/resize_v.png");
    public static final Identifier RESIZE_TRBL = Identifier.fromNamespaceAndPath(CodeChickenLib.MOD_ID, "textures/gui/cursors/resize_diag_trbl.png");
    public static final Identifier RESIZE_TLBR = Identifier.fromNamespaceAndPath(CodeChickenLib.MOD_ID, "textures/gui/cursors/resize_diag_tlbr.png");

    private record Cursor(long handle,CursorType type) {}
    private static final Map<Identifier, Cursor> cursors = new HashMap<>();
    private static Identifier active = null;

    private static long createCursor(Identifier cursorTexture) {
        try {
            Resource resource = Minecraft.getInstance().getResourceManager().getResource(cursorTexture).orElse(null);
            if (resource == null) return MemoryUtil.NULL;
            BufferedImage image;
            try (var stream = resource.open()) { image = ImageIO.read(stream); }
            if (image == null) throw new IllegalArgumentException("Invalid cursor PNG " + cursorTexture);
            int width = image.getWidth(), height = image.getHeight();
            ByteBuffer pixels = BufferUtils.createByteBuffer(Math.multiplyExact(Math.multiplyExact(width,height),4));
            for (int y=0;y<height;y++) for (int x=0;x<width;x++) {
                int argb = image.getRGB(x,y);
                pixels.put((byte)(argb >>> 16)).put((byte)(argb >>> 8)).put((byte)argb).put((byte)(argb >>> 24));
            }
            pixels.flip();
            var surface = SDL_CreateSurfaceFrom(width,height,SDL_PIXELFORMAT_RGBA32,pixels,width*4);
            if (surface == null) return MemoryUtil.NULL;
            try {
                return SDL_CreateColorCursor(surface,Math.min(16,width-1),Math.min(16,height-1));
            } finally { SDL_DestroySurface(surface); }
        } catch (Exception e) {
            LOGGER.warn("An error occurred while creating cursor", e);
            return MemoryUtil.NULL;
        }
    }

    public static void setCursor(@Nullable Identifier cursor) {
        active=cursor;
    }

    /** Participate in the native per-frame cursor selection, including its allow-change policy. */
    public static void requestCursor(GuiGraphicsExtractor graphics){
        if(active==null)return;
        Cursor cursor=cursors.computeIfAbsent(active,id->{
            long handle=createCursor(id);
            if(handle==MemoryUtil.NULL)return new Cursor(handle,CursorType.DEFAULT);
            try{return new Cursor(handle,CursorTypeAccessor.ccl$createCursor(id.toString(),handle));}
            catch(Throwable t){SDL_DestroyCursor(handle);throw t;}
        });
        graphics.requestCursor(cursor.type());
    }

    public static void resetCursor(){active=null;}

    public static void onResourceReload(){
        resetCursor();
        if(cursors.isEmpty())return;
        // Invalidate the window's cached selected cursor before freeing SDL handles.
        Minecraft.getInstance().getWindow().selectCursor(CursorType.DEFAULT);
        cursors.values().forEach(cursor->{
            if(cursor.handle()!=MemoryUtil.NULL)SDL_DestroyCursor(cursor.handle());
        });
        cursors.clear();
    }
}