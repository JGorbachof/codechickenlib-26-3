package codechicken.lib.model;

import com.mojang.math.Quadrant;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.cuboid.CuboidFace;
import net.minecraft.client.resources.model.cuboid.FaceBakery;
import net.minecraft.client.resources.model.cuboid.ItemModelGenerator;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.ExtraFaceData;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.List;

/** Generates the front, back and pixel-edge faces of layered item sprites. */
public class ItemQuadBakery {
    public static final ModelState IDENTITY = new ModelState() { };

    private static final ModelBaker.Interner DIRECT = new ModelBaker.Interner() {
        @Override public Vector3fc vector(Vector3fc vector) { return new Vector3f(vector); }
        @Override public BakedQuad.MaterialInfo materialInfo(BakedQuad.MaterialInfo info) { return info; }
    };

    public static List<BakedQuad> bakeItem(TextureAtlasSprite... sprites) {
        return bakeItem(IDENTITY, sprites);
    }

    public static List<BakedQuad> bakeItem(ModelState state, TextureAtlasSprite... sprites) {
        return bakeItem(DIRECT, state, sprites);
    }

    public static List<BakedQuad> bakeItem(ModelBaker.Interner interner, ModelState state, TextureAtlasSprite... sprites) {
        QuadCollection.Builder builder = new QuadCollection.Builder();
        Vector3f from = new Vector3f(0, 0, 7.5F);
        Vector3f to = new Vector3f(16, 16, 8.5F);
        for (int i = 0; i < sprites.length; i++) {
            TextureAtlasSprite sprite = sprites[i];
            var material = interner.materialInfo(BakedQuad.MaterialInfo.of(new Material.Baked(sprite, false),
                    sprite.transparency(), i, null, 0, true));
            builder.addUnculledFace(FaceBakery.bakeQuad(interner, from, to, new CuboidFace.UVs(0, 0, 16, 16),
                    Quadrant.R0, material, Direction.SOUTH, state, null, ExtraFaceData.DEFAULT));
            builder.addUnculledFace(FaceBakery.bakeQuad(interner, from, to, new CuboidFace.UVs(16, 0, 0, 16),
                    Quadrant.R0, material, Direction.NORTH, state, null, ExtraFaceData.DEFAULT));
            ItemModelGenerator.bakeSideFaces(builder, interner, state, material, ExtraFaceData.DEFAULT);
        }
        return builder.build().getAll();
    }
}