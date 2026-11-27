package codechicken.lib.inventory.container;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** Copy-safe shift-click transfer shared by CCL menus. */
public final class SlotTransfer {
    private SlotTransfer(){}
    public static boolean sameStorage(Slot a,Slot b){
        return a==b || (a.getContainerSlot()==b.getContainerSlot()&&(a.isSameInventory(b)||b.isSameInventory(a)));
    }
    public static boolean move(ItemStack remaining,List<Slot> targets,boolean reverse,boolean singleEmpty,Slot source){
        if(remaining.isEmpty())return false;
        boolean moved=false;
        int start=reverse?targets.size()-1:0,step=reverse?-1:1;
        for(int pass=0;pass<2;pass++){
            if(pass==0&&!remaining.isStackable())continue;
            for(int i=start;i>=0&&i<targets.size()&&!remaining.isEmpty();i+=step){
                Slot slot=targets.get(i);
                if(source!=null&&sameStorage(source,slot)||!slot.isActive()||!slot.mayPlace(remaining))continue;
                ItemStack previous=slot.getItem().copy();
                if(pass==0&&(previous.isEmpty()||!ItemStack.isSameItemSameComponents(remaining,previous)))continue;
                if(pass==1&&!previous.isEmpty())continue;
                int cap=Math.max(0,Math.min(remaining.getMaxStackSize(),slot.getMaxStackSize(remaining)));
                int count=Math.min(remaining.getCount(),Math.max(0,cap-previous.getCount()));
                if(count==0)continue;
                ItemStack next=pass==0?previous.copyWithCount(previous.getCount()+count):remaining.copyWithCount(count);
                slot.setByPlayer(next,previous);
                remaining.shrink(count);
                moved=true;
                if(pass==1&&singleEmpty)break;
            }
        }
        return moved;
    }
}