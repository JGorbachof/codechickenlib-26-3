package codechicken.lib.internal.mixin;

import com.mojang.blaze3d.platform.cursor.CursorType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CursorType.class)
public interface CursorTypeAccessor {
    @Invoker("<init>")
    static CursorType ccl$createCursor(String name,long handle){
        throw new AssertionError("Mixin constructor invoker was not applied");
    }
}