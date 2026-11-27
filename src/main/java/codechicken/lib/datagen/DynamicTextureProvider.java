package codechicken.lib.datagen;

import codechicken.lib.gui.modular.lib.DynamicTextures;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingOutputStream;
import net.minecraft.util.Util;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * This provider can be used to create a data generator that can generate various textures programmatically.
 * The primary function is to convert dynamically resized textures into new fixed size textures.
 * <p>
 * Created by brandon3055 on 07/09/2023
 */
public class DynamicTextureProvider implements DataProvider {

    private final DataGenerator gen;
    private final ResourceProvider fileHelper;
    private final String modid;
    private final java.util.Map<Path,GeneratorResult> results = new java.util.LinkedHashMap<>();
    private java.util.Map<Path,GeneratorResult> generated;

    public DynamicTextureProvider(DataGenerator gen, ResourceProvider fileHelper, String modid) {
        this.gen = gen;
        this.fileHelper = fileHelper;
        this.modid = modid;
    }

    /**
     * If you are extending this provider, then override this method to add your textures.
     */
    public void addTextures() {
    }

    public void addDynamicTextures(DynamicTextures dynamicTextures) {
        dynamicTextures.makeTextures(t -> {
            addDynamicTexture(t.dynamicInput(), t.outputLocation(), t.width(), t.height(), t.topBorder(), t.leftBorder(), t.bottomBorder(), t.rightBorder());
            return t.guiTexturePath();
        });
    }

    public void addDynamicTexture(String dynamicInput, String outputTexture, int width, int height, int border) {
        addDynamicTexture(dynamicInput, outputTexture, width, height, border, border, border, border);
    }

    public void addDynamicTexture(String dynamicInput, String outputTexture, int width, int height, int topBorder, int leftBorder, int bottomBorder, int rightBorder) {
        addDynamicTexture(Identifier.fromNamespaceAndPath(modid, dynamicInput), Identifier.fromNamespaceAndPath(modid, outputTexture), width, height, topBorder, leftBorder, bottomBorder, rightBorder);
    }

    public void addDynamicTexture(Identifier dynamicInput, Identifier outputTexture, int width, int height, int border) {
        addDynamicTexture(dynamicInput, outputTexture, width, height, border, border, border, border);
    }

    public void addDynamicTexture(Identifier dynamicInput, Identifier outputTexture, int width, int height, int topBorder, int leftBorder, int bottomBorder, int rightBorder) {
        try {
            if (!dynamicInput.getPath().endsWith(".png")) {
                dynamicInput = Identifier.fromNamespaceAndPath(dynamicInput.getNamespace(), dynamicInput.getPath() + ".png");
            }
            if (!outputTexture.getPath().endsWith(".png")) {
                outputTexture = Identifier.fromNamespaceAndPath(outputTexture.getNamespace(), outputTexture.getPath() + ".png");
            }

            Resource inputResource = fileHelper.getResourceOrThrow(dynamicInput);
            PackOutput packOutput = gen.getPackOutput("assets/" + outputTexture.getNamespace());
            Path outputFile = packOutput.getOutputFolder().resolve(outputTexture.getPath());

            BufferedImage input;
            try (var stream = inputResource.open()) { input = ImageIO.read(stream); }
            if (input == null) throw new IOException("Invalid PNG: " + dynamicInput);
            BufferedImage output=tile(input,width,height,topBorder,leftBorder,bottomBorder,rightBorder);
            ByteArrayOutputStream bos=new ByteArrayOutputStream();
            if(!ImageIO.write(output,"png",bos))throw new IOException("PNG writer unavailable");
            (generated==null?results:generated).put(outputFile,new GeneratorResult(bos.toByteArray(),outputFile));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Tile the center and edges while retaining the original border pixels. */
    static BufferedImage tile(BufferedImage input,int width,int height,int top,int left,int bottom,int right){
        int sw=input.getWidth(),sh=input.getHeight();
        if(top<0||left<0||bottom<0||right<0||width<=0||height<=0||
                (long)left+right>=sw||(long)top+bottom>=sh||
                (long)left+right>width||(long)top+bottom>height)
            throw new IllegalArgumentException("Invalid texture dimensions or borders");
        var output=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<height;y++){
            int sy=sourceCoordinate(y,height,sh,top,bottom);
            for(int x=0;x<width;x++)output.setRGB(x,y,input.getRGB(sourceCoordinate(x,width,sw,left,right),sy));
        }
        return output;
    }

    private static int sourceCoordinate(int value,int target,int source,int before,int after){
        if(value<before)return value;
        if(value>=target-after)return source-(target-value);
        return before+(value-before)%(source-before-after);
    }

    @Override
    public synchronized CompletableFuture<?> run(CachedOutput writer) {
        var current=new java.util.LinkedHashMap<>(results);
        generated=current;
        try{addTextures();}finally{generated=null;}
        return CompletableFuture.allOf(current.values().stream().map(result->saveResult(writer,result)).toArray(CompletableFuture[]::new));
    }

    @SuppressWarnings ("UnstableApiUsage")
    static CompletableFuture<?> saveResult(CachedOutput arg, GeneratorResult result) {
        return CompletableFuture.runAsync(() -> {
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                HashingOutputStream hos = new HashingOutputStream(Hashing.sha1(), bos);
                hos.write(result.fileBytes);
                arg.writeIfNeeded(result.path, bos.toByteArray(), hos.hash());
                LOGGER.info("Saved file to {}", result.path);
            } catch (IOException ex) {
                throw new java.io.UncheckedIOException("Failed to save " + result.path, ex);
            }

        }, Util.backgroundExecutor());
    }

    @Override
    public String getName() {
        return "dynamic-textures:" + modid;
    }

    private static class GeneratorResult {

        private final byte[] fileBytes;
        private final Path path;

        private GeneratorResult(byte[] fileBytes, Path filePath) {
            this.fileBytes = fileBytes;
            this.path = filePath;
        }
    }
}
