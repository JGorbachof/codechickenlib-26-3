package codechicken.lib.internal;

import com.google.common.base.Suppliers;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.covers1624.quack.image.AnimatedGifEncoder;
import net.covers1624.quack.io.IOUtils;
import net.covers1624.quack.platform.OperatingSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Mostly internal, intended for developer use.
 * <p>
 * May change at any time.
 * <p>
 * Created by covers1624 on 27/2/23.
 */
public class ItemFileRenderer {

    public static final int DEFAULT_RES = 512;

    private static final Logger LOGGER = LogManager.getLogger();
    private static final java.util.Queue<RenderTask> tasks = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static final Path OUTPUT = Paths.get("exports");


    static Path exportPath(String file){
        Path relative=Path.of(file);
        Path result=OUTPUT.resolve(relative).normalize();
        if(relative.isAbsolute()||!result.startsWith(OUTPUT)||result.equals(OUTPUT))
            throw new IllegalArgumentException("Export path must be inside exports.");
        return result;
    }

    public static void close(){
        RenderTask task;
        while((task=tasks.poll())!=null)task.close();
    }

    static int straightAlphaAbgr(int r,int g,int b,int a){
        if(a==0)return 0;
        r=Math.min(255,(r*255+a/2)/a);g=Math.min(255,(g*255+a/2)/a);b=Math.min(255,(b*255+a/2)/a);
        return a<<24|b<<16|g<<8|r;
    }

    public static void renderStatic(ItemStack stack, String file, int resolution) {
        tasks.add(new StaticRenderTask(stack, resolution, exportPath(file)));
    }

    public static void addGifRenderTask(ItemStack stack, String file, int resolution, int fps, int duration) {
        tasks.add(new GifRenderTask(stack, resolution, exportPath(file), fps, duration));
    }

    public static boolean addWebpRenderTask(ItemStack stack, String file, int resolution, int fps, int duration) {
        if (!WebpRenderTask.isFfmpegAvailable()) return false;

        tasks.add(new WebpRenderTask(stack, resolution, exportPath(file), fps, duration));
        return true;
    }

    public static void tick() {
        RenderTask task = tasks.peek();
        if (task != null && guardRender(task)) {
            tasks.poll();
        }
    }

    private static boolean guardRender(RenderTask task) {
        try {
            return task.render();
        } catch (Throwable ex) {
            LOGGER.error("Failed to render.", ex);
            task.close();
            return true;
        }
    }

    private static abstract class RenderTask {

        private static final boolean DEBUG = Boolean.getBoolean("ccl.ItemFileRenderer.debug");

        protected final ItemStack stack;

        protected final int resolution;
        protected final Path path;

        private RenderTask(ItemStack stack, int resolution, Path path) {
            this.stack = stack.copy();
            if(resolution<=0)throw new IllegalArgumentException("Invalid resolution.");
            this.resolution = resolution;
            this.path = path;
        }

        /**
         * Perform the render operation.
         *
         * @return If the render operation is complete.
         */
        protected abstract boolean render() throws IOException;

        private CompletableFuture<NativeImage> pending;

        protected boolean capturePending(){return pending!=null;}
        void close(){
            var capture=pending;pending=null;
            if(capture!=null)capture.whenComplete((image,error)->{if(image!=null)image.close();});
        }
        protected @Nullable NativeImage pollScreenshot(){
            if(pending==null){pending=capture();return null;}
            if(!pending.isDone())return null;
            var result=pending;pending=null;
            return result.join();
        }
        private CompletableFuture<NativeImage> capture(){
            RenderSystem.assertOnRenderThread();
            var mc=Minecraft.getInstance();
            var device=RenderSystem.getDevice();
            int max=device.getDeviceInfo().limits().maxTextureSizeForFormat(com.mojang.renderpearl.api.GpuFormat.RGBA8_UNORM);
            if(resolution>max)throw new IllegalArgumentException("Resolution exceeds GPU limit "+max);
            var state=new TrackingItemStackRenderState();
            mc.getItemModelResolver().updateForTopItem(state,stack,ItemDisplayContext.GUI,mc.level,mc.player,0);
            if(state.isEmpty())return CompletableFuture.completedFuture(new NativeImage(resolution,resolution,true));
            var future=new CompletableFuture<NativeImage>();
            var oldProjection=RenderSystem.getProjectionMatrixBuffer();
            var oldProjectionType=RenderSystem.getProjectionType();
            var oldLights=RenderSystem.getShaderLights();
            var oldScissor=RenderSystem.getScissorStateForRenderTypeDraws();
            var atlas=new GuiItemAtlas(mc.gameRenderer.featureRenderDispatcher(),resolution,resolution);
            GpuBuffer buffer=null;
            boolean submitted=false;
            try{
                var slot=java.util.Objects.requireNonNull(atlas.getOrUpdate(state),"Item atlas allocation failed");
                var texture=slot.textureView().texture();
                buffer=device.createBuffer(()->"CCL item export readback",GpuBuffer.USAGE_MAP_READ|GpuBuffer.USAGE_COPY_DST,Math.multiplyExact(4L,Math.multiplyExact((long)resolution,resolution)));
                var readback=buffer;
                device.createCommandEncoder().copyTextureToBuffer(texture,readback,0,()->{
                    NativeImage image=null;
                    try(var mapped=readback.map(true,false)){
                        image=new NativeImage(resolution,resolution,false);
                        var bytes=mapped.data();
                        for(int y=0;y<resolution;y++)for(int x=0;x<resolution;x++){
                            int i=4*(x+y*resolution);
                            int r=bytes.get(i)&255,g=bytes.get(i+1)&255,b=bytes.get(i+2)&255,a=bytes.get(i+3)&255;
                            image.setPixelABGR(x,resolution-y-1,straightAlphaAbgr(r,g,b,a));
                        }
                        if(future.complete(image))image=null;
                    }catch(Throwable t){future.completeExceptionally(t);}
                    finally{try{if(image!=null)image.close();}finally{try{readback.close();}finally{atlas.close();}}}
                },0);
                submitted=true;
                return future;
            }finally{
                if(oldProjection!=null)RenderSystem.setProjectionMatrix(oldProjection,oldProjectionType);
                RenderSystem.setShaderLights(oldLights);
                if(oldScissor.enabled())RenderSystem.enableScissorForRenderTypeDraws(oldScissor.x(),oldScissor.y(),oldScissor.width(),oldScissor.height());
                else RenderSystem.disableScissorForRenderTypeDraws();
                if(!submitted){if(buffer!=null)buffer.close();atlas.close();}
            }
        }
    }

    private static class StaticRenderTask extends RenderTask {

        private StaticRenderTask(ItemStack stack, int resolution, Path path) {
            super(stack, resolution, path);
        }

        @Override
        protected boolean render() throws IOException {
            NativeImage captured=pollScreenshot();
            if(captured==null)return false;
            try (NativeImage image = captured) {
                image.writeToFile(IOUtils.makeParents(path));
            }
            return true;
        }
    }

    private static abstract class AnimatedRenderTask extends RenderTask {

        protected final int fps;
        protected final long targetDuration;

        protected final List<NativeImage> frames;
        protected final long frameDelay;

        protected long startTime = -1;
        protected long lastFrame;

        private @Nullable CompletableFuture<Void> finalizeTask;

        private AnimatedRenderTask(ItemStack stack, int resolution, Path path, int fps, int duration) {
            super(stack, resolution, path);
            if(fps<=0||duration<=0)throw new IllegalArgumentException("Invalid animation timing.");
            this.fps = fps;
            this.targetDuration = TimeUnit.SECONDS.toMillis(duration);

            frames = new ArrayList<>(Math.min(1024,Math.multiplyExact(duration,fps)));
            frameDelay = Math.max(1,1000L/fps);
        }

        @Override
        protected boolean render() {
            long currTime = System.currentTimeMillis();
            if (startTime == -1) {
                startTime = currTime;
            }
            if(capturePending()){
                NativeImage frame=pollScreenshot();
                if(frame==null)return false;
                frames.add(frame);
                LOGGER.info("Captured frame {}",frames.size());
            }
            if (startTime + targetDuration <= currTime && !frames.isEmpty()) {
                if (finalizeTask == null) {
                    finalizeTask = CompletableFuture.runAsync(() -> {
                        try {
                            serialize();
                        } catch (Throwable ex) {
                            LOGGER.error("Failed to serialize item render task.", ex);
                        } finally { closeFrames(); }
                    });
                }
                return finalizeTask.isDone();
            }

            // Wait more for next frame.
            if (lastFrame + frameDelay > currTime) return false;
            lastFrame = currTime;
            NativeImage frame=pollScreenshot();
            if(frame!=null)frames.add(frame);
            LOGGER.info("Captured frame {} / {}", frames.size(), ((targetDuration / 1000) * fps));
            return false;
        }

        private void closeFrames(){
            frames.forEach(NativeImage::close);
            frames.clear();
        }
        @Override void close(){
            super.close();
            // Once serialization starts, its worker owns the frames.
            if(finalizeTask==null)closeFrames();
        }

        protected abstract void serialize() throws IOException;
    }

    private static class GifRenderTask extends AnimatedRenderTask {

        private GifRenderTask(ItemStack stack, int resolution, Path path, int fps, int duration) {
            super(stack, resolution, path, fps, duration);
        }

        @Override
        protected void serialize() throws IOException {
            try (OutputStream os = Files.newOutputStream(IOUtils.makeParents(path))) {
                AnimatedGifEncoder encoder = new AnimatedGifEncoder();
                encoder.start(os);
                encoder.setDelay((int) frameDelay);
                encoder.setRepeat(0);  // Always repeat.
                encoder.setQuality(1); // Best quality possible.
                for (int i = 0; i < frames.size(); i++) {
                    LOGGER.info("Encoding Frame {} / {}", i + 1, frames.size());
                    NativeImage frame = frames.get(i);
                    try (frame) {
                        var bitmap=new java.awt.image.BufferedImage(frame.getWidth(),frame.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        bitmap.setRGB(0,0,frame.getWidth(),frame.getHeight(),frame.getPixels(),0,frame.getWidth());
                        encoder.addFrame(bitmap);
                    }
                }
                encoder.finish();
                LOGGER.info("Finished writing gif.");
            }
        }
    }

    private static class WebpRenderTask extends AnimatedRenderTask {

        public static final Supplier<@Nullable String> FFMPEG_BINARY = Suppliers.memoize(() -> {
            String ffmpeg = System.getProperty("ccl.ffmpeg", "ffmpeg" + (OperatingSystem.current().isWindows() ? ".exe" : ""));
            LOGGER.info("Probing for ffmpeg with {}", ffmpeg);
            try {
                int ret = runFfmpeg(List.of(ffmpeg, "-version"), LOGGER::info);
                if (ret == 0) {
                    LOGGER.info("ffmpeg available via {}", ffmpeg);
                    return ffmpeg;
                }
                LOGGER.error("Failed to find working ffmpeg. Got exit code: {}", ret);
            } catch (IOException ex) {
                LOGGER.error("Failed to find ffmpeg. Exception whilst running command.", ex);
            }
            return null;
        });

        public static boolean isFfmpegAvailable() {
            return FFMPEG_BINARY.get() != null;
        }

        private WebpRenderTask(ItemStack stack, int resolution, Path path, int fps, int duration) {
            super(stack, resolution, path, fps, duration);
        }

        @Override
        protected void serialize() throws IOException {
            Files.createDirectories(path.getParent());
            Path tempDir=Files.createTempDirectory(path.getParent(),".ccl-webp-");
            try {
            LOGGER.info("Dumping frames to temp directory..");
            int i = 0;
            for (NativeImage frame : frames) {
                try (frame) {
                    Path file = tempDir.resolve(i++ + ".png");
                    LOGGER.info("Dumping: {}", file);
                    frame.writeToFile(file);
                }
            }

            int ret = runFfmpeg(
                    List.of(
                            requireNonNull(FFMPEG_BINARY.get()),
                            "-y",
                            "-framerate", String.valueOf(fps),
                            "-i", tempDir.toAbsolutePath() + "/%d.png",
                            "-loop", "0",
                            "-lossless", "1",
                            "-compression_level", "0",
                            path.toAbsolutePath().toString()
                    ),
                    LOGGER::info
            );
            if (ret != 0) {
                throw new IOException("ffmpeg exited with exit code "+ret);
            } else {
                LOGGER.info("ffmpeg success!");
            }

            } finally {
                try(var files=Files.list(tempDir)){
                    for(var file:files.toList())Files.deleteIfExists(file);
                } finally {Files.deleteIfExists(tempDir);}
            }
        }

        private static int runFfmpeg(List<String> args, Consumer<String> out) throws IOException {
            ProcessBuilder pb = new ProcessBuilder(args);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                reader.lines().forEach(out);
            }
            proc.onExit().join();
            return proc.exitValue();
        }
    }
}
