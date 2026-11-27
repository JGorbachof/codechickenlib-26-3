package codechicken.lib.block;

import codechicken.lib.block.ModularTileBlock.TileComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.MustBeInvokedByOverriders;

import static net.covers1624.quack.util.SneakyUtils.unsafeCast;

/**
 * Created by covers1624 on 19/7/22.
 */
@ApiStatus.Experimental
public abstract class ModularBlockEntity extends BlockEntity {

    private final ModularTileBlock<?> block;
    private final DataComponent[] components;

    public ModularBlockEntity(BlockEntityType<?> tileType, BlockPos pos, BlockState state) {
        super(tileType, pos, state);
        Block bl = state.getBlock();
        if (!(bl instanceof ModularTileBlock)) {
            throw new IllegalStateException("ModularBlockEntity constructed with the incorrect Block! Expected a ModularTileBlock. Got: " + bl.getClass().getName() + " State: " + state);
        }
        block = (ModularTileBlock<?>) bl;

        components = new DataComponent[block.namedComponents.size()];
        for (TileComponent<?> component : block.namedComponents.values()) {
            components[component.id] = component.createData(this);
        }
    }

    public final <T extends DataComponent> T getData(TileComponent<T> component) {
        assert block.namedComponents.get(component.name) == component;

        return unsafeCast(components[component.id]);
    }

    @Override
    @MustBeInvokedByOverriders
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);

        for (DataComponent component : components) {
            output.store(component.tileComponent.name, COMPONENT_SAVE_CODEC, component);
        }
    }

    @Override
    @MustBeInvokedByOverriders
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);

        for (DataComponent component : components) {
            input.read(component.tileComponent.name, CompoundTag.CODEC).ifPresent(componentTag -> component.load(componentTag, input.lookup()));
        }
    }

    // The output's actual registry context also works for detached block entities.
    private static final com.mojang.serialization.Codec<DataComponent> COMPONENT_SAVE_CODEC =
            com.mojang.serialization.Codec.of(new com.mojang.serialization.Encoder<DataComponent>() {
                @Override public <T> com.mojang.serialization.DataResult<T> encode(DataComponent component,
                        com.mojang.serialization.DynamicOps<T> ops,T prefix) {
                    CompoundTag tag=new CompoundTag();
                    HolderLookup.Provider fallback=component.tile.getLevel()==null?null:component.tile.getLevel().registryAccess();
                    component.save(tag,ComponentRegistryContext.serializationRegistries(ops,fallback));
                    return CompoundTag.CODEC.encode(tag,ops,prefix);
                }
            },new com.mojang.serialization.Decoder<DataComponent>() {
                @Override public <T> com.mojang.serialization.DataResult<com.mojang.datafixers.util.Pair<DataComponent,T>> decode(
                        com.mojang.serialization.DynamicOps<T> ops,T input) {
                    return com.mojang.serialization.DataResult.error(()->"Component decoding requires the owning block entity.");
                }
            });

    public static abstract class DataComponent {

        protected final ModularBlockEntity tile;
        protected final TileComponent<?> tileComponent;

        protected DataComponent(ModularBlockEntity tile, TileComponent<?> tileComponent) {
            this.tile = tile;
            this.tileComponent = tileComponent;
        }

        protected void save(CompoundTag tag, HolderLookup.Provider registries) {
        }

        protected void load(CompoundTag tag, HolderLookup.Provider registries) {
        }
    }
}
