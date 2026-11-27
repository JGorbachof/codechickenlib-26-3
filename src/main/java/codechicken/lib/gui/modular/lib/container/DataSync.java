package codechicken.lib.gui.modular.lib.container;

import codechicken.lib.data.MCDataInput;
import codechicken.lib.inventory.container.data.AbstractDataStore;
import codechicken.lib.inventory.container.modular.ModularGuiContainerMenu;

import java.util.function.Supplier;

/**
 * This class provides a convenient way to synchronize server side data with a client side screen via the ContainerMenu
 * <p>
 * Created by brandon3055 on 09/09/2023
 */
public class DataSync<T> {
    public static final int PKT_SEND_CHANGES = 255;
    private final ModularGuiContainerMenu containerMenu;
    private final AbstractDataStore<T> dataStore;
    private final Supplier<T> valueGetter;
    private boolean sentInitial;
    private boolean pendingSend;

    public DataSync(ModularGuiContainerMenu containerMenu, AbstractDataStore<T> dataStore, Supplier<T> valueGetter) {
        this.containerMenu = containerMenu;
        this.dataStore = dataStore;
        this.valueGetter = valueGetter;
        if(containerMenu.dataSyncs.size()>=256)throw new IllegalStateException("Too many data sync entries for byte-index protocol.");
        containerMenu.dataSyncs.add(this);
    }

    public T get() {
        return dataStore.get();
    }

    /**
     * This should only ever be called server side!
     */
    public void detectAndSend() {
        if(!(containerMenu.inventory.player instanceof net.minecraft.server.level.ServerPlayer))return;
        T next=valueGetter.get();
        if(sentInitial&&!pendingSend&&dataStore.isSameValue(next))return;
        int index=containerMenu.dataSyncs.indexOf(this);
        if(index<0||index>255)throw new IllegalStateException("Invalid data sync index "+index);
        pendingSend=true;
        dataStore.set(next);
        containerMenu.sendPacketToClient(PKT_SEND_CHANGES,buf->{
            buf.writeByte((byte)index);
            dataStore.toBytes(buf);
        });
        sentInitial=true;
        pendingSend=false;
    }

    public void handleSyncPacket(MCDataInput buf) {
        dataStore.fromBytes(buf);
    }
}
