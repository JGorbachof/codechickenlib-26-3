package codechicken.lib.gui.modular.lib;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.util.Util;

public final class GuiInput {
    private GuiInput() {}
    public static boolean hasShiftDown() {
        return InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
    }
    public static boolean hasControlDown() {
        if (Util.getPlatform()==Util.OS.OSX) return InputConstants.isKeyDown(InputConstants.KEY_LGUI) || InputConstants.isKeyDown(InputConstants.KEY_RGUI);
        return InputConstants.isKeyDown(InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL);
    }
}