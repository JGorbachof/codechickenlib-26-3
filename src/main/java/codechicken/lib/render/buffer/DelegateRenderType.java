package codechicken.lib.render.buffer;

import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import java.util.Objects;

/** Composition of a native render type and its prepared pipeline. */
public class DelegateRenderType {
    protected final RenderType parent;
    public DelegateRenderType(RenderType parent){this.parent=Objects.requireNonNull(parent);}
    public DelegateRenderType(RenderType parent,VertexFormat format){
        this(parent);
        if(!parent.format().equals(format))throw new IllegalArgumentException("A different vertex format requires a matching RenderPipeline.");
    }
    public RenderType renderType(){return parent;}
    public VertexFormat format(){return parent.format();}
    public PreparedRenderType prepare(){return parent.prepare();}
}
