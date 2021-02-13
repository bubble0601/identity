package draylar.identity.cca;

import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent;
import dev.onyxstudios.cca.api.v3.component.tick.ServerTickingComponent;
import draylar.identity.Identity;
import draylar.identity.impl.DimensionsRefresher;
import draylar.identity.mixin.EntityAccessor;
import draylar.identity.mixin.LivingEntityAccessor;
import draylar.identity.registry.Components;
import draylar.identity.registry.EntityTags;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.RavagerEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;

import java.util.Optional;

/**
 * This component provides information about a {@link PlayerEntity}'s identity status.
 *
 * <p>{@link IdentityEntityComponent#identity} being null represents "no identity," and accessors should check for this before using the field.
 */
public class IdentityEntityComponent extends IdentityComponent {

    private final LivingEntity entity;

    public IdentityEntityComponent(LivingEntity entity) {
        this.entity = entity;
    }

    /**
     * Returns the identity associated with the {@link LivingEntity} this component is attached to.
     *
     * <p>Note that this method may return null, which represents "no identity."
     *
     * @return the current {@link LivingEntity} identity associated with this component's player owner, or null if they have no identity equipped
     */
    public LivingEntity getIdentity() {
        return identity;
    }

    /**
     * Sets the identity of this {@link IdentityEntityComponent}.
     *
     * <p>Setting a identity refreshes the player's dimensions/hitbox, and toggles flight capabilities depending on the entity.
     * To clear this component's identity, pass null.
     *
     * @param identity {@link LivingEntity} new identity for this component, or null to clear
     */
    @Override
    public void setIdentity(LivingEntity identity) {
        this.identity = identity;

        // refresh entity hitbox dimensions
        ((DimensionsRefresher) entity).identity_refreshDimensions();

        // Identity is valid and scaling health is on; set entity's max health and current health to reflect identity.
        if (identity != null && Identity.CONFIG.scalingHealth) {
            entity.setHealth(Math.min(entity.getHealth(), identity.getMaxHealth()));
            entity.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(Math.min(Identity.CONFIG.maxHealth, identity.getMaxHealth()));
        }

        // If the identity is null (going back to player), set the player's base health value to 20 (default) to clear old changes.
        if (identity == null) {
            entity.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(20);
        }

        // If the player is riding a Ravager and changes into an Identity that cannot ride Ravagers, kick them off.
        if (entity.getVehicle() instanceof RavagerEntity && (identity == null || !EntityTags.RAVAGER_RIDING.contains(identity.getType()))) {
            entity.stopRiding();
        }

        // sync with client
        Components.CURRENT_IDENTITY.sync(this.entity);
    }

    @Override
    public void serverTick() {
        tickIdentity();
    }

    private void tickIdentity() {
        // todo: maybe items not working because world is client?
        LivingEntity entity = this.entity;
        LivingEntity identity = this.identity;

        // assign basic data to entity from player on server; most data transferring occurs on client
        if (identity != null) {
            identity.setPos(entity.getX(), entity.getY(), entity.getZ());
            identity.setHeadYaw(entity.getHeadYaw());
            identity.setJumping(((LivingEntityAccessor) entity).isJumping());
            identity.setSprinting(entity.isSprinting());
            identity.setStuckArrowCount(entity.getStuckArrowCount());
            identity.setInvulnerable(true);
            identity.setNoGravity(true);
            identity.setSneaking(entity.isSneaking());
            identity.setSwimming(entity.isSwimming());
            identity.setCurrentHand(entity.getActiveHand());
            identity.setPose(entity.getPose());
            identity.setOnFireFor(entity.getFireTicks());

            if (identity instanceof TameableEntity) {
                ((TameableEntity) identity).setInSittingPose(entity.isSneaking());
                ((TameableEntity) identity).setSitting(entity.isSneaking());
            }

            if (entity instanceof TameableEntity) {
                identity.setSneaking(((TameableEntity) entity).isSitting());
            }

            ((EntityAccessor) identity).callSetFlag(7, entity.isFallFlying());

            ((LivingEntityAccessor) identity).callTickActiveItemStack();
            Components.CURRENT_IDENTITY.sync(entity);
        }
    }

    @Override
    public void readFromNbt(CompoundTag tag) {
        Optional<EntityType<?>> type = EntityType.fromTag(tag);

        // set identity to null (no identity) if the entity id is "minecraft:empty"
        if (tag.getString("id").equals("minecraft:empty")) {
            this.identity = null;
            ((DimensionsRefresher) entity).identity_refreshDimensions();
        }

        // if entity type was valid, deserialize entity data from tag
        else if (type.isPresent()) {
            CompoundTag entityTag = tag.getCompound("EntityData");

            // ensure entity data exists
            if (entityTag != null) {
                if (identity == null || !type.get().equals(identity.getType())) {
                    identity = (LivingEntity) type.get().create(entity.world);

                    // refresh player dimensions/hitbox on client
                    ((DimensionsRefresher) entity).identity_refreshDimensions();
                }

                identity.fromTag(entityTag);
            }
        }
    }
}
