/*
 * Copyright (c) MoriyaShiine. All Rights Reserved.
 */
package moriyashiine.enchancement.common.component.entity;

import moriyashiine.enchancement.client.EnchancementClient;
import moriyashiine.enchancement.common.event.StepHeightEvent;
import moriyashiine.enchancement.common.init.ModEnchantments;
import moriyashiine.enchancement.common.init.ModSoundEvents;
import moriyashiine.enchancement.common.payload.SlideResetVelocityPayload;
import moriyashiine.enchancement.common.payload.SlideSetVelocityPayload;
import moriyashiine.enchancement.common.payload.SlideSlamPayload;
import moriyashiine.enchancement.common.util.EnchancementUtil;
import moriyashiine.enchancement.mixin.util.accessor.EntityAccessor;
import moriyashiine.enchancement.mixin.util.accessor.LivingEntityAccessor;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.Thickness;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.event.GameEvent;
import org.ladysnake.cca.api.v3.component.tick.CommonTickingComponent;

import java.util.UUID;

public class SlideComponent implements CommonTickingComponent {
	public static final int DEFAULT_JUMP_BOOST_RESET_TICKS = 5, DEFAULT_SLAM_COOLDOWN = 7;

	private static final EntityAttributeModifier SAFE_FALL_DISTANCE_MODIFIER = new EntityAttributeModifier(UUID.fromString("72d836d9-33eb-4a26-a12c-3cba2346d296"), "Enchantment modifier", 6, EntityAttributeModifier.Operation.ADD_VALUE);

	private final PlayerEntity obj;
	private SlideVelocity velocity = SlideVelocity.ZERO;
	private boolean isSlamming = false;
	private int jumpBoostResetTicks = DEFAULT_JUMP_BOOST_RESET_TICKS, slamCooldown = DEFAULT_SLAM_COOLDOWN, ticksLeftToJump = 0, ticksSliding = 0;

	private int slideLevel = 0;
	private boolean hasSlide = false;

	private boolean wasPressingSlamKey = false;

	public SlideComponent(PlayerEntity obj) {
		this.obj = obj;
	}

	@Override
	public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
		velocity = new SlideVelocity(tag.getFloat("VelocityX"), tag.getFloat("VelocityZ"));
		isSlamming = tag.getBoolean("IsSlamming");
		jumpBoostResetTicks = tag.getInt("JumpBoostResetTicks");
		slamCooldown = tag.getInt("SlamCooldown");
		ticksLeftToJump = tag.getInt("TicksLeftToJump");
		ticksSliding = tag.getInt("TicksSliding");
	}

	@Override
	public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
		tag.putFloat("VelocityX", velocity.x());
		tag.putFloat("VelocityZ", velocity.z());
		tag.putBoolean("IsSlamming", isSlamming);
		tag.putInt("JumpBoostResetTicks", jumpBoostResetTicks);
		tag.putInt("SlamCooldown", slamCooldown);
		tag.putInt("TicksLeftToJump", ticksLeftToJump);
		tag.putInt("TicksSliding", ticksSliding);
	}

	@Override
	public void tick() {
		boolean hasBuoy = EnchancementUtil.hasEnchantment(ModEnchantments.BUOY, obj);
		slideLevel = EnchantmentHelper.getEquipmentLevel(ModEnchantments.SLIDE, obj);
		hasSlide = slideLevel > 0;
		if (hasSlide) {
			if (obj.isSneaking() || (obj.isTouchingWater() && !hasBuoy)) {
				velocity = SlideVelocity.ZERO;
			}
			if (slamCooldown > 0) {
				slamCooldown--;
			}
			if (ticksLeftToJump > 0) {
				ticksLeftToJump--;
			}
			if (isSliding()) {
				((EntityAccessor) obj).enchancement$spawnSprintingParticles();
				obj.getWorld().emitGameEvent(GameEvent.STEP, obj.getPos(), GameEvent.Emitter.of(obj.getSteppingBlockState()));
				double dX = velocity.x(), dZ = velocity.z();
				if (!obj.isOnGround()) {
					dX *= 0.2;
					dZ *= 0.2;
				}
				obj.addVelocity(dX, 0, dZ);
				if (obj.isTouchingWater() && hasBuoy) {
					obj.setVelocity(obj.getVelocity().getX(), slideLevel * 0.25, obj.getVelocity().getZ());
				}
				if (ticksSliding < 60) {
					ticksSliding++;
				}
			} else if (ticksSliding > 0) {
				ticksSliding = Math.max(0, ticksSliding - 4);
			}
		} else {
			velocity = SlideVelocity.ZERO;
			isSlamming = false;
			jumpBoostResetTicks = DEFAULT_JUMP_BOOST_RESET_TICKS;
			slamCooldown = DEFAULT_SLAM_COOLDOWN;
			ticksLeftToJump = 0;
			ticksSliding = 0;
		}
	}

	@Override
	public void serverTick() {
		tick();
		if (hasSlide && isSlamming) {
			slamTick(() -> {
				obj.getWorld().getOtherEntities(obj, new Box(obj.getBlockPos()).expand(5, 1, 5), foundEntity -> foundEntity.isAlive() && foundEntity.distanceTo(obj) < 5).forEach(entity -> {
					if (entity instanceof LivingEntity living && EnchancementUtil.shouldHurt(obj, living)) {
						living.takeKnockback(1, obj.getX() - living.getX(), obj.getZ() - living.getZ());
					}
				});
				obj.getWorld().emitGameEvent(GameEvent.STEP, obj.getPos(), GameEvent.Emitter.of(obj.getSteppingBlockState()));
				BlockState state = obj.getWorld().getBlockState(obj.getLandingPos());
				if (state.contains(Properties.THICKNESS) && state.contains(Properties.VERTICAL_DIRECTION) && state.get(Properties.THICKNESS) == Thickness.TIP && state.get(Properties.VERTICAL_DIRECTION) == Direction.UP) {
					obj.damage(obj.getDamageSources().stalagmite(), Integer.MAX_VALUE);
				}
			});
			EnchancementUtil.PACKET_IMMUNITIES.put(obj, 20);
		}
		EntityAttributeInstance safeFallDistanceAttribute = obj.getAttributeInstance(EntityAttributes.GENERIC_SAFE_FALL_DISTANCE);
		if (hasSlide && isSliding()) {
			StepHeightEvent.ENTITIES.put(this, obj);
			if (!safeFallDistanceAttribute.hasModifier(SAFE_FALL_DISTANCE_MODIFIER)) {
				safeFallDistanceAttribute.addPersistentModifier(SAFE_FALL_DISTANCE_MODIFIER);
			}
			EnchancementUtil.PACKET_IMMUNITIES.put(obj, 20);
		} else {
			StepHeightEvent.ENTITIES.remove(this);
			if (safeFallDistanceAttribute.hasModifier(SAFE_FALL_DISTANCE_MODIFIER)) {
				safeFallDistanceAttribute.removeModifier(SAFE_FALL_DISTANCE_MODIFIER);
			}
		}
	}

	@Override
	public void clientTick() {
		tick();
		if (hasSlide && !obj.isSpectator() && obj == MinecraftClient.getInstance().player) {
			if (isSlamming) {
				slamTick(() -> {
					BlockPos.Mutable mutable = new BlockPos.Mutable();
					for (int i = 0; i < 360; i += 15) {
						for (int j = 1; j < 5; j++) {
							double x = obj.getX() + MathHelper.sin(i) * j / 2, z = obj.getZ() + MathHelper.cos(i) * j / 2;
							BlockState state = obj.getWorld().getBlockState(mutable.set(x, Math.round(obj.getY() - 1), z));
							if (!state.isReplaceable() && obj.getWorld().getBlockState(mutable.move(Direction.UP)).isReplaceable()) {
								obj.getWorld().addParticle(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), x, mutable.getY(), z, 0, 0, 0);
							}
						}
					}
				});
			}
			GameOptions options = MinecraftClient.getInstance().options;
			if (EnchancementClient.SLIDE_KEYBINDING.isPressed() && !obj.isSneaking() && !((LivingEntityAccessor) obj).enchancement$jumping()) {
				if (canSlide()) {
					velocity = getVelocityFromInput(options).rotateY((float) Math.toRadians(-(obj.getHeadYaw() + 90)));
					SlideSetVelocityPayload.send(velocity);
				}
			} else if (velocity != SlideVelocity.ZERO) {
				velocity = SlideVelocity.ZERO;
				SlideResetVelocityPayload.send();
			}
			boolean pressingSlamKey = EnchancementClient.SLAM_KEYBINDING.isPressed();
			if (pressingSlamKey && !wasPressingSlamKey && canSlam()) {
				isSlamming = true;
				slamCooldown = DEFAULT_SLAM_COOLDOWN;
				SlideSlamPayload.send();
			}
			wasPressingSlamKey = pressingSlamKey;
		} else {
			wasPressingSlamKey = false;
		}
	}

	public void setVelocity(SlideVelocity velocity) {
		this.velocity = velocity;
	}

	public void setSlamming(boolean slamming) {
		this.isSlamming = slamming;
	}

	public boolean isSlamming() {
		return isSlamming;
	}

	public void setSlamCooldown(int slamCooldown) {
		this.slamCooldown = slamCooldown;
	}

	public boolean isSliding() {
		return !velocity.equals(SlideVelocity.ZERO);
	}

	public boolean shouldBoostJump() {
		return ticksLeftToJump > 0;
	}

	public float getJumpBonus() {
		return MathHelper.lerp(ticksSliding / 60F, 1F, 3F);
	}

	public int getSlideLevel() {
		return slideLevel;
	}

	public boolean hasSlide() {
		return hasSlide;
	}

	public boolean canSlide() {
		return !isSliding() && obj.isOnGround() && EnchancementUtil.isGroundedOrAirborne(obj);
	}

	public boolean canSlam() {
		return slamCooldown == 0 && !isSliding() && !obj.isOnGround() && EnchancementUtil.isGroundedOrAirborne(obj);
	}

	private void slamTick(Runnable onLand) {
		obj.setVelocity(obj.getVelocity().getX() * 0.98, -3, obj.getVelocity().getZ() * 0.98);
		obj.fallDistance = 0;
		if (obj.isOnGround()) {
			isSlamming = false;
			ticksLeftToJump = 5;
			obj.playSound(ModSoundEvents.ENTITY_GENERIC_IMPACT, 1, 1);
			onLand.run();
		}
	}

	private SlideVelocity getVelocityFromInput(GameOptions options) {
		boolean any = false, forward = false, sideways = false;
		int x = 0, z = 0;
		if (options.forwardKey.isPressed()) {
			any = true;
			forward = true;
			x = 1;
		}
		if (options.backKey.isPressed()) {
			any = true;
			forward = true;
			x = -1;
		}
		if (options.leftKey.isPressed()) {
			any = true;
			sideways = true;
			z = -1;
		}
		if (options.rightKey.isPressed()) {
			any = true;
			sideways = true;
			z = 1;
		}
		return new SlideVelocity(any ? x : 1, z).multiply(forward && sideways ? 0.75F : 1).multiply(slideLevel * 0.25F);
	}

	public record SlideVelocity(float x, float z) {
		public static final SlideVelocity ZERO = new SlideVelocity(0, 0);

		SlideVelocity multiply(float value) {
			return new SlideVelocity(x() * value, z() * value);
		}

		SlideVelocity rotateY(float angle) {
			float cos = MathHelper.cos(angle);
			float sin = MathHelper.sin(angle);
			float nX = x() * cos + z() * sin;
			float nZ = z() * cos - x() * sin;
			return new SlideVelocity(nX, nZ);
		}
	}
}
