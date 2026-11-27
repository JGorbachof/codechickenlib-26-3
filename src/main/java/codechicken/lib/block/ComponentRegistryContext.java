package codechicken.lib.block;

import net.minecraft.core.HolderLookup;

/** Registry context selection independent of block entity lifecycle. */
final class ComponentRegistryContext {
    private ComponentRegistryContext() {}

    static HolderLookup.Provider serializationRegistries(com.mojang.serialization.DynamicOps<?> ops,
            @org.jetbrains.annotations.Nullable HolderLookup.Provider fallback) {
        if(ops instanceof net.minecraft.resources.RegistryOps<?> registryOps) {
            if(registryOps.lookupProvider instanceof net.minecraft.resources.RegistryOps.HolderLookupAdapter adapter)
                return adapter.lookupProvider;
            throw new IllegalStateException("Component save requires a full registry lookup provider.");
        }
        return fallback!=null?fallback:net.minecraft.core.RegistryAccess.EMPTY;
    }

}
