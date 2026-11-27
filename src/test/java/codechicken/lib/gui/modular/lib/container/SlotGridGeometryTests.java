package codechicken.lib.gui.modular.lib.container;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SlotGridGeometryTests {
    @Test void partialLastRowIsIncluded() {
        var grid=new SlotGridGeometry(10,9,2,3);
        assertEquals(9,grid.columns());
        assertEquals(2,grid.rows());
        assertEquals(178,grid.width());
        assertEquals(39,grid.height());
        assertEquals(1,grid.x(9));
        assertEquals(22,grid.y(9));
    }
    @Test void emptyAndNarrowGridsAreValid() {
        var empty=new SlotGridGeometry(0,9,2,3);
        assertEquals(0,empty.width());
        assertEquals(0,empty.height());
        assertThrows(IndexOutOfBoundsException.class,()->empty.x(0));
        var small=new SlotGridGeometry(2,9,0,0);
        assertEquals(36,small.width());
        assertEquals(18,small.height());
        assertThrows(IllegalArgumentException.class,()->new SlotGridGeometry(1,0,0,0));
    }
}