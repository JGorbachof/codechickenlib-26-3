package codechicken.lib.gui.modular.lib;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GuiItemTintTests {
    @Test void premultipliedTintPreservesWhiteAndOpaqueColor(){
        assertEquals(-1,GuiItemTint.premultiply(-1));
        assertEquals(0xFF123456,GuiItemTint.premultiply(0xFF123456));
        assertEquals(0,GuiItemTint.premultiply(0x00FFFFFF));
        assertEquals(0x80408020,GuiItemTint.premultiply(0x8080FF40));
    }
}