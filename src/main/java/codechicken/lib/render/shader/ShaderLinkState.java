package codechicken.lib.render.shader;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/** Tracks relink requests until a new program has successfully committed. */
final class ShaderLinkState {
    private final AtomicLong requested=new AtomicLong(1);
    private long committed;
    private boolean retry;
    private int[] ids;
    private long[] revisions;
    void beginLink(){retry=true;}
    void invalidate(){requested.incrementAndGet();}
    long request(){return requested.get();}
    boolean needsLink(long request,int[] ids,long[] revisions){
        return retry||committed!=request||!Arrays.equals(this.ids,ids)||!Arrays.equals(this.revisions,revisions);
    }
    void commit(long request,int[] ids,long[] revisions){
        this.ids=ids.clone();this.revisions=revisions.clone();committed=request;retry=false;
    }
}