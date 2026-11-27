package codechicken.lib.datagen;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DynamicTextureTests {
    @Test void asymmetricBordersAndTiledCenter(){
        var input=new BufferedImage(5,4,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<4;y++)for(int x=0;x<5;x++)input.setRGB(x,y,0x80000000|y<<8|x);
        var out=DynamicTextureProvider.tile(input,9,7,1,2,1,1);
        int[] xs={0,1,2,3,2,3,2,3,4};
        int[] ys={0,1,2,1,2,1,3};
        for(int y=0;y<7;y++)for(int x=0;x<9;x++)assertEquals(input.getRGB(xs[x],ys[y]),out.getRGB(x,y));
    }
    @Test void shrinkPreservesBordersAndInvalidSizesReject(){
        var image=new BufferedImage(5,5,BufferedImage.TYPE_INT_ARGB);
        image.setRGB(4,4,0xFF123456);
        assertEquals(0xFF123456,DynamicTextureProvider.tile(image,2,2,1,1,1,1).getRGB(1,1));
        assertThrows(IllegalArgumentException.class,()->DynamicTextureProvider.tile(image,1,3,1,1,1,1));
        assertThrows(IllegalArgumentException.class,()->DynamicTextureProvider.tile(image,10,10,-1,0,0,0));
    }
}