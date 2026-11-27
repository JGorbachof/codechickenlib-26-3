/*
 * This file is part of CodeChickenLib.
 * Copyright (c) 2018, covers1624, All rights reserved.
 *
 * CodeChickenLib is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * CodeChickenLib is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with CodeChickenLib. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package codechicken.lib.model;

import codechicken.lib.math.InterpHelper;
import codechicken.lib.math.MathHelper;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.resources.model.sprite.Material;
import net.neoforged.neoforge.client.model.quad.BakedColors;
import net.neoforged.neoforge.client.model.quad.BakedNormals;
import org.joml.Vector3f;
import java.util.Arrays;
import codechicken.lib.vec.Cuboid6;
import codechicken.lib.vec.Vector3;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A simple easy to manipulate quad format. Can be reset and then used on a different format.
 *
 * @author covers1624
 */
public class Quad implements IVertexProducer, IVertexConsumer {

    private @Nullable CachedFormat format;

    public int tintIndex = -1;
    public @Nullable Direction orientation;
    public boolean diffuseLighting = true;
    public @Nullable BakedQuad.MaterialInfo materialInfo;
    public @Nullable TextureAtlasSprite sprite;

    public Vertex[] vertices = new Vertex[4];
    public boolean full;

    // Not copied.
    private int vertexIndex = 0;

    // Cache for normal computation.
    private final Vector3 v1 = new Vector3();
    private final Vector3 v2 = new Vector3();
    private final Vector3 t = new Vector3();
    private final Cuboid6 c = new Cuboid6();

    /**
     * Use this if you reset the quad each time you use it.
     */
    public Quad() {
    }

    /**
     * use this if you want to initialize the quad with a format.
     *
     * @param format The format.
     */
    public Quad(CachedFormat format) {
        this.format = format;
    }

    public final CachedFormat format() {
        return requireNonNull(format, "Quad does not have a format assigned yet.");
    }

    @Override
    public VertexFormat getVertexFormat() {
        return format().format;
    }

    @Override
    public void setQuadTint(int tint) {
        tintIndex = tint;
    }

    @Override
    public void setQuadOrientation(Direction orientation) {
        this.orientation = orientation;
    }

    @Override
    public void setApplyDiffuseLighting(boolean diffuse) {
        if (materialInfo != null && diffuse != diffuseLighting) {
            materialInfo = new BakedQuad.MaterialInfo(materialInfo.sprite(), materialInfo.layer(), materialInfo.itemRenderType(),
                    materialInfo.itemGlintRenderType(), materialInfo.itemGlintSpecialRenderType(), materialInfo.tintIndex(),
                    diffuse ? null : Direction.UP, materialInfo.lightEmission(), materialInfo.ambientOcclusion());
        }
        diffuseLighting = diffuse;
    }

    @Override
    public void setTexture(TextureAtlasSprite texture) {
        sprite = texture;
    }

    @Override
    public void setMaterialInfo(BakedQuad.MaterialInfo info) {
        materialInfo = info;
    }

    @Override
    public void put(int element, float... data) {
        if (full) {
            throw new RuntimeException("Unable to add data when full.");
        }
        Vertex v = vertices[vertexIndex];
        if (v == null) {
            v = new Vertex(format());
            vertices[vertexIndex] = v;
        }
        if(data.length>4)throw new IllegalArgumentException("Too many vertex components.");
        Arrays.fill(v.raw[element],0);
        System.arraycopy(data, 0, v.raw[element], 0, data.length);
        if (element == (format().elementCount - 1)) {
            vertexIndex++;
            if (vertexIndex == 4) {
                vertexIndex = 0;
                full = true;
                if (orientation == null) {
                    calculateOrientation(false);
                }
            }
        }
    }

    @Override
    public void put(Quad quad) {
        copyFrom(quad);
    }

    @Override
    public void pipe(IVertexConsumer consumer) {
        consumer.put(this);
    }

    /**
     * Used to reset the interpolation values inside the provided helper.
     *
     * @param helper The helper.
     * @param s      The axis. side >> 1;
     * @return The same helper.
     */
    public InterpHelper resetInterp(InterpHelper helper, int s) {
        helper.reset(
                vertices[0].dx(s), vertices[0].dy(s),
                vertices[1].dx(s), vertices[1].dy(s),
                vertices[2].dx(s), vertices[2].dy(s),
                vertices[3].dx(s), vertices[3].dy(s)
        );
        return helper;
    }

    /**
     * Clamps the Quad inside the box.
     *
     * @param bb The box.
     */
    public void clamp(AABB bb) {
        clamp(c.set(bb));
    }

    /**
     * Clamps the Quad inside the box.
     *
     * @param cuboid The box.
     */
    public void clamp(Cuboid6 cuboid) {
        for (Vertex vertex : vertices) {
            float[] vec = vertex.vec();
            vec[0] = (float) MathHelper.clip(vec[0], cuboid.min.x, cuboid.max.x);
            vec[1] = (float) MathHelper.clip(vec[1], cuboid.min.y, cuboid.max.y);
            vec[2] = (float) MathHelper.clip(vec[2], cuboid.min.z, cuboid.max.z);
        }
        calculateOrientation(true);
    }

    /**
     * Re-calculates the Orientation of this quad,
     * optionally the normal vector.
     *
     * @param setNormal If the normal vector should be updated.
     */
    public void calculateOrientation(boolean setNormal) {
        v1.set(vertices[3].vec()).subtract(t.set(vertices[1].vec()));
        v2.set(vertices[2].vec()).subtract(t.set(vertices[0].vec()));

        Vector3 normal = v2.crossProduct(v1).normalize();

        if (format().hasNormal && setNormal) {
            for (Vertex vertex : vertices) {
                vertex.normal()[0] = (float) normal.x;
                vertex.normal()[1] = (float) normal.y;
                vertex.normal()[2] = (float) normal.z;
                vertex.normal()[3] = 0;
            }
        }
        orientation = Direction.getApproximateNearest(normal.x, normal.y, normal.z);
    }

    /**
     * Used to create a new quad complete copy of this one.
     *
     * @return The new quad.
     */
    public Quad copy() {
        if (!full) {
            throw new RuntimeException("Only copying full quads is supported.");
        }
        Quad quad = new Quad(format());
        quad.tintIndex = tintIndex;
        quad.orientation = orientation;
        quad.diffuseLighting = diffuseLighting;
        quad.materialInfo = materialInfo;
        quad.sprite = sprite;
        quad.full = true;
        for (int i = 0; i < 4; i++) {
            quad.vertices[i] = vertices[i].copy();
        }
        return quad;
    }

    /**
     * Copies the data inside the given quad to this one. This ignores VertexFormat, please make sure your quads are in
     * the same format.
     *
     * @param quad The Quad to copy from.
     * @return This quad.
     */
    public Quad copyFrom(Quad quad) {
        if (!quad.full) throw new IllegalArgumentException("Cannot copy an incomplete quad.");
        if (format == null) reset(quad.format());
        if (!format().format.equals(quad.format().format)) throw new IllegalArgumentException("Quad formats differ.");
        vertexIndex=0;
        tintIndex = quad.tintIndex;
        orientation = quad.orientation;
        diffuseLighting = quad.diffuseLighting;
        materialInfo = quad.materialInfo;
        sprite = quad.sprite;
        full = quad.full;
        for (int v = 0; v < 4; v++) {
            if (vertices[v] == null) vertices[v] = new Vertex(format());
            for (int e = 0; e < format().elementCount; e++) {
                System.arraycopy(quad.vertices[v].raw[e], 0, vertices[v].raw[e], 0, 4);
            }
        }
        return this;
    }

    /**
     * Reset the quad to the new format.
     *
     * @param format The new format.
     */
    public void reset(CachedFormat format) {
        this.format = format;
        tintIndex = -1;
        orientation = null;
        diffuseLighting = true;
        materialInfo = null;
        sprite = null;
        for (int i = 0; i < vertices.length; i++) {
            Vertex v = vertices[i];
            if (v == null) {
                vertices[i] = v = new Vertex(format);
            }
            v.reset(format);
        }
        vertexIndex = 0;
        full = false;
    }

    /**
     * Rewind this Quad without completely resetting it.
     */
    public void rewind() {
        vertexIndex = 0;
        full = false;
    }

    /**
     * Bakes this Quad to a BakedQuad.
     *
     * @return The BakedQuad.
     */
    public BakedQuad bake() {
        var face = requireNonNull(orientation, "Quad requires an orientation.");
        var texture = requireNonNull(sprite, "Quad requires a sprite.");
        var cached = format();
        if (!cached.hasPosition || !cached.hasUV) {
            throw new IllegalStateException("Quad requires position and UV elements.");
        }
        var positions = new Vector3f[4];
        var uv = new long[4];
        var colors = new int[4];
        var normals = new int[4];
        for (int v = 0; v < 4; v++) {
            Vertex vertex = requireNonNull(vertices[v], "Missing quad vertex.");
            float[] p = vertex.vec();
            positions[v] = new Vector3f(p[0], p[1], p[2]);
            uv[v] = UVPair.pack(vertex.uv()[0], vertex.uv()[1]);
            colors[v] = -1;
            if (cached.hasColor) {
                float[] c = vertex.color();
                colors[v] = channel(c[3]) << 24 | channel(c[0]) << 16 | channel(c[1]) << 8 | channel(c[2]);
            }
            if (cached.hasNormal) {
                float[] n = vertex.normal();
                normals[v] = BakedNormals.pack(n[0], n[1], n[2]);
            }
        }
        var original = materialInfo;
        Direction shadeOverride = original == null ? (diffuseLighting ? null : Direction.UP) : original.shadeDirectionOverride();
        int emission = original == null ? 0 : original.lightEmission();
        boolean ao = original == null || original.ambientOcclusion();
        var info = original != null && original.sprite() == texture
                ? new BakedQuad.MaterialInfo(texture, original.layer(), original.itemRenderType(),
                    original.itemGlintRenderType(), original.itemGlintSpecialRenderType(), tintIndex, shadeOverride, emission, ao)
                : BakedQuad.MaterialInfo.of(new Material.Baked(texture, false), texture.transparency(), tintIndex, shadeOverride, emission, ao);
        return new BakedQuad(positions[0], positions[1], positions[2], positions[3],
                uv[0], uv[1], uv[2], uv[3], face, info,
                BakedNormals.of(normals[0], normals[1], normals[2], normals[3]),
                BakedColors.of(colors[0], colors[1], colors[2], colors[3]));
    }

    /**
     * Bake static geometry together with the per-draw light and overlay information.
     * The editable UV1 and UV2 channels use the legacy coordinate/240 convention.
     * Missing channels use the supplied defaults. Native overlay is per quad, not
     * per vertex; a nonuniform overlay cannot be represented and is rejected.
     */
    public LitQuad bakeWithInstance(int defaultLight,int defaultOverlay) {
        int[] light=new int[4];
        int overlay=defaultOverlay;
        for(int v=0;v<4;v++){
            var vertex=vertices[v];
            light[v]=format().hasLightMap?net.minecraft.util.LightCoordsUtil.smoothPack(
                    lightCoordinate(vertex.lightmap()[0]),lightCoordinate(vertex.lightmap()[1])):defaultLight;
            if(format().hasOverlay){
                int packed=net.minecraft.client.renderer.texture.OverlayTexture.pack(
                        overlayCoordinate(vertex.overlay()[0]),overlayCoordinate(vertex.overlay()[1]));
                if(v==0)overlay=packed;
                else if(overlay!=packed)throw new IllegalArgumentException("Native quad overlays must be uniform across vertices.");
            }
        }
        return new LitQuad(bake(),light[0],light[1],light[2],light[3],overlay);
    }

    private static int lightCoordinate(float normalized){
        if(!Float.isFinite(normalized)||normalized<0||normalized>1)
            throw new IllegalArgumentException("Invalid normalized light coordinate "+normalized);
        return Math.round(normalized*240);
    }
    private static int overlayCoordinate(float normalized){
        if(!Float.isFinite(normalized)||normalized<0||normalized>65535F/240F)
            throw new IllegalArgumentException("Invalid normalized overlay coordinate "+normalized);
        return Math.round(normalized*240);
    }

    private static int channel(float value) {
        return Math.round(Math.clamp(value, 0, 1) * 255);
    }

    /**
     * A simple vertex format.
     */
    public static class Vertex {

        public CachedFormat format;

        /**
         * The raw data.
         */
        public float[][] raw;

        // References to the arrays inside raw.
        private float @Nullable [] vec;
        private float @Nullable [] normal;
        private float @Nullable [] color;
        private float @Nullable [] uv;
        private float @Nullable [] overlay;
        private float @Nullable [] lightmap;

        /**
         * Create a new Vertex.
         *
         * @param format The format for the vertex.
         */
        public Vertex(CachedFormat format) {
            this.format = format;
            raw = new float[format.elementCount][4];
            preProcess();
            if (color != null) Arrays.fill(color, 1);
        }

        /**
         * Creates a new Vertex using the data inside the other. A copy!
         *
         * @param other The other.
         */
        public Vertex(Vertex other) {
            format = other.format;
            raw = other.raw.clone();
            for (int v = 0; v < format.elementCount; v++) {
                raw[v] = other.raw[v].clone();
            }
            preProcess();
        }

        /**
         * Pulls references to the individual element's arrays inside raw. Modifying the individual element arrays will
         * update raw.
         */
        public void preProcess() {
            if (format.hasPosition) {
                vec = raw[format.positionIndex];
            }
            if (format.hasNormal) {
                normal = raw[format.normalIndex];
            }
            if (format.hasColor) {
                color = raw[format.colorIndex];
            }
            if (format.hasUV) {
                uv = raw[format.uvIndex];
            }
            if (format.hasOverlay) {
                overlay = raw[format.overlayIndex];
            }
            if (format.hasLightMap) {
                lightmap = raw[format.lightMapIndex];
            }
        }

        // @formatter:off
        public final float[] vec() { return requireNonNull(vec, "Vertex does not have the position element.");}
        public final float[] normal() { return requireNonNull(normal, "Vertex does not have the normal element.");}
        public final float[] color() { return requireNonNull(color, "Vertex does not have the colour element.");}
        public final float[] uv() { return requireNonNull(uv, "Vertex does not have the uv element.");}
        public final float[] overlay() { return requireNonNull(overlay, "Vertex does not have the overlay element.");}
        public final float[] lightmap() { return requireNonNull(lightmap, "Vertex does not have the lightmap element.");}
        // @formatter:on

        /**
         * Gets the 2d X coord for the given axis.
         *
         * @param s The axis. side >> 1
         * @return The x coord.
         */
        public float dx(int s) {
            if (s <= 1) {
                return vec()[0];
            } else {
                return vec()[2];
            }
        }

        /**
         * Gets the 2d Y coord for the given axis.
         *
         * @param s The axis. side >> 1
         * @return The y coord.
         */
        public float dy(int s) {
            if (s > 0) {
                return vec()[1];
            } else {
                return vec()[2];
            }
        }

        /**
         * Interpolates the new color values for this Vertex using the others as a reference.
         *
         * @param interpHelper The InterpHelper to use.
         * @param others       The other Vertices to use as a template.
         * @return The same Vertex.
         */
        public Vertex interpColorFrom(InterpHelper interpHelper, Vertex[] others) {
            for (int e = 0; e < 4; e++) {
                float p1 = others[0].color()[e];
                float p2 = others[1].color()[e];
                float p3 = others[2].color()[e];
                float p4 = others[3].color()[e];
                // Only interpolate if colors are different.
                if (p1 != p2 || p2 != p3 || p3 != p4) {
                    color()[e] = interpHelper.interpolate(p1, p2, p3, p4);
                }
            }
            return this;
        }

        /**
         * Interpolates the new UV values for this Vertex using the others as a reference.
         *
         * @param interpHelper The InterpHelper to use.
         * @param others       The other Vertices to use as a template.
         * @return The same Vertex.
         */
        public Vertex interpUVFrom(InterpHelper interpHelper, Vertex[] others) {
            for (int e = 0; e < 2; e++) {
                float p1 = others[0].uv()[e];
                float p2 = others[1].uv()[e];
                float p3 = others[2].uv()[e];
                float p4 = others[3].uv()[e];
                if (p1 != p2 || p2 != p3 || p3 != p4) {
                    uv()[e] = interpHelper.interpolate(p1, p2, p3, p4);
                }
            }
            return this;
        }

        /**
         * Interpolates the new LightMap values for this Vertex using the others as a reference.
         *
         * @param interpHelper The InterpHelper to use.
         * @param others       The other Vertices to use as a template.
         * @return The same Vertex.
         */
        public Vertex interpLightMapFrom(InterpHelper interpHelper, Vertex[] others) {
            for (int e = 0; e < 2; e++) {
                float p1 = others[0].lightmap()[e];
                float p2 = others[1].lightmap()[e];
                float p3 = others[2].lightmap()[e];
                float p4 = others[3].lightmap()[e];
                if (p1 != p2 || p2 != p3 || p3 != p4) {
                    lightmap()[e] = interpHelper.interpolate(p1, p2, p3, p4);
                }
            }
            return this;
        }

        /**
         * Copies this Vertex to a new one.
         *
         * @return The new Vertex.
         */
        public Vertex copy() {
            return new Vertex(this);
        }

        /**
         * Resets the Vertex to a new format. Expands the raw array if needed.
         *
         * @param format The format to reset to.
         */
        public void reset(CachedFormat format) {
            // If the format is different and our raw array is smaller, then expand it.
            if (!this.format.equals(format) && format.elementCount > raw.length) {
                raw = new float[format.elementCount][4];
            }
            this.format = format;

            for (float[] element : raw) Arrays.fill(element, 0);
            vec = null;
            normal = null;
            color = null;
            uv = null;
            overlay = null;
            lightmap = null;

            preProcess();
            if (color != null) Arrays.fill(color, 1);
        }
    }
}
