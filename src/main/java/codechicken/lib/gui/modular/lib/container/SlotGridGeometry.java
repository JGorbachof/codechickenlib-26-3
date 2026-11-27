package codechicken.lib.gui.modular.lib.container;

/** Grid geometry shared by slot positioning and GUI sizing. */
public record SlotGridGeometry(int count,int requestedColumns,int xSpacing,int ySpacing) {
    public SlotGridGeometry {
        if(count<0||requestedColumns<=0)throw new IllegalArgumentException("Invalid slot grid");
    }
    public int columns(){return Math.min(count,requestedColumns);}
    public int rows(){return count==0?0:1+(count-1)/columns();}
    public int width(){return columns()==0?0:columns()*18+(columns()-1)*xSpacing;}
    public int height(){return rows()==0?0:rows()*18+(rows()-1)*ySpacing;}
    public int x(int index){check(index);return 1+(index%columns())*(18+xSpacing);}
    public int y(int index){check(index);return 1+(index/columns())*(18+ySpacing);}
    private void check(int index){if(index<0||index>=count)throw new IndexOutOfBoundsException(index);}
}