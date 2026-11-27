package codechicken.lib.render.shader;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;

/**
 * Created by covers1624 on 24/5/20.
 */
public class AssetShaderObject extends AbstractShaderObject implements ResourceManagerReloadListener {

    private static final Logger LOGGER = LogManager.getLogger();

    private final Identifier asset;
    @Nullable
    private String source;
    private net.minecraft.server.packs.resources.ResourceProvider provider;

    public AssetShaderObject(String name, ShaderType type, Collection<UniformPair> uniforms, Identifier asset) {
        this(name,type,uniforms,asset,null);
    }

    public AssetShaderObject(String name,ShaderType type,Collection<UniformPair> uniforms,Identifier asset,@Nullable net.minecraft.server.packs.resources.ResourceProvider provider){
        super(name,type,uniforms);
        this.asset=Objects.requireNonNull(asset);
        this.provider=provider;
    }

    @Override
    protected synchronized String getSource() {
        if (source == null) {
            source = (provider==null?new GlslProcessor(asset):new GlslProcessor(provider,asset)).process().processedSource();
        }

        return source;
    }

    @Override
    public synchronized void onResourceManagerReload(ResourceManager resourceManager) {
        provider=Objects.requireNonNull(resourceManager);
        source = null;
        dirty = true;
    }

}
