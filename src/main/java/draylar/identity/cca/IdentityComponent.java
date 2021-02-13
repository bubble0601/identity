package draylar.identity.cca;

import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent;
import dev.onyxstudios.cca.api.v3.component.tick.ServerTickingComponent;
import draylar.identity.Identity;
import draylar.identity.impl.DimensionsRefresher;
import draylar.identity.mixin.EntityAccessor;
import draylar.identity.mixin.LivingEntityAccessor;
import draylar.identity.registry.Components;
import draylar.identity.registry.EntityTags;
import io.github.ladysnake.pal.VanillaAbilities;
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
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;

import java.util.Optional;

/**
 * This component provides information about a {@link LivingEntity}'s identity status.
 *
 * <p>{@link IdentityComponent#identity} being null represents "no identity," and accessors should check for this before using the field.
 */
public abstract class IdentityComponent implements AutoSyncedComponent, ServerTickingComponent {
    protected LivingEntity identity = null;

    public abstract LivingEntity getIdentity();
    public abstract void setIdentity(LivingEntity entity);

    @Override
    public void writeToNbt(CompoundTag tag) {
        CompoundTag entityTag = new CompoundTag();

        // serialize current identity data to tag if it exists
        if (identity != null) {
            identity.toTag(entityTag);
        }

        // put entity type ID under the key "id", or "minecraft:empty" if no identity is equipped (or the identity entity type is invalid)
        tag.putString("id", identity == null ? "minecraft:empty" : Registry.ENTITY_TYPE.getId(identity.getType()).toString());
        tag.put("EntityData", entityTag);
    }
}
