package codechicken.lib.internal;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
class ItemExportTests {
    @Test void premultipliedPixelsConvertToStraightAbgr(){
        assertEquals(0x800080FF,ItemFileRenderer.straightAlphaAbgr(128,64,0,128));
        assertEquals(0,ItemFileRenderer.straightAlphaAbgr(12,34,56,0));
        assertEquals(0xFF030201,ItemFileRenderer.straightAlphaAbgr(1,2,3,255));
    }
    @Test void exportPathsStayWithinRoot(){
        assertEquals(Path.of("exports","sub","file.png"),ItemFileRenderer.exportPath("sub/file.png"));
        assertThrows(IllegalArgumentException.class,()->ItemFileRenderer.exportPath("../outside.png"));
        assertThrows(IllegalArgumentException.class,()->ItemFileRenderer.exportPath("."));
        assertThrows(IllegalArgumentException.class,()->ItemFileRenderer.exportPath(Path.of("outside.png").toAbsolutePath().toString()));
    }
}