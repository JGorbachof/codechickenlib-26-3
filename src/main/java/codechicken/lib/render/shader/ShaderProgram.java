package codechicken.lib.render.shader;

import com.google.common.collect.ImmutableList;
import net.covers1624.quack.collection.FastStream;
import net.covers1624.quack.collection.StreamableIterable;

import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * A ShaderProgram.
 * <p>
 * As of 1.18.2, you will probably want to use {@link CCShaderInstance} instead as an extension
 * to Vanilla's {@link ShaderInstance}.
 * <p>
 * You probably want {@link ShaderProgramBuilder} to construct a ShaderProgram.
 * it should be noted, that a ShaderProgram is a {@link ResourceManagerReloadListener},
 * its recommended that you ensure this is registered to {@link ReloadableResourceManager}
 * to ensure {@link ShaderObject}s are re loaded properly when Resources are reloaded.
 * <p>
 * Created by covers1624 on 24/5/20.
 */
public class ShaderProgram implements ResourceManagerReloadListener, AutoCloseable {

    private final List<ShaderObject> shaders;
    private final Map<String, CCUniform> uniforms;
    @Nullable
    private final Runnable applyCallback;
    private int programId = -1;
    private boolean bound;
    private int previousProgram;
    private final ShaderLinkState linkState=new ShaderLinkState();
    private boolean applying,compiling;
    private volatile boolean closed;

    ShaderProgram(Collection<ShaderObject> shaders, Collection<UniformPair> uniforms, @Nullable Runnable applyCallback) {
        this.shaders = ImmutableList.copyOf(shaders);
        this.uniforms = FastStream.of(uniforms)
                .toImmutableMap(UniformPair::name, e -> CCUniform.makeUniform(e.name(), e.type(), e.type().getSize()));
        this.applyCallback = applyCallback;
    }

    /**
     * Gets all {@link ShaderObject}s that make up this {@link ShaderProgram}.
     *
     * @return The {@link ShaderObject}s.
     */
    public List<ShaderObject> getShaders() {
        return shaders;
    }

    /**
     * Get all {@link UniformPair}s exposed by this shader.
     *
     * @return The uniforms.
     */
    public Map<String, CCUniform> getUniforms() {
        return uniforms;
    }

    /**
     * Get a {@link CCUniform} from this {@link ShaderProgram}.
     *
     * @param name The name of the Uniform.
     * @return the {@link CCUniform}.
     */
    @Nullable
    public CCUniform getUniform(String name) {
        return uniforms.get(name);
    }

    /**
     * Gets the GL {@link ShaderProgram} id for this shader.
     * Might not be initialized until {@link #use()} is called once.
     *
     * @return The id, -1 if not initialized.
     */
    public int getProgramId() {
        return programId;
    }

    /**
     * Binds this shader for use, Lazily allocates, links
     * and compiles all {@link ShaderObject}s.
     */
    public void use() {
        CCShaderInstance.requireOpenGL();
        if (bound) {
            throw new IllegalStateException("Already bound.");
        }
        compile();
        previousProgram=org.lwjgl.opengl.GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL20.glUseProgram(programId);
        bound=true;applying=true;
        try {
            if(applyCallback!=null)applyCallback.run();
            uniforms.values().forEach(CCUniform::upload);
        }catch(Throwable t){bound=false;GL20.glUseProgram(previousProgram);throw t;}
        finally{applying=false;}
    }

    /**
     * Forces the ShaderProgram to compile and link.
     * <p>
     * This will happen automatically when calling {@link #use()}, however,
     * it may be required to call this ahead of time in some cases.
     * <p>
     * Be sure to only call this when you have GL context.
     */
    public void compile() {
        CCShaderInstance.requireOpenGL();
        if(closed||bound||compiling)throw new IllegalStateException("Cannot compile a closed, bound or compiling program.");
        compiling=true;
        int next=0;
        try{
            if(shaders.stream().anyMatch(ShaderObject::isDirty))linkState.invalidate();
            long request=linkState.request();
            for(var shader:shaders)shader.alloc();
            int[] ids=shaders.stream().mapToInt(ShaderObject::getShaderID).toArray();
            long[] revisions=shaders.stream().mapToLong(ShaderObject::compilationRevision).toArray();
            if(programId!=-1&&!linkState.needsLink(request,ids,revisions))return;
            // Keep a retry pending even when an in-place shader compilation cleared dirty.
            linkState.beginLink();
            next=GL20.glCreateProgram();
            if(next==0)throw new IllegalStateException("Program allocation failed.");
            for(int id:ids)GL20.glAttachShader(next,id);
            GL20.glLinkProgram(next);
            if(GL20.glGetProgrami(next,GL20.GL_LINK_STATUS)==GL11.GL_FALSE)
                throw new IllegalStateException("Shader linkage failed: "+GL20.glGetProgramInfoLog(next));
            for(var shader:shaders)shader.onLink(next);
            var locations=new java.util.LinkedHashMap<CCUniform,Integer>();
            for(var uniform:uniforms.values())locations.put(uniform,GL20.glGetUniformLocation(next,uniform.getName()));
            int old=programId;
            programId=next;next=0;
            locations.forEach(CCUniform::setLocation);
            linkState.commit(request,ids,revisions);
            if(old!=-1)GL20.glDeleteProgram(old);
        }finally{
            compiling=false;
            if(next!=0)GL20.glDeleteProgram(next);
        }
    }

    /** Shader objects can be shared; their owners close them separately. */
    @Override public void close(){
        if(closed)return;
        if(applying||compiling)throw new IllegalStateException("Cannot close during shader callback.");
        CCShaderInstance.requireOpenGL();
        if(bound)release();
        if(programId!=-1)GL20.glDeleteProgram(programId);
        programId=-1;closed=true;
    }

    /**
     * Releases this shader.
     */
    public void release() {
        if(applying)throw new IllegalStateException("Cannot release during shader apply callback.");
        CCShaderInstance.requireOpenGL();
        if (!bound) {
            throw new IllegalStateException("Not bound");
        }
        bound = false;
        GL20.glUseProgram(previousProgram);
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        if(closed)return;
        linkState.invalidate();
        for (ShaderObject shader : shaders) {
            if (shader instanceof ResourceManagerReloadListener) {
                ((ResourceManagerReloadListener) shader).onResourceManagerReload(resourceManager);
            }
        }
    }
}
