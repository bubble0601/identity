package draylar.identity.mixin;

import draylar.identity.Identity;
import draylar.identity.api.model.EntityUpdater;
import draylar.identity.api.model.EntityUpdaters;
import draylar.identity.registry.Components;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity> extends EntityRenderer<T> {
    protected LivingEntityRendererMixin(EntityRenderDispatcher dispatcher) {
        super(dispatcher);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, locals = LocalCapture.CAPTURE_FAILHARD)
    private void onRender(T entity, float f, float g, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i, CallbackInfo ci) {
        LivingEntity identity = Components.CURRENT_IDENTITY.get(entity).getIdentity();

        // sync player data to identity identity
        if(identity != null) {
            identity.lastLimbDistance = entity.lastLimbDistance;
            identity.limbDistance = entity.limbDistance;
            identity.limbAngle = entity.limbAngle;
            identity.handSwinging = entity.handSwinging;
            identity.handSwingTicks = entity.handSwingTicks;
            identity.lastHandSwingProgress = entity.lastHandSwingProgress;
            identity.handSwingProgress = entity.handSwingProgress;
            identity.bodyYaw = entity.bodyYaw;
            identity.prevBodyYaw = entity.prevBodyYaw;
            identity.headYaw = entity.headYaw;
            identity.prevHeadYaw = entity.prevHeadYaw;
            identity.age = entity.age;
            identity.preferredHand = entity.preferredHand;

            ((EntityAccessor) identity).setVehicle(entity.getVehicle());
            ((EntityAccessor) identity).setTouchingWater(entity.isTouchingWater());

            // phantoms' pitch is inverse for whatever reason
            if(identity instanceof PhantomEntity) {
                identity.pitch = -entity.pitch;
                identity.prevPitch = -entity.prevPitch;
            } else {
                identity.pitch = entity.pitch;
                identity.prevPitch = entity.prevPitch;
            }

            // equip held items on identity
            if(Identity.CONFIG.identitiesEquipItems) {
                identity.equipStack(EquipmentSlot.MAINHAND, entity.getEquippedStack(EquipmentSlot.MAINHAND));
                identity.equipStack(EquipmentSlot.OFFHAND, entity.getEquippedStack(EquipmentSlot.OFFHAND));
            }

            // equip armor items on identity
            if(Identity.CONFIG.identitiesEquipArmor) {
                identity.equipStack(EquipmentSlot.HEAD, entity.getEquippedStack(EquipmentSlot.HEAD));
                identity.equipStack(EquipmentSlot.CHEST, entity.getEquippedStack(EquipmentSlot.CHEST));
                identity.equipStack(EquipmentSlot.LEGS, entity.getEquippedStack(EquipmentSlot.LEGS));
                identity.equipStack(EquipmentSlot.FEET, entity.getEquippedStack(EquipmentSlot.FEET));
            }

            if (identity instanceof MobEntity) {
                ((MobEntity) identity).setAttacking(entity.isUsingItem());
            }

            // Assign pose
            identity.setPose(entity.getPose());

            // set active hand after configuring held items
            identity.setCurrentHand(entity.getActiveHand() == null ? Hand.MAIN_HAND : entity.getActiveHand());
            ((LivingEntityAccessor) identity).callSetLivingFlag(1, entity.isUsingItem());
            identity.getItemUseTime();
            ((LivingEntityAccessor) identity).callTickActiveItemStack();

            // update identity specific properties
            EntityUpdater entityUpdater = EntityUpdaters.getUpdater((EntityType<? extends LivingEntity>) identity.getType());
            if(entityUpdater != null) {
                entityUpdater.update(entity, identity);
            }

            // render
            EntityRenderer identityRenderer = MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(identity);
            identityRenderer.render(identity, f, g, matrixStack, vertexConsumerProvider, i);
            ci.cancel();
        }
    }
}
