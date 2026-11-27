package codechicken.lib.render;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;

import codechicken.lib.vec.Quat;
import codechicken.lib.vec.Vector3;
import codechicken.lib.vec.Vertex5;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import static codechicken.lib.math.MathHelper.phi;

public class CCModelLibrary {

    public static final CCModel icosahedron4;
    public static final CCModel icosahedron7;

    private static int i;

    static {
        Vector3[] verts = new Vector3[12];

        verts[0] = new Vector3(-1, phi, 0);
        verts[1] = new Vector3(1, phi, 0);
        verts[2] = new Vector3(1, -phi, 0);
        verts[3] = new Vector3(-1, -phi, 0);

        verts[4] = new Vector3(0, -1, phi);
        verts[5] = new Vector3(0, 1, phi);
        verts[6] = new Vector3(0, 1, -phi);
        verts[7] = new Vector3(0, -1, -phi);

        verts[8] = new Vector3(phi, 0, -1);
        verts[9] = new Vector3(phi, 0, 1);
        verts[10] = new Vector3(-phi, 0, 1);
        verts[11] = new Vector3(-phi, 0, -1);

        Quat quat = Quat.aroundAxis(0, 0, 1, Math.atan(1 / phi));
        for (Vector3 vec : verts) {
            quat.rotate(vec);
        }

        icosahedron4 = CCModel.newModel(PrimitiveTopology.TRIANGLES, 60);
        icosahedron7 = CCModel.newModel(PrimitiveTopology.QUADS, 80);

        i = 0;
        //top
        addIcosahedronTriangle(verts[1], 0.5, 0, verts[0], 0, 0.25, verts[5], 1, 0.25);
        addIcosahedronTriangle(verts[1], 0.5, 0, verts[5], 0, 0.25, verts[9], 1, 0.25);
        addIcosahedronTriangle(verts[1], 0.5, 0, verts[9], 0, 0.25, verts[8], 1, 0.25);
        addIcosahedronTriangle(verts[1], 0.5, 0, verts[8], 0, 0.25, verts[6], 1, 0.25);
        addIcosahedronTriangle(verts[1], 0.5, 0, verts[6], 0, 0.25, verts[0], 1, 0.25);
        //centre 1vert top
        addIcosahedronTriangle(verts[0], 0.5, 0.25, verts[11], 0, 0.75, verts[10], 1, 0.75);
        addIcosahedronTriangle(verts[5], 0.5, 0.25, verts[10], 0, 0.75, verts[4], 1, 0.75);
        addIcosahedronTriangle(verts[9], 0.5, 0.25, verts[4], 0, 0.75, verts[2], 1, 0.75);
        addIcosahedronTriangle(verts[8], 0.5, 0.25, verts[2], 0, 0.75, verts[7], 1, 0.75);
        addIcosahedronTriangle(verts[6], 0.5, 0.25, verts[7], 0, 0.75, verts[11], 1, 0.75);
        //centre 1vert bottom
        addIcosahedronTriangle(verts[2], 0.5, 0.75, verts[8], 0, 0.25, verts[9], 1, 0.25);
        addIcosahedronTriangle(verts[7], 0.5, 0.75, verts[6], 0, 0.25, verts[8], 1, 0.25);
        addIcosahedronTriangle(verts[11], 0.5, 0.75, verts[0], 0, 0.25, verts[6], 1, 0.25);
        addIcosahedronTriangle(verts[10], 0.5, 0.75, verts[5], 0, 0.25, verts[0], 1, 0.25);
        addIcosahedronTriangle(verts[4], 0.5, 0.75, verts[9], 0, 0.25, verts[5], 1, 0.25);
        //bottom
        addIcosahedronTriangle(verts[3], 0.5, 1, verts[2], 0, 0.75, verts[4], 1, 0.75);
        addIcosahedronTriangle(verts[3], 0.5, 1, verts[7], 0, 0.75, verts[2], 1, 0.75);
        addIcosahedronTriangle(verts[3], 0.5, 1, verts[11], 0, 0.75, verts[7], 1, 0.75);
        addIcosahedronTriangle(verts[3], 0.5, 1, verts[10], 0, 0.75, verts[11], 1, 0.75);
        addIcosahedronTriangle(verts[3], 0.5, 1, verts[4], 0, 0.75, verts[10], 1, 0.75);

        icosahedron4.computeNormals().smoothNormals();
        icosahedron7.computeNormals().smoothNormals();
    }

    private static void addIcosahedronTriangle(Vector3 vec1, double u1, double v1, Vector3 vec2, double u2, double v2, Vector3 vec3, double u3, double v3) {
        icosahedron4.verts[i * 3] = icosahedron7.verts[i * 4] = new Vertex5(vec1, u1, v1);
        icosahedron4.verts[i * 3 + 1] = icosahedron7.verts[i * 4 + 1] = new Vertex5(vec2, u2, v2);
        icosahedron4.verts[i * 3 + 2] = icosahedron7.verts[i * 4 + 2] = icosahedron7.verts[i * 4 + 3] = new Vertex5(vec3, u3, v3);
        i++;
    }

    private static final RenderPipeline ICOS_TRIANGLES = createPipeline("icosahedron4", PrimitiveTopology.TRIANGLES);
    private static final RenderPipeline ICOS_QUADS = createPipeline("icosahedron7", PrimitiveTopology.QUADS);
    private static final Map<Identifier, RenderType> TRIANGLE_TYPES = new ConcurrentHashMap<>();
    private static final Map<Identifier, RenderType> QUAD_TYPES = new ConcurrentHashMap<>();

    private static RenderPipeline createPipeline(String name, PrimitiveTopology topology) {
        return RenderPipeline.builder(RenderPipelines.BLOCK_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(codechicken.lib.CodeChickenLib.MOD_ID, "pipeline/" + name))
                .withPrimitiveTopology(topology)
                .withColorTargetState(ColorTargetState.DEFAULT)
                .build();
    }

    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(ICOS_TRIANGLES);
        event.registerPipeline(ICOS_QUADS);
    }

    public static RenderType getIcos4RenderType(Identifier texture) {
        return TRIANGLE_TYPES.computeIfAbsent(texture, id -> RenderType.create("icosahedron4", makeIcosState(id, ICOS_TRIANGLES)));
    }

    public static RenderType getIcos7RenderType(Identifier texture) {
        return QUAD_TYPES.computeIfAbsent(texture, id -> RenderType.create("icosahedron7", makeIcosState(id, ICOS_QUADS)));
    }

    public static RenderSetup makeIcosState(Identifier texture) {
        return makeIcosState(texture, ICOS_QUADS);
    }

    private static RenderSetup makeIcosState(Identifier texture, RenderPipeline pipeline) {
        return RenderSetup.builder(pipeline).withTexture("Sampler0", texture).useLightmap().createRenderSetup();
    }
}