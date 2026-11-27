package codechicken.lib.gui.modular.lib;

import codechicken.lib.gui.modular.sprite.ModAtlasHolder;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.*;
import java.util.*;
import java.util.stream.Stream;

public final class AtlasNativeSmoke {
    static final Identifier ATLAS=Identifier.parse("codechickenlib:textures/atlas/test.png");
    static final Identifier INFO=Identifier.parse("codechickenlib:gui");
    static final Identifier JSON=Identifier.parse("codechickenlib:atlases/gui.json");
    static final Identifier OWN=Identifier.parse("codechickenlib:textures/gui/a.png");
    static final Identifier OTHER=Identifier.parse("other:textures/gui/b.png");
    static final Identifier EXPLICIT=Identifier.parse("other:textures/special.png");

    public static void verify(){
        // Constructor and close must not require an active GPU before registration.
        try(var holder=new ModAtlasHolder("codechickenlib","textures/atlas/test.png","gui")){
            if(!holder.atlasLocation().equals(ATLAS))throw new IllegalStateException("Atlas identity lost");
        }
        var first=prepare(0xFF112233);
        try{
            var second=prepare(0xFFABCDEF);
            try{
                if(first.get(Identifier.parse("codechickenlib:gui/a")).getOriginalImage().getPixel(0,0)!=0xFF112233||
                   second.get(Identifier.parse("codechickenlib:gui/a")).getOriginalImage().getPixel(0,0)!=0xFFABCDEF)
                    throw new IllegalStateException("Reload used stale sprite pixels");
                if(second.get(Identifier.parse("other:gui/b"))!=null||
                   second.get(Identifier.parse("other:special"))==null||second.get(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation())==null)
                    throw new IllegalStateException("Atlas namespace or explicit cross-namespace lookup changed");
            }finally{second.values().forEach(net.minecraft.client.renderer.texture.SpriteContents::close);}
        }finally{first.values().forEach(net.minecraft.client.renderer.texture.SpriteContents::close);}
        try{
            var manager=Class.forName("net.minecraft.client.renderer.texture.TextureManager",false,AtlasNativeSmoke.class.getClassLoader());
            if(Arrays.stream(manager.getDeclaredMethods()).noneMatch(m->m.getName().contains("ccl$getTextures")))
                throw new IllegalStateException("Missing native texture ownership accessor");
        }catch(ClassNotFoundException e){throw new IllegalStateException(e);}
        System.out.println("CCL_ATLAS_SMOKE_PASS: two native CPU stitches, changed pixels, directory namespace, explicit cross-namespace and lazy holder");
    }

    static Map<Identifier,net.minecraft.client.renderer.texture.SpriteContents> prepare(int color){
        var pack=(PackResources)java.lang.reflect.Proxy.newProxyInstance(AtlasNativeSmoke.class.getClassLoader(),new Class[]{PackResources.class},(p,m,a)->{
            if(m.getName().equals("packId"))return "atlas-smoke";
            throw new UnsupportedOperationException(m.getName());
        });
        var image=new java.awt.image.BufferedImage(4,4,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<4;y++)for(int x=0;x<4;x++)image.setRGB(x,y,color);
        var bytes=new java.io.ByteArrayOutputStream();
        try{javax.imageio.ImageIO.write(image,"png",bytes);}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
        var png=new Resource(pack,()->new java.io.ByteArrayInputStream(bytes.toByteArray()));
        var json=new Resource(pack,()->new java.io.ByteArrayInputStream("""
            {"sources":[{"type":"directory","source":"gui","prefix":"gui/"},{"type":"single","resource":"other:special"}]}
            """.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        Map<Identifier,Resource> resources=Map.of(OWN,png,OTHER,png,EXPLICIT,png,JSON,json);
        ResourceManager manager=new ResourceManager(){
            public Set<String> getNamespaces(){return Set.of("codechickenlib","other");}
            public Optional<Resource> getResource(Identifier id){return Optional.ofNullable(resources.get(id));}
            public List<Resource> getResourceStack(Identifier id){return getResource(id).stream().toList();}
            public Map<Identifier,Resource> listResources(String path,Selector filter){
                var result=new HashMap<Identifier,Resource>();
                resources.forEach((id,res)->{if(id.getPath().startsWith(path+"/")&&filter.isIncluded(id))result.put(id,res);});
                return result;
            }
            public Map<Identifier,List<Resource>> listResourceStacks(String path,Selector filter){
                var result=new HashMap<Identifier,List<Resource>>();
                listResources(path,filter).forEach((id,res)->result.put(id,List.of(res)));return result;
            }
            public Stream<PackResources> listPacks(){return Stream.of(pack);}
        };
        var filtered=new ModAtlasHolder.ModResourceManager(manager,"codechickenlib");
        if(filtered.listResources("textures/gui",id->true).size()!=1||filtered.listResourceStacks("textures/gui",id->true).size()!=2)
            throw new IllegalStateException("Legacy stack visibility changed");
        // Native SpriteLoader.stitch queries live client Options, absent in clientData.
        // Exercise the same native source decoding and Stitcher at mip 0, no anisotropy.
        var loader=net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader.create(Set.of());
        var sources=net.minecraft.client.renderer.texture.atlas.SpriteSourceList.load(filtered,INFO).list(filtered,Set.of());
        var stitcher=new net.minecraft.client.renderer.texture.Stitcher<net.minecraft.client.renderer.texture.SpriteContents>(1024,1024,0,0);
        var result=new HashMap<Identifier,net.minecraft.client.renderer.texture.SpriteContents>();
        try{
            for(var source:sources){
                var sprite=source.get(loader);
                if(sprite!=null){result.put(sprite.name(),sprite);stitcher.registerSprite(sprite);}
            }
            stitcher.stitch();
            var stitched=new HashSet<Identifier>();
            stitcher.gatherSprites((sprite,x,y,padding)->stitched.add(sprite.name()));
            if(!stitched.equals(result.keySet()))throw new IllegalStateException("Stitched sprite set changed");
            return result;
        }catch(Throwable t){result.values().forEach(net.minecraft.client.renderer.texture.SpriteContents::close);throw t;}
    }
}