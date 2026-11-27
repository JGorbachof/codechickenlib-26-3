package codechicken.lib.block;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ComponentRegistryContextTests {
    @Test void detachedSaveUsesSerializationProvider() {
        var provider=HolderLookup.Provider.create(java.util.stream.Stream.empty());
        var fallback=HolderLookup.Provider.create(java.util.stream.Stream.empty());
        assertSame(provider,ComponentRegistryContext.serializationRegistries(provider.createSerializationContext(NbtOps.INSTANCE),fallback));
        assertSame(provider,ComponentRegistryContext.serializationRegistries(provider.createSerializationContext(NbtOps.INSTANCE),null));
    }
    @Test void contextFreeSaveUsesExplicitFallbackOrEmpty() {
        var provider=HolderLookup.Provider.create(java.util.stream.Stream.empty());
        assertSame(provider,ComponentRegistryContext.serializationRegistries(NbtOps.INSTANCE,provider));
        assertSame(RegistryAccess.EMPTY,ComponentRegistryContext.serializationRegistries(NbtOps.INSTANCE,null));
    }
}