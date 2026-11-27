package codechicken.lib.gui.modular.sprite;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * This is similar to Minecraft's {@link net.minecraft.client.resources.model.sprite.Material}
 * This contains the essential data required to render an atlas sprite.
 * <p>
 * The primary purpose of this class is to make porting between MC versions easier.
 * It also allows for loading sprites from a custom texture atlas. Minecraft's material class can only load from vanilla atlases.
 * <p>
 * Created by brandon3055 on 20/08/2023
 */
public class Material {
    private final Identifier atlasLocation;
    private final Identifier texture;
    private final Function<Identifier, TextureAtlasSprite> spriteFunction;

    @Nullable
    private RenderType renderType;
    @Nullable
    private net.minecraft.client.resources.model.sprite.Material vanillaMat;

    public Material(Identifier atlasLocation, Identifier texture, Function<Identifier, TextureAtlasSprite> spriteFunction) {
        this.atlasLocation = atlasLocation;
        this.texture = texture;
        this.spriteFunction = spriteFunction;
    }

    public Identifier atlasLocation() {
        return atlasLocation;
    }

    public SpriteId getSpriteId() {
        return new SpriteId(atlasLocation, texture);
    }

    public Identifier texture() {
        return texture;
    }

    public TextureAtlasSprite sprite() {
        return spriteFunction.apply(texture());
    }

    /**
     * Returns the cached render type for this material.
     * The supplied function will be used to create the render type the first time this method is called.
     *
     * @param typeBuilder a function that will be used to create the render type if it does not already exist.
     * @return The render type for this material.
     */
    public RenderType renderType(Function<Identifier, RenderType> typeBuilder) {
        if (this.renderType == null) {
            this.renderType = typeBuilder.apply(atlasLocation());
        }
        return this.renderType;
    }

    /**
     * Convenience method to create a vertex consumer using this materials render type.
     *
     * @param buffers     bugger source.
     * @param typeBuilder a function that will be used to create the render type if it does not already exist.
     */
    public VertexConsumer buffer(Function<RenderType, VertexConsumer> buffers, Function<Identifier, RenderType> typeBuilder) {
        return buffers.apply(renderType(typeBuilder));
    }

    /** Vanilla model materials resolve sprite identifiers through the item and block atlases. */
    public net.minecraft.client.resources.model.sprite.Material getVanillaMat() {
        if (!atlasLocation.equals(TextureAtlas.LOCATION_BLOCKS) && !atlasLocation.equals(TextureAtlas.LOCATION_ITEMS)) {
            throw new IllegalStateException("Custom atlas requires getSpriteId(): " + atlasLocation);
        }
        if (vanillaMat == null) {
            vanillaMat = new net.minecraft.client.resources.model.sprite.Material(texture);
        }
        return vanillaMat;
    }

    /**
     * Convenient method for getting a material from a vanilla texture atlas.
     *
     * @return an un-cached material from a vanilla atlas.
     */
    public static Material fromAtlas(Identifier atlasLocation, String texture) {
        return new Material(atlasLocation, Identifier.fromNamespaceAndPath(atlasLocation.getNamespace(), texture), e -> Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(atlasLocation).getSprite(e));
    }

    /**
     * Create a material from an existing sprite.
     * Note: This will only work with sprites from a vanilla atlas.
     */
    @Nullable
    public static Material fromSprite(@Nullable TextureAtlasSprite sprite) {
        if (sprite == null) return null;
        return new Material(sprite.atlasLocation(), sprite.contents().name(), e -> Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(sprite.atlasLocation()).getSprite(e));
    }
}
