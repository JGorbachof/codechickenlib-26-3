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

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.api.vertex.VertexFormatElement;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A simple VertexFormat cache.
 * This caches the existence of attributes and their indexes.
 *
 * @author covers1624
 */
public class CachedFormat {

    private static final Map<VertexFormat, CachedFormat> formatCache = new ConcurrentHashMap<>();
    // Logical editable quad format retains normals independently of the terrain GPU layout.
    public static final VertexFormat QUAD_FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT).addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT).addAttribute("UV2", GpuFormat.RG16_SINT)
            .addAttribute("Normal", GpuFormat.RGBA8_SNORM).build();
    public static final CachedFormat BLOCK = new CachedFormat(QUAD_FORMAT);

    /**
     * Lookup or create the CachedFormat for a given VertexFormat.
     *
     * @param format The format to lookup.
     * @return The CachedFormat.
     */
    public static CachedFormat lookup(VertexFormat format) {
        //Hotwire the common one.
        if (format == QUAD_FORMAT) {
            return BLOCK;
        }
        return formatCache.computeIfAbsent(format, CachedFormat::new);
    }

    public final VertexFormat format;

    public final boolean hasPosition;
    public final boolean hasNormal;
    public final boolean hasColor;
    public final boolean hasUV;
    public final boolean hasOverlay;
    public final boolean hasLightMap;

    public final int positionIndex;
    public final int normalIndex;
    public final int colorIndex;
    public final int uvIndex;
    public final int overlayIndex;
    public final int lightMapIndex;

    public final int elementCount;

    /**
     * Caches the vertex format element indexes for efficiency.
     *
     * @param format The format.
     */
    private CachedFormat(VertexFormat format) {
        this.format = format;
        List<VertexFormatElement> elements = format.getElements();
        elementCount = elements.size();

        boolean hasPosition = false;
        boolean hasNormal = false;
        boolean hasColor = false;
        boolean hasUV = false;
        boolean hasOverlay = false;
        boolean hasLightMap = false;

        int positionIndex = -1;
        int normalIndex = -1;
        int colorIndex = -1;
        int uvIndex = -1;
        int overlayIndex = -1;
        int lightMapIndex = -1;

        for (int i = 0; i < elementCount; i++) {
            VertexFormatElement element = elements.get(i);
            switch (element.name()) {
                case "Position" -> { hasPosition = true; positionIndex = i; }
                case "Normal" -> { hasNormal = true; normalIndex = i; }
                case "Color" -> { hasColor = true; colorIndex = i; }
                case "UV0" -> { hasUV = true; uvIndex = i; }
                case "UV1" -> { hasOverlay = true; overlayIndex = i; }
                case "UV2" -> { hasLightMap = true; lightMapIndex = i; }
            }
        }

        this.hasPosition = hasPosition;
        this.hasNormal = hasNormal;
        this.hasColor = hasColor;
        this.hasUV = hasUV;
        this.hasOverlay = hasOverlay;
        this.hasLightMap = hasLightMap;

        this.positionIndex = positionIndex;
        this.normalIndex = normalIndex;
        this.colorIndex = colorIndex;
        this.uvIndex = uvIndex;
        this.overlayIndex = overlayIndex;
        this.lightMapIndex = lightMapIndex;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof CachedFormat other)) {
            return false;
        }
        return other.elementCount == elementCount &&
                other.positionIndex == positionIndex &&
                other.normalIndex == normalIndex &&
                other.colorIndex == colorIndex &&
                other.uvIndex == uvIndex &&
                other.overlayIndex == overlayIndex &&
                other.lightMapIndex == lightMapIndex;
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + elementCount;
        result = 31 * result + positionIndex;
        result = 31 * result + normalIndex;
        result = 31 * result + colorIndex;
        result = 31 * result + uvIndex;
        result = 31 * result + overlayIndex;
        result = 31 * result + lightMapIndex;
        return result;
    }
}
