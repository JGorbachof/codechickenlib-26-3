package codechicken.lib.render.shader;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ShaderReloadTests {
    private static Identifier id(String path){return Identifier.parse("test:"+path);}
    private static ResourceManager resources(Map<Identifier,String> data){
        PackResources pack=(PackResources)Proxy.newProxyInstance(PackResources.class.getClassLoader(),new Class[]{PackResources.class},
                (proxy,method,args)->{if(method.getName().equals("packId"))return "test-pack";throw new UnsupportedOperationException(method.getName());});
        return (ResourceManager)Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),new Class[]{ResourceManager.class},
                (proxy,method,args)->{
                    if(method.getName().equals("getResource")){
                        var value=data.get(args[0]);
                        return value==null?Optional.empty():Optional.of(new Resource(pack,()->new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8))));
                    }
                    if(method.isDefault())return java.lang.reflect.InvocationHandler.invokeDefault(proxy,method,args);
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test void processorCanBeReusedAndRetainsDeclaredDependencyOrder(){
        Identifier main=id("main"),a=id("shaders/include/a"),b=id("shaders/include/b"),common=id("shaders/include/common");
        var data=new LinkedHashMap<Identifier,String>();
        data.put(main,"#version 150\n#moj_import <test:a>\n#moj_import <test:b>\nvoid main(){}");
        data.put(a,"#moj_import <test:common>\nfloat a=common;");
        data.put(b,"#moj_import <test:common>\nfloat b=a;");
        data.put(common,"float common=1;");
        var processor=new GlslProcessor(resources(data),main);
        var first=processor.process();
        assertEquals(List.of(common,a,b,main),first.order());
        assertEquals(first,processor.process());
        assertEquals(1,first.processedSource().split("float common=1;",-1).length-1);
        data.put(common,"float common=2;");
        assertTrue(processor.process().processedSource().contains("float common=2;"));
    }

    @Test void cyclicImportsFailClearly(){
        Identifier main=id("main"),a=id("shaders/include/a");
        var processor=new GlslProcessor(resources(Map.of(main,"#version 150\n#moj_import <test:a>",a,"#moj_import \"test:main\"")),main);
        assertTrue(assertThrows(IllegalArgumentException.class,processor::process).getMessage().contains("Cyclic shader imports"));
    }

    @Test void assetAndBinaryReloadUseSuppliedResources(){
        var main=id("main");
        var first=resources(Map.of(main,"#version 150\nfloat first;"));
        var second=resources(Map.of(main,"#version 150\nfloat second;"));
        var asset=new AssetShaderObject("a",ShaderObject.StandardShaderType.VERTEX,List.of(),main,first);
        assertTrue(asset.getSource().contains("first"));
        asset.onResourceManagerReload(second);
        assertTrue(asset.isDirty());
        assertTrue(asset.getSource().contains("second"));
        var binary=new BinaryShaderObject("b",main,ShaderObject.StandardShaderType.VERTEX,BinaryType.SPIR_V,"main",List.of(),c->{});
        binary.onResourceManagerReload(first);
        assertTrue(new String(binary.getRawSource(),StandardCharsets.UTF_8).contains("first"));
        binary.onResourceManagerReload(second);
        assertTrue(new String(binary.getRawSource(),StandardCharsets.UTF_8).contains("second"));
        binary.onResourceManagerReload(resources(Map.of()));
        assertThrows(java.io.UncheckedIOException.class,binary::getRawSource);
    }

    @Test void failedShaderBuilderDoesNotPolluteUniforms(){
        var builder=ShaderProgramBuilder.builder();
        assertThrows(IllegalStateException.class,()->builder.addShader("invalid",b->b.uniform("orphan",UniformType.FLOAT)));
        assertTrue(builder.build().getUniforms().isEmpty());
        builder.addShader("valid",b->b.type(ShaderObject.StandardShaderType.VERTEX).source("source").uniform("orphan",UniformType.INT));
        assertEquals(UniformType.INT,builder.build().getUniform("orphan").getUniformType());
    }
    @Test void relinkRequestsSurviveFailedLinkAndConcurrentInvalidation(){
        var state=new ShaderLinkState();
        int[] ids={12,34};long[] revisions={1,1};
        assertTrue(state.needsLink(state.request(),ids,revisions));
        state.commit(state.request(),ids,revisions);
        assertFalse(state.needsLink(state.request(),ids,revisions));
        state.beginLink();
        long attempt=state.request();
        assertTrue(state.needsLink(attempt,ids,revisions));
        // Failed link: no commit, retry even though shader ids and revisions unchanged.
        assertTrue(state.needsLink(state.request(),ids,revisions));
        state.invalidate();
        state.commit(attempt,ids,revisions);
        assertTrue(state.needsLink(state.request(),ids,revisions));
        state.commit(state.request(),ids,revisions);
        assertTrue(state.needsLink(state.request(),ids,new long[]{2,1}));
        assertTrue(state.needsLink(state.request(),new int[]{12,35},revisions));
    }
    @Test void nativeIncludesRetainConditionalPositionAndHeaderGuards(){
        Identifier main=id("main"),header=id("shaders/include/header.glsl"),nested=id("shaders/include/nested.glsl");
        var data=Map.of(main,"#version 330\n#extension GL_ARB_separate_shader_objects : require\n#if FIRST\n#include <test:header.glsl>\n#endif\n#if SECOND\n#include <test:header.glsl>\n#endif\n/*\n#include <test:missing.glsl>\n*/\nvoid main(){}",
            header,"#ifndef HEADER\n#define HEADER\n#include <test:nested.glsl>\nfloat h=1;\n#endif",
            nested,"float n=2;");
        var result=new GlslProcessor(resources(data),main).process();
        var source=result.processedSource();
        assertEquals(List.of(nested,header,main),result.order());
        assertTrue(source.indexOf("#extension")<source.indexOf("float n=2;"));
        assertTrue(source.indexOf("#if FIRST")<source.indexOf("float n=2;"));
        assertEquals(2,source.split("float n=2;",-1).length-1);
        assertTrue(source.contains("/*\n#include <test:missing.glsl>\n*/"));
        assertFalse(source.contains("#include <test:header.glsl>"));
    }
    @Test void nativeIncludeCyclesAndVersionedHeadersReject(){
        Identifier main=id("main"),header=id("shaders/include/header.glsl");
        var text="#version 330\n#include <test:header.glsl>";
        assertThrows(IllegalArgumentException.class,()->new GlslProcessor(resources(Map.of(main,text,header,"#include <test:header.glsl>")),main).process());
        assertThrows(IllegalArgumentException.class,()->new GlslProcessor(resources(Map.of(main,text,header,"#version 330")),main).process());
    }
}