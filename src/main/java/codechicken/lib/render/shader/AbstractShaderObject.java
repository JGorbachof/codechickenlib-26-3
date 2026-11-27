package codechicken.lib.render.shader;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.util.Collection;

/**
 * Created by covers1624 on 24/5/20.
 */
public abstract class AbstractShaderObject extends NamedShaderObject implements AutoCloseable {

    protected int shaderId = -1;
    protected volatile boolean dirty;

    protected AbstractShaderObject(String name, ShaderType type, Collection<UniformPair> uniforms) {
        super(name, type, uniforms);
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
            GL20.glShaderSource(next,getSource());
            GL20.glCompileShader(next);
            if(GL20.glGetShaderi(next,GL20.GL_COMPILE_STATUS)==GL11.GL_FALSE)
                throw new IllegalStateException("Shader compilation failed: "+GL20.glGetShaderInfoLog(next));
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

    protected abstract String getSource();
}
