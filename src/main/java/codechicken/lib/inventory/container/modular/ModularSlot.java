package codechicken.lib.inventory.container.modular;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.IndexModifier;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;

import java.util.function.*;

/**
 * A fully configurable inventory slot.
 * If there is anything this slot can not do... Let me know.
 * <p>
 * Created by brandon3055 on 10/09/2023
 */
public class ModularSlot extends ResourceHandlerSlot {

    private Container backingContainer;
    private boolean canPlace = true;
    private boolean checkContainer = true;
    private Supplier<Boolean> enabled = () -> true;
    private Predicate<ItemStack> validator = stack -> true;
    private Function<ItemStack, Integer> stackLimit = stack -> Integer.MAX_VALUE;
    private BiPredicate<Player, ItemStack> canRemove = (player, stack) -> true;
    private BiConsumer<ItemStack, ItemStack> onSet = (oldStack, newStack) -> { };

    public ModularSlot(Container container, int index) {
        this(container,index,0,0);
    }

    public ModularSlot(Container container, int index,int xPos,int yPos) {
        this(VanillaContainerWrapper.of(container),(slot,resource,count)->{
            container.setItem(slot,resource.toStack(count));
            container.setChanged();
        },index,xPos,yPos);
        backingContainer=container;
    }

    public ModularSlot(ItemStacksResourceHandler handler,int index) {
        this(handler,handler::set,index,0,0);
    }

    public ModularSlot(ItemStacksResourceHandler handler,int index,int xPos,int yPos) {
        this(handler,handler::set,index,xPos,yPos);
    }

    public ModularSlot(ResourceHandler<ItemResource> handler,IndexModifier<ItemResource> modifier,int index) {
        this(handler,modifier,index,0,0);
    }

    public ModularSlot(ResourceHandler<ItemResource> handler,IndexModifier<ItemResource> modifier,int index,int xPos,int yPos) {
        super(handler,modifier,index,xPos,yPos);
    }

    /**
     * Configure this slot as an output only slot.
     * Items can not be placed in this slot by the player.
     */
    public ModularSlot output() {
        canPlace = false;
        return this;
    }

    /**
     * Do not use the containers canPlaceItem when checking if an item can be placed.
     */
    public ModularSlot noCheck() {
        checkContainer = false;
        return this;
    }

    /**
     * Allows you to attach a validator to control what items are allowed in this slot.
     * You can also limit a slots allowed contents via the {@link Container#canPlaceItem(int, ItemStack)} method of the container.
     *
     * @param validator The validator predicate, If the predicate returns false for a stack, the stack will not be placed.
     */
    public ModularSlot setValidator(Predicate<ItemStack> validator) {
        this.validator = validator;
        return this;
    }

    /**
     * Allows you to get a callback when the slot contents are set.
     * Parameters given are Old stack then New stack.
     */
    public ModularSlot onSet(BiConsumer<ItemStack, ItemStack> onSet) {
        this.onSet = onSet;
        return this;
    }

    /**
     * Allows you to apply a stack size limit that (if smaller) will override the container and the item stack limits.
     */
    public ModularSlot setStackLimit(Function<ItemStack, Integer> stackLimit) {
        this.stackLimit = stackLimit;
        return this;
    }

    /**
     * Allows you to attach a "can remove" predicate that can block removal of a stack from the slot by the player.
     */
    public ModularSlot setCanRemove(BiPredicate<Player, ItemStack> canRemove) {
        this.canRemove = canRemove;
        return this;
    }

    public ModularSlot setEnabled(Supplier<Boolean> enabled) {
        this.enabled = enabled;
        return this;
    }

    public ModularSlot setEnabled(boolean enabled) {
        this.enabled = () -> enabled;
        return this;
    }

    @Override public boolean isSameInventory(net.minecraft.world.inventory.Slot other){
        if(backingContainer!=null){
            if(other instanceof ModularSlot modular && modular.backingContainer!=null)return backingContainer==modular.backingContainer;
            if(other.container==backingContainer)return true;
        }
        return super.isSameInventory(other);
    }

    @Override
    public boolean mayPlace(ItemStack itemStack) {
        return isActive() && canPlace && !itemStack.isEmpty() && validator.test(itemStack) && (!checkContainer || super.mayPlace(itemStack));
    }

    @Override
    public boolean mayPickup(Player player) {
        return isActive() && canRemove.test(player, getStackCopy()) && super.mayPickup(player);
    }

    @Override
    protected void setStackCopy(ItemStack itemStack) {
        onSet.accept(getStackCopy(), itemStack);
        super.setStackCopy(itemStack);
    }

    @Override
    public boolean isActive() {
        return enabled.get();
    }

    @Override
    public int getMaxStackSize(ItemStack itemStack) {
        return Math.max(0, Math.min(super.getMaxStackSize(itemStack), stackLimit.apply(itemStack)));
    }
}
