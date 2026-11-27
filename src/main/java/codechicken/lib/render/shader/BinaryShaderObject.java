package codechicken.lib.render.shader;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL46;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.function.Consumer;

/**
 * Allows using pre-compiled shader binaries with {@link ShaderProgram}.
 * <p>
 * Created by KitsuneAlex on 18/11/21.
 */
public class BinaryShaderObject extends NamedShaderObject implements ResourceManagerReloadListener, AutoCloseable {

    private static final Logger LOGGER = LogManager.getLogger();

    private final Identifier asset;
    private final BinaryType binaryType;
    private final String entryPoint;

    private final Consumer<ConstantCache> specializationCallback;
    private volatile boolean dirty = false;
    private net.minecraft.server.packs.resources.ResourceProvider provider;
    private int shaderId = -1;

    public BinaryShaderObject(String name, Identifier asset, ShaderType type, BinaryType binaryType, String entryPoint, Collection<UniformPair> uniforms, Consumer<ConstantCache> specializationCallback) {
        super(name, type, uniforms);
        this.asset = asset;
        this.binaryType = binaryType;
        this.entryPoint = entryPoint;
        this.specializationCallback = specializationCallback;
    }

    private volatile long compilationRevision;
    @Override public long compilationRevision(){return compilationRevision;}

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public synchronized void alloc() {
        CCShaderInstance.requireOpenGL();
        if(!dirty&&shaderId!=-1)return;
        int next=GL20.glCreateShader(getShaderType().getGLCode());
        if(next==0)throw new IllegalStateException("Shader allocation failed.");
        try{
            byte[] source=getRawSource();
            if(source.length==0)throw new IllegalStateException("Empty shader binary "+asset);
            ByteBuffer buffer=BufferUtils.createByteBuffer(source.length).order(ByteOrder.nativeOrder());
            buffer.put(source).flip();
            GL46.glShaderBinary(new int[]{next},binaryType.getGLCode(),buffer);
            var constants=new ShaderConstantCache();
            specializationCallback.accept(constants);
            GL46.glSpecializeShader(next,entryPoint,constants.getIndices(),constants.getValues());
            if(GL20.glGetShaderi(next,GL20.GL_COMPILE_STATUS)==GL11.GL_FALSE)
                throw new IllegalStateException("Shader specialization failed: "+GL20.glGetShaderInfoLog(next));
            int old=shaderId;shaderId=next;next=0;dirty=false;compilationRevision++;
            if(old!=-1)GL20.glDeleteShader(old);
        }finally{if(next!=0)GL20.glDeleteShader(next);}
    }

    @Override public synchronized void close(){
        if(shaderId==-1)return;
        CCShaderInstance.requireOpenGL();
        GL20.glDeleteShader(shaderId);shaderId=-1;dirty=true;
    }

    @Override
    public int getShaderID() {
        return shaderId;
    }

    @Override
    public void onLink(int programId) {
    }

    @Override
    public synchronized void onResourceManagerReload(ResourceManager resourceManager) {
        provider=java.util.Objects.requireNonNull(resourceManager);
        dirty=true;
    }

    synchronized byte[] getRawSource() {
        var current=provider!=null?provider:Minecraft.getInstance().getResourceManager();
        try (InputStream input=current.getResourceOrThrow(asset).open()){
            return input.readAllBytes();
        }catch(java.io.IOException e){
            throw new java.io.UncheckedIOException("Failed to read shader binary "+asset,e);
        }
    }
}