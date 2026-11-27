package codechicken.lib.gui.modular.sprite;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Created by brandon3055 on 20/08/2023
 */
public class ModAtlasHolder implements PreparableReloadListener, AutoCloseable {

    private TextureAtlas textureAtlas;
    private TextureManager registeredManager;
    private final Identifier atlasLocation;
    private final Identifier atlasInfoLocation;
    private final String modid;

    /**
     * Defines a mod texture atlas.
     * Must be registered as a resource reload listener via RegisterClientReloadListenersEvent
     * This is all that is needed to create a custom texture atlas.
     *
     * @param modid             The mod id of the mod registering this atlas.
     * @param atlasLocation     The texture atlas location. e.g. "textures/atlas/gui.png" (Will have the modid: prefix added automatically)
     * @param atlasInfoLocation The path to the atlas json file relative to modid:atlases/
     *                          e.g. "gui" will point to modid:atlases/gui.json
     */
    public ModAtlasHolder(String modid, String atlasLocation, String atlasInfoLocation) {
        this.atlasInfoLocation = Identifier.fromNamespaceAndPath(modid, atlasInfoLocation);
        this.atlasLocation = Identifier.fromNamespaceAndPath(modid, atlasLocation);
        // GPU allocation is deferred until the native reload listener is registered.
        this.modid = modid;
    }

    public synchronized void init() {
        var manager=Minecraft.getInstance().getTextureManager();
        if(textureAtlas!=null&&registeredManager==manager)return;
        if(textureAtlas!=null)close();
        var atlas=new TextureAtlas(atlasLocation);
        manager.register(atlasLocation,atlas);
        textureAtlas=atlas;
        registeredManager=manager;
    }

    public Identifier atlasLocation(){return atlasLocation;}

    public synchronized TextureAtlasSprite getSprite(Identifier id){
        if(textureAtlas==null)throw new IllegalStateException("Atlas not initialized: "+atlasLocation);
        return textureAtlas.getSprite(id);
    }

    @Override
    public synchronized CompletableFuture<Void> reload(SharedState state,Executor prepareExecutor,PreparationBarrier barrier,Executor applyExecutor){
        var atlas=Objects.requireNonNull(textureAtlas,"Atlas not initialized: "+atlasLocation);
        return SpriteLoader.create(atlas)
            .loadAndStitch(new ModResourceManager(state.resourceManager(),modid),atlasInfoLocation,0,prepareExecutor,Set.of())
            .thenCompose(preparations->{
                var handedOff=new java.util.concurrent.atomic.AtomicBoolean();
                return preparations.readyForUpload().thenApply(ignored->preparations)
                    .thenCompose(barrier::wait).thenAcceptAsync(prepared->{
                        synchronized(this){
                            if(textureAtlas!=atlas)throw new IllegalStateException("Atlas replaced during reload: "+atlasLocation);
                            handedOff.set(true);
                            atlas.upload(prepared);
                        }
                    },applyExecutor).whenComplete((v,error)->{
                        if(error!=null&&!handedOff.get())preparations.regions().values().forEach(TextureAtlasSprite::close);
                    });
            });
    }

    @Override
    public synchronized void close(){
        var atlas=textureAtlas;
        if(atlas==null)return;
        var manager=registeredManager;
        textureAtlas=null;registeredManager=null;
        if(manager!=null&&((codechicken.lib.internal.mixin.TextureManagerAccessor)manager).ccl$getTextures().get(atlasLocation)==atlas)
            manager.release(atlasLocation);
        // If the manager replaced or released it, the native manager already closed it.
    }

    public static class ModResourceManager implements ResourceManager {

        private final ResourceManager wrapped;
        private final String modid;

        public ModResourceManager(ResourceManager wrapped, String modid) {
            this.wrapped = wrapped;
            this.modid = modid;
        }

        @Override
        public Map<Identifier, Resource> listResources(String pPath, ResourceManager.Selector pFilter) {
            return wrapped.listResources(pPath, e -> pFilter.isIncluded(e) && e.getNamespace().equals(modid));
        }

        //@formatter:off
        @Override public Set<String> getNamespaces() { return wrapped.getNamespaces(); }
        @Override public List<Resource> getResourceStack(Identifier pLocation) { return wrapped.getResourceStack(pLocation); }
        @Override public Map<Identifier, List<Resource>> listResourceStacks(String pPath, ResourceManager.Selector pFilter) { return wrapped.listResourceStacks(pPath, pFilter); }
        @Override public Stream<PackResources> listPacks() { return wrapped.listPacks(); }
        @Override public Optional<Resource> getResource(Identifier pLocation) { return wrapped.getResource(pLocation); }
        //@formatter:on
    }
}
