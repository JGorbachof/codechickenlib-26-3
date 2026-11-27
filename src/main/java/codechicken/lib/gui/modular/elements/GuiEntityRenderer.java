package codechicken.lib.gui.modular.elements;

import codechicken.lib.gui.modular.lib.BackgroundRender;
import codechicken.lib.gui.modular.lib.GuiRender;
import codechicken.lib.gui.modular.lib.geometry.GuiParent;
import codechicken.lib.gui.modular.lib.geometry.Rectangle;
import codechicken.lib.render.CCRenderEventHandler;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Created by brandon3055 on 15/11/2023
 */
public class GuiEntityRenderer extends GuiElement<GuiEntityRenderer> implements BackgroundRender {

    public static final Logger LOGGER = LogManager.getLogger();
    private static final Map<Identifier, Entity> entityCache = new HashMap<>();
    private static final List<Identifier> invalidEntities = new ArrayList<>();
    private static net.minecraft.client.multiplayer.ClientLevel cacheLevel;
    private boolean cachedEntity;

    private Supplier<Float> rotationSpeed = () -> 1F;
    private Supplier<Float> lockedRotation = () -> 0F;
    private Entity entity;
    private Identifier entityName;
    private boolean invalidEntity = false;
    private Supplier<Boolean> rotationLocked = () -> false;
    private Supplier<Boolean> trackMouse = () -> false;
    private Supplier<Boolean> drawName = () -> false;
    public boolean force2dSize = false;

    public GuiEntityRenderer(@NotNull GuiParent<?> parent) {
        super(parent);
    }

    public GuiEntityRenderer setEntity(Entity entity) {
        cachedEntity=false;
        this.entity = entity;
        if (this.entity == null) {
            invalidEntity = true;
            return this;
        }

        this.entityName = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        invalidEntity = invalidEntities.contains(entityName);
        return this;
    }

    public GuiEntityRenderer setEntity(Identifier entity) {
        this.entityName = entity;
        cachedEntity=true;
        refreshCachedEntity();return this;
    }
    public static void clearEntityCache(){entityCache.clear();invalidEntities.clear();cacheLevel=null;}
    private void refreshCachedEntity(){
        if(cacheLevel!=mc().level){clearEntityCache();cacheLevel=mc().level;}
        if(entityName==null){entity=null;invalidEntity=true;return;}
        this.entity = entityCache.computeIfAbsent(entityName, resourceLocation -> {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(resourceLocation).orElse(null);
            return type == null ? null : mc().level == null ? null : type.create(mc().level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        });

        invalidEntity = this.entity == null;
        if (invalidEntities.contains(entityName)) {
            invalidEntity = true;
        }
    }

    public GuiEntityRenderer setRotationSpeed(float rotationSpeed) {
        this.rotationSpeed = () -> rotationSpeed;
        return this;
    }

    public GuiEntityRenderer setRotationSpeed(Supplier<Float> rotationSpeed) {
        this.rotationSpeed = rotationSpeed;
        return this;
    }

    public float getRotationSpeed() {
        return rotationSpeed.get();
    }

    public GuiEntityRenderer setLockedRotation(float lockedRotation) {
        this.lockedRotation = () -> lockedRotation;
        return this;
    }

    public GuiEntityRenderer setLockedRotation(Supplier<Float> lockedRotation) {
        this.lockedRotation = lockedRotation;
        return this;
    }

    public float getLockedRotation() {
        return lockedRotation.get();
    }

    public GuiEntityRenderer setRotationLocked(boolean rotationLocked) {
        this.rotationLocked = () -> rotationLocked;
        return this;
    }

    public GuiEntityRenderer setRotationLocked(Supplier<Boolean> rotationLocked) {
        this.rotationLocked = rotationLocked;
        return this;
    }

    public boolean isRotationLocked() {
        return rotationLocked.get();
    }

    public GuiEntityRenderer setTrackMouse(boolean trackMouse) {
        this.trackMouse = () -> trackMouse;
        return this;
    }

    public GuiEntityRenderer setTrackMouse(Supplier<Boolean> trackMouse) {
        this.trackMouse = trackMouse;
        return this;
    }

    public boolean isTrackMouse() {
        return trackMouse.get();
    }

    public GuiEntityRenderer setDrawName(boolean drawName) {
        this.drawName = () -> drawName;
        return this;
    }

    public GuiEntityRenderer setDrawName(Supplier<Boolean> drawName) {
        this.drawName = drawName;
        return this;
    }

    public boolean isDrawName() {
        return drawName.get();
    }

    public GuiEntityRenderer setForce2dSize(boolean force2dSize) {
        this.force2dSize = force2dSize;
        return this;
    }

    @Override
    public double getBackgroundDepth() {
        if(cachedEntity&&(cacheLevel!=mc().level||entity==null||entity.level()!=mc().level))refreshCachedEntity();
        Rectangle rect = getRectangle();
        if (invalidEntity || entity == null) return 0.01;
        float scale = (float) (force2dSize ? (Math.min(rect.height() / entity.getBbHeight(), rect.width() / entity.getBbWidth())) : rect.height() / entity.getBbHeight());
        if (Float.isInfinite(scale)) scale = 1;
        return scale * 2;
    }

    @Override
    public void renderBackground(GuiRender render, double mouseX, double mouseY, float partialTicks) {
        if(cachedEntity&&(cacheLevel!=mc().level||entity==null||entity.level()!=mc().level))refreshCachedEntity();
        if (invalidEntity) return;

        try {
            if (entity != null) {
                Rectangle rect = getRectangle();
                float scale = (float) (force2dSize ? (Math.min(rect.height() / entity.getBbHeight(), rect.width() / entity.getBbWidth())) : rect.height() / entity.getBbHeight());
                float xPos = (float) (rect.x() + (rect.width() / 2D));
                float yPos = (float) ((yMin() + (ySize() / 2)) + (rect.height() / 2));
                float rotation = rotationLocked.get() ? lockedRotation.get() : (CCRenderEventHandler.renderTime + partialTicks) * rotationSpeed.get();
                if (entity instanceof LivingEntity living) {
                    int eyeOffset = (int) ((entity.getEyeHeight()) * scale);
                    if (trackMouse.get()) {
                        renderEntityInInventoryFollowsMouse(render, xPos, yPos, scale, force2dSize, xPos - (float) mouseX, yPos - (float) mouseY - eyeOffset, living);
                    } else {
                        renderEntityInInventoryWithRotation(render, xPos, yPos, scale, force2dSize, rotation, living);
                    }
                } else {
                    Quaternionf quaternionf = new Quaternionf().rotateZ((float) Math.PI);
                    Quaternionf quaternionf1 = Axis.YP.rotationDegrees(rotation);
                    quaternionf.mul(quaternionf1);
                    renderEntityInInventory(render, xPos, yPos, scale, force2dSize, quaternionf, quaternionf1, entity);
                }
            }
        } catch (Throwable e) {
            invalidEntity = true;
            invalidEntities.add(entityName);
            LOGGER.error("Failed to render entity in GUI. This is not a bug there are just some entities that can not be rendered like this.");
            LOGGER.error("Entity: " + entity, e);
        }
    }

    public static void renderEntityInInventoryFollowsMouse(GuiRender render, double pX, double pY, double pScale, float offsetX, float offsetY, LivingEntity pEntity) {
        renderEntityInInventoryFollowsMouse(render, pX, pY, pScale, false, offsetX, offsetY, pEntity);
    }

    public static void renderEntityInInventoryFollowsMouse(GuiRender render, double pX, double pY, double pScale, boolean flat, float offsetX, float offsetY, LivingEntity pEntity) {
        float xAngle = (float) Math.atan(offsetX / 40.0F);
        float yAngle = (float) Math.atan(offsetY / 40.0F);
        renderEntityInInventoryFollowsAngle(render, pX, pY, pScale, flat, xAngle, yAngle, pEntity);
    }

    public static void renderEntityInInventoryFollowsAngle(GuiRender render, double pX, double pY, double pScale, float angleX, float angleY, LivingEntity pEntity) {
        renderEntityInInventoryFollowsAngle(render, pX, pY, pScale, false, angleX, angleY, pEntity);
    }

    public static void renderEntityInInventoryFollowsAngle(GuiRender render, double pX, double pY, double pScale, boolean flat, float angleX, float angleY, LivingEntity pEntity) {
        Quaternionf quaternionf = (new Quaternionf()).rotateZ((float) Math.PI);
        Quaternionf quaternionf1 = (new Quaternionf()).rotateX(angleY * 20.0F * ((float) Math.PI / 180F));
        quaternionf.mul(quaternionf1);
        var state=extractState(pEntity);
        if(state instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState living){
            living.bodyRot=180+angleX*20;
            living.yRot=angleX*20;
            living.xRot=-angleY*20;
        }
        submitEntity(render,pX,pY,pScale,flat,quaternionf,quaternionf1,state);
    }

    public static void renderEntityInInventoryWithRotation(GuiRender render, double xPos, double yPos, double scale, double rotation, LivingEntity living) {
        renderEntityInInventoryWithRotation(render, xPos, yPos, scale, false, rotation, living);
    }

    public static void renderEntityInInventoryWithRotation(GuiRender render, double xPos, double yPos, double scale, boolean flat, double rotation, LivingEntity living) {
        Quaternionf quaternionf = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf quaternionf1 = Axis.YP.rotationDegrees((float) rotation);
        quaternionf.mul(quaternionf1);
        var state=extractState(living);
        if(state instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState model){
            model.bodyRot=180;model.yRot=0;model.xRot=0;
        }
        submitEntity(render,xPos,yPos,scale,flat,quaternionf,quaternionf1,state);
    }

    public static void renderEntityInInventory(GuiRender render, double pX, double pY, double pScale, Quaternionf quat, @Nullable Quaternionf pCameraOrientation, Entity pEntity) {
        renderEntityInInventory(render, pX, pY, pScale, false, quat, pCameraOrientation, pEntity);
    }

    public static void renderEntityInInventory(GuiRender render, double pX, double pY, double pScale, boolean flat, Quaternionf quat, @Nullable Quaternionf pCameraOrientation, Entity pEntity) {
        submitEntity(render,pX,pY,pScale,flat,quat,pCameraOrientation,extractState(pEntity));
    }

    private static net.minecraft.client.renderer.entity.state.EntityRenderState extractState(Entity entity){
        var renderer=Minecraft.getInstance().getEntityRenderDispatcher();
        var state=renderer.extractEntity(entity,1);
        state.shadowPieces.clear();
        state.shadowRadius=0;
        state.outlineColor=0;
        state.lightCoords=15728880;
        return state;
    }

    private static void submitEntity(GuiRender render,double x,double y,double scale,boolean flat,Quaternionf rotation,
            @Nullable Quaternionf camera,net.minecraft.client.renderer.entity.state.EntityRenderState state){
        if(!Double.isFinite(scale)||scale<=0)return;
        double halfWidth=Math.max(1,state.boundingBoxWidth*scale/2);
        double height=Math.max(1,state.boundingBoxHeight*scale);
        int x0=(int)Math.floor(x-halfWidth),x1=(int)Math.ceil(x+halfWidth);
        int y0=(int)Math.floor(y-height),y1=(int)Math.ceil(y);
        var translation=new org.joml.Vector3f(0,state.boundingBoxHeight/2,0);
        render.withNativePose(()->{
            var graphics=render.graphics();
            var preview=new net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState(state,translation,new Quaternionf(rotation),
                    camera==null?null:new Quaternionf(camera),x0,y0,x1,y1,(float)scale,graphics.peekScissorStack());
            ((codechicken.lib.gui.modular.lib.GuiEntityDepth)(Object)preview).ccl$setFlat(flat);
            ((codechicken.lib.internal.mixin.GuiGraphicsExtractorAccessor)graphics).ccl$getGuiRenderState().addPicturesInPictureState(preview);
        });
    }
}