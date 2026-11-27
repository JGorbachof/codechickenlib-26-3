package codechicken.lib.render.shader;

import codechicken.lib.render.shader.GlslProcessor.ProcessedShader;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;


import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.util.Util;

import net.minecraft.resources.Identifier;
import net.minecraft.server.ChainedJsonException;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.io.IOException;
import java.util.*;

/**
 * A Slightly extended ShaderInstance supporting:
 * - Better Uniform caching.
 * - All OpenGL Uniform types.
 * - Global Uniform callbacks.
 * - Better import/includes in shaders.
 * <p>
 * Created by covers1624 on 8/4/22.
 */
public class CCShaderInstance implements AutoCloseable {

    private final List<Runnable> applyCallbacks = new LinkedList<>();
    private final Map<String,CCUniform> uniforms=new LinkedHashMap<>();
    private final VertexFormat format;
    private final Identifier name;
    private final int program;
    private final Map<String,Integer> samplers=new LinkedHashMap<>();
    private final Map<String,Integer> samplerTextures=new LinkedHashMap<>();
    private int previousProgram;
    private int previousActiveTexture;
    private final Map<Integer,Integer> previousTextures=new LinkedHashMap<>();
    private boolean applied,closed,applying;

    /**
     * Legacy standalone GLSL shader. Not a RenderPipeline: only use in an explicitly
     * owned OpenGL draw scope, never inside a RenderPearl-managed render pass.
     */
    protected CCShaderInstance(ResourceProvider provider,Identifier loc,VertexFormat format) throws IOException {
        this.name=loc;this.format=format;
        requireOpenGL();
        Identifier jsonId=Identifier.fromNamespaceAndPath(loc.getNamespace(),"shaders/core/"+loc.getPath()+".json");
        JsonObject json;
        try(var reader=provider.getResourceOrThrow(jsonId).openAsReader()){
            json=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
        }
        int vertex=0,fragment=0,linked=0;
        try{
            vertex=compileProgram(provider,GL20.GL_VERTEX_SHADER,Identifier.parse(GsonHelper.getAsString(json,"vertex")),".vsh");
            fragment=compileProgram(provider,GL20.GL_FRAGMENT_SHADER,Identifier.parse(GsonHelper.getAsString(json,"fragment")),".fsh");
            linked=GL20.glCreateProgram();
            if(linked==0) throw new IOException("Could not allocate shader "+loc);
            GL20.glAttachShader(linked,vertex);GL20.glAttachShader(linked,fragment);
            int index=0;
            for(var element:format.getElements()) GL20.glBindAttribLocation(linked,index++,element.name());
            GL20.glLinkProgram(linked);
            if(GL20.glGetProgrami(linked,GL20.GL_LINK_STATUS)==GL11.GL_FALSE) throw new IOException(GL20.glGetProgramInfoLog(linked));
            if(json.has("uniforms")) for(var uniform:json.getAsJsonArray("uniforms")) parseUniformNode(uniform);
            for(var uniform:uniforms.values()) uniform.setLocation(GL20.glGetUniformLocation(linked,uniform.getName()));
            if(json.has("samplers")) for(var sampler:json.getAsJsonArray("samplers")){
                String samplerName=GsonHelper.getAsString(sampler.getAsJsonObject(),"name");
                samplers.put(samplerName,GL20.glGetUniformLocation(linked,samplerName));
            }
            program=linked;linked=0;
        }finally{
            if(vertex!=0) GL20.glDeleteShader(vertex);
            if(fragment!=0) GL20.glDeleteShader(fragment);
            if(linked!=0) GL20.glDeleteProgram(linked);
        }
    }
    static void requireOpenGL(){
        com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();
        if(org.lwjgl.opengl.GL.getCapabilities()==null) throw new IllegalStateException("Legacy shader requires a current OpenGL context.");
    }
    public static CCShaderInstance create(ResourceProvider provider,Identifier loc,VertexFormat format){
        try{return new CCShaderInstance(provider,loc,format);}
        catch(IOException e){throw new java.io.UncheckedIOException("Failed to initialize "+loc,e);}
    }
    public Identifier getName(){return name;}
    public VertexFormat getVertexFormat(){return format;}
    public int getId(){return program;}
    public void onApply(Runnable callback){applyCallbacks.add(Objects.requireNonNull(callback));}
    public @Nullable CCUniform getUniform(String name){return uniforms.get(name);}
    public void setSampler(String name,int texture){
        if(!samplers.containsKey(name)) throw new IllegalArgumentException("Unknown sampler "+name);
        samplerTextures.put(name,texture);
    }
    public void apply(){
        requireOpenGL();
        if(applied||closed) throw new IllegalStateException("Shader already applied or closed.");
        previousProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        previousActiveTexture=GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
        GL20.glUseProgram(program);
        applied=true;applying=true;
        try{
            List.copyOf(applyCallbacks).forEach(Runnable::run);
            uniforms.values().forEach(CCUniform::upload);
            int unit=0;
            for(var entry:samplers.entrySet()){
                if(entry.getValue()<0)continue;
                if(!samplerTextures.containsKey(entry.getKey())) throw new IllegalStateException("Unbound sampler "+entry.getKey());
                if(unit>=GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS))throw new IllegalStateException("Too many samplers.");
                org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0+unit);
                previousTextures.put(unit,GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,samplerTextures.get(entry.getKey()));
                GL20.glUniform1i(entry.getValue(),unit++);
            }
        }catch(Throwable t){applying=false;clear();throw t;}
        finally{applying=false;}
    }
    public void clear(){
        if(applying)throw new IllegalStateException("Cannot clear during shader apply callback.");
        if(!applied)return;
        requireOpenGL();
        previousTextures.forEach((unit,texture)->{
            org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0+unit);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
        });
        previousTextures.clear();
        org.lwjgl.opengl.GL13.glActiveTexture(previousActiveTexture);
        GL20.glUseProgram(previousProgram);applied=false;
    }
    @Override public void close(){
        if(closed)return;
        if(applying)throw new IllegalStateException("Cannot close during shader apply callback.");
        requireOpenGL();clear();GL20.glDeleteProgram(program);closed=true;
    }

    protected void parseUniformNode(JsonElement json) throws ChainedJsonException {
        JsonObject obj = GsonHelper.convertToJsonObject(json, "uniform");
        String name = GsonHelper.getAsString(obj, "name");
        String typeStr = GsonHelper.getAsString(obj, "type");
        UniformType type = UniformType.parse(typeStr);
        if (type == null) {
            throw new ChainedJsonException("Invalid type '%s'. See UniformType enum. All vanilla types supported.".formatted(typeStr));
        }

        int count = GsonHelper.getAsInt(obj, "count");
        // Hack for vanilla compat. Vanilla specifies 'float, count 4' for vec4, we need ot to be a vec4
        switch (type) {
            case FLOAT -> {
                switch(count) {
                    case 2 -> type = UniformType.VEC2;
                    case 3 -> type = UniformType.VEC3;
                    case 4 -> type = UniformType.VEC4;
                }
            }
            case INT -> {
                switch(count) {
                    case 2 -> type = UniformType.I_VEC2;
                    case 3 -> type = UniformType.I_VEC3;
                    case 4 -> type = UniformType.I_VEC4;
                }
            }
            case U_INT -> {
                switch(count) {
                    case 2 -> type = UniformType.U_VEC2;
                    case 3 -> type = UniformType.U_VEC3;
                    case 4 -> type = UniformType.U_VEC4;
                }
            }
        }
        CCUniform uniform = CCUniform.makeUniform(name, type, count);

        JsonArray jsonValues = GsonHelper.getAsJsonArray(obj, "values");
        if (jsonValues.size() != count && jsonValues.size() > 1) {
            throw new ChainedJsonException("Invalid amount of values specified (expected " + count + ", found " + jsonValues.size() + ")");
        }
        switch (type.getCarrier()) {
            case INT, U_INT -> uniform.glUniformI(parseInts(count, jsonValues));
            case FLOAT, MATRIX -> uniform.glUniformF(false, parseFloats(count, jsonValues));
            case DOUBLE, D_MATRIX -> uniform.glUniformD(false, parseDoubles(count, jsonValues));
        }
        if(uniforms.putIfAbsent(name,uniform)!=null)throw new ChainedJsonException("Duplicate uniform "+name);
    }

    private static int compileProgram(ResourceProvider provider,int shaderType,Identifier loc,String extension) throws IOException {
        var adjusted=Identifier.fromNamespaceAndPath(loc.getNamespace(),"shaders/core/"+loc.getPath()+extension);
        var source=new GlslProcessor(provider,adjusted).process();
        int shader=GL20.glCreateShader(shaderType);
        if(shader==0)throw new IOException("Could not allocate "+adjusted);
        GL20.glShaderSource(shader,source.processedSource());
        GL20.glCompileShader(shader);
        if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==GL11.GL_FALSE){
            String info=GL20.glGetShaderInfoLog(shader);GL20.glDeleteShader(shader);
            throw new IOException("Couldn't compile "+adjusted+": "+info);
        }
        return shader;
    }

    private static float[] parseFloats(int count, JsonArray jsonValues) throws ChainedJsonException {
        int i = 0;
        float[] values = new float[Math.max(count, 16)];
        for (JsonElement jsonValue : jsonValues) {
            try {
                values[i++] = GsonHelper.convertToFloat(jsonValue, "value");
            } catch (Exception ex) {
                ChainedJsonException chainedjsonexception = ChainedJsonException.forException(ex);
                chainedjsonexception.prependJsonKey("values[" + i + "]");
                throw chainedjsonexception;
            }
        }

        // If only one value is specified, copy to all in array.
        if (count > 1 && jsonValues.size() == 1) {
            Arrays.fill(values, 1, values.length, values[0]);
        }
        return Arrays.copyOfRange(values, 0, count);
    }

    private static int[] parseInts(int count, JsonArray jsonValues) throws ChainedJsonException {
        int i = 0;
        int[] values = new int[Math.max(count, 16)];
        for (JsonElement jsonValue : jsonValues) {
            try {
                values[i++] = GsonHelper.convertToInt(jsonValue, "value");
            } catch (Exception ex) {
                ChainedJsonException chainedjsonexception = ChainedJsonException.forException(ex);
                chainedjsonexception.prependJsonKey("values[" + i + "]");
                throw chainedjsonexception;
            }
        }

        // If only one value is specified, copy to all in array.
        if (count > 1 && jsonValues.size() == 1) {
            Arrays.fill(values, 1, values.length, values[0]);
        }
        return Arrays.copyOfRange(values, 0, count);
    }

    private static double[] parseDoubles(int count, JsonArray jsonValues) throws ChainedJsonException {
        int i = 0;
        double[] values = new double[Math.max(count, 16)];
        for (JsonElement jsonValue : jsonValues) {
            try {
                values[i++] = GsonHelper.convertToDouble(jsonValue, "value");
            } catch (Exception ex) {
                ChainedJsonException chainedjsonexception = ChainedJsonException.forException(ex);
                chainedjsonexception.prependJsonKey("values[" + i + "]");
                throw chainedjsonexception;
            }
        }

        // If only one value is specified, copy to all in array.
        if (count > 1 && jsonValues.size() == 1) {
            Arrays.fill(values, 1, values.length, values[0]);
        }
        return Arrays.copyOfRange(values, 0, count);
    }
}
