package codechicken.lib.inventory.container;

import codechicken.lib.packet.PacketCustom;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public abstract class ContainerExtended extends AbstractContainerMenu {

    @Nullable
    protected ServerPlayer player;
    private Slot transferSource;

    protected ContainerExtended(MenuType<?> type, int id, Inventory inv) {
        super(type, id);
        if (inv.player instanceof ServerPlayer player) {
            this.player = player;
        }
    }

    @Override
    public void setSynchronizer(ContainerSynchronizer delegate) {
        super.setSynchronizer(new ContainerSynchronizer() {
            @Override
            public void sendInitialData(AbstractContainerMenu container, java.util.List<ItemStack> stacks, ItemStack carried, int[] data) {
                delegate.sendInitialData(container, stacks, carried, data);
                if (player != null) {
                    for (int i = 0; i < stacks.size(); i++) {
                        ItemStack stack = stacks.get(i);
                        if (stack.getCount() > Byte.MAX_VALUE) {
                            sendLargeStack(stack, i, player);
                        }
                    }
                }
            }

            @Override
            public void sendSlotChange(AbstractContainerMenu container, int slot, ItemStack stack) {
                delegate.sendSlotChange(container, slot, stack);
                if (player != null) {
                    if (stack.getCount() > Byte.MAX_VALUE) {
                        sendLargeStack(stack, slot, player);
                    }
                }
            }

            @Override public RemoteSlot createSlot() { return delegate.createSlot(); }
            @Override
            public void sendCarriedChange(AbstractContainerMenu container, ItemStack stack) {
                delegate.sendCarriedChange(container, stack);
            }

            @Override
            public void sendDataChange(AbstractContainerMenu container, int slot, int data) {
                delegate.sendDataChange(container, slot, data);
            }
        });
    }

    public void sendLargeStack(ItemStack stack, int slot, ServerPlayer player) {
    }

    @Override
    public void clicked(int slot, int dragType, ContainerInput clickType, Player player) {
        if (slot >= 0 && slot < slots.size()) {
            Slot actualSlot = getSlot(slot);
            if (actualSlot instanceof SlotHandleClicks) {
                ((SlotHandleClicks) actualSlot).slotClick(this, player, dragType, clickType);
                return;
            }
        }
        super.clicked(slot, dragType, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack transferredStack = ItemStack.EMPTY;
        if(slotIndex<0||slotIndex>=slots.size())return ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);

        if (slot.isActive() && slot.hasItem() && slot.mayPickup(player)) {
            ItemStack stack = slot.getItem().copy();
            transferredStack = stack.copy();

            Slot prior=transferSource;
            transferSource=slot;
            try{
                if(!doMergeStackAreas(slotIndex,stack))return ItemStack.EMPTY;
            }finally{transferSource=prior;}
            slot.setByPlayer(stack,transferredStack);
            slot.onTake(player,stack);
        }

        return transferredStack;
    }

    @Override
    public boolean moveItemStackTo(ItemStack stack, int startIndex, int endIndex, boolean reverse) {
        return SlotTransfer.move(stack,slots.subList(startIndex,endIndex),reverse,false,transferSource);
    }

    public boolean doMergeStackAreas(int slotIndex, ItemStack stack) {
        return false;
    }

    protected void bindPlayerInventory(Inventory inventoryPlayer) {
        bindPlayerInventory(inventoryPlayer, 8, 84);
    }

    protected void bindPlayerInventory(Inventory inventoryPlayer, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventoryPlayer, col + row * 9 + 9, x + col * 18, y + row * 18));
            }
        }
        for (int slot = 0; slot < 9; slot++) {
            addSlot(new Slot(inventoryPlayer, slot, x + slot * 18, y + 58));
        }
    }

    @Override
    public boolean stillValid(Player var1) {
        return true;
    }

    /**
     * May be called from a client packet handler to handle additional info
     */

    public void handleOutputPacket(PacketCustom packet) {
    }

    /**
     * May be called from a server packet handler to handle additional info
     */

    public void handleInputPacket(PacketCustom packet) {
    }

    /**
     * May be called from a server packet handler to handle client input
     */

    public void handleGuiChange(int ID, int value) {
    }
}
