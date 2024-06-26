/*
 * Copyright (c) MoriyaShiine. All Rights Reserved.
 */
package moriyashiine.enchancement.common.component.entity;

import moriyashiine.enchancement.common.event.StepHeightEvent;
import moriyashiine.enchancement.common.init.ModEnchantments;
import moriyashiine.enchancement.common.init.ModEntityComponents;
import moriyashiine.enchancement.common.util.EnchancementUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.CommonTickingComponent;

import java.util.UUID;

public class ExtendedWaterComponent implements AutoSyncedComponent, CommonTickingComponent {
	private static final EntityAttributeModifier SAFE_FALL_DISTANCE_MODIFIER = new EntityAttributeModifier(UUID.fromString("5a6cc485-ad0c-47d0-941e-a6011801bc34"), "Enchantment modifier", 4, EntityAttributeModifier.Operation.ADD_VALUE);

	private final LivingEntity obj;
	private int ticksWet = 0;

	private boolean hasAmphibious = false, hasBuoy = false;

	public ExtendedWaterComponent(LivingEntity obj) {
		this.obj = obj;
	}

	@Override
	public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
		ticksWet = tag.getInt("TicksWet");
	}

	@Override
	public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
		tag.putInt("TicksWet", ticksWet);
	}

	@Override
	public void tick() {
		hasAmphibious = EnchancementUtil.hasEnchantment(ModEnchantments.AMPHIBIOUS, obj);
		hasBuoy = EnchancementUtil.hasEnchantment(ModEnchantments.BUOY, obj);
		if (shouldCount()) {
			if (obj.isWet()) {
				markWet(200);
			}
		} else {
			ticksWet = 0;
		}
		if (ticksWet > 0) {
			ticksWet--;
			if (hasAmphibious && obj.isOnFire()) {
				obj.extinguishWithSound();
			}
		}
	}

	@Override
	public void serverTick() {
		tick();
		if (obj.age % 10 == 0) {
			boolean increase = false;
			if (ticksWet > 0) {
				if (hasBuoy) {
					increase = true;
				}
				if (!obj.isWet()) {
					obj.getWorld().playSound(null, obj.getBlockPos(), SoundEvents.BLOCK_POINTED_DRIPSTONE_DRIP_WATER, obj.getSoundCategory(), 1, 1);
				}
			}
			EntityAttributeInstance safeFallDistanceAttribute = obj.getAttributeInstance(EntityAttributes.GENERIC_SAFE_FALL_DISTANCE);
			if (increase) {
				StepHeightEvent.ENTITIES.put(this, obj);
				if (!safeFallDistanceAttribute.hasModifier(SAFE_FALL_DISTANCE_MODIFIER)) {
					safeFallDistanceAttribute.addPersistentModifier(SAFE_FALL_DISTANCE_MODIFIER);
				}
			} else {
				StepHeightEvent.ENTITIES.remove(this);
				if (safeFallDistanceAttribute.hasModifier(SAFE_FALL_DISTANCE_MODIFIER)) {
					safeFallDistanceAttribute.removeModifier(SAFE_FALL_DISTANCE_MODIFIER);
				}
			}
		}
	}

	@Override
	public void clientTick() {
		tick();
		if (ticksWet > 0 && !obj.isInvisible() && !obj.isWet()) {
			if (MinecraftClient.getInstance().gameRenderer.getCamera().isThirdPerson() || MinecraftClient.getInstance().player != obj) {
				if (hasAmphibious) {
					obj.getWorld().addParticle(ParticleTypes.FALLING_WATER, obj.getParticleX(1), obj.getY() + obj.getHeight() * MathHelper.nextDouble(obj.getRandom(), 0.4, 0.8), obj.getParticleZ(1), 0, 0, 0);
				}
				if (hasBuoy) {
					obj.getWorld().addParticle(ParticleTypes.FALLING_WATER, obj.getParticleX(1), obj.getY() + obj.getHeight() * 0.15, obj.getParticleZ(1), 0, 0, 0);
				}
			}
		}
	}

	public void sync() {
		ModEntityComponents.EXTENDED_WATER.sync(obj);
	}

	public int getTicksWet() {
		return ticksWet;
	}

	public void markWet(int ticks) {
		if (ticksWet < ticks) {
			ticksWet = ticks;
		}
	}

	public boolean hasAmphibious() {
		return hasAmphibious;
	}

	public boolean shouldCount() {
		return hasAmphibious || hasBuoy;
	}
}
