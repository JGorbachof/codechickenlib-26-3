package codechicken.lib.gui.modular.lib;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GuiColourTests {
    @Test void textRetainsLegacyRgbAndExplicitAlpha(){
        assertEquals(0xFF123456,GuiRender.legacyTextColour(0x123456));
        assertEquals(0x80123456,GuiRender.legacyTextColour(0x80123456));
    }
}