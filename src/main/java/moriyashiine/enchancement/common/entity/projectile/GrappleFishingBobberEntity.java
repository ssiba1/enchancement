/*
 * Copyright (c) MoriyaShiine. All Rights Reserved.
 */
package moriyashiine.enchancement.common.entity.projectile;

import moriyashiine.enchancement.common.ModConfig;
import moriyashiine.enchancement.common.init.ModEntityComponents;
import moriyashiine.enchancement.common.init.ModEntityTypes;
import moriyashiine.enchancement.common.init.ModSoundEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class GrappleFishingBobberEntity extends FishingBobberEntity {
	public BlockPos grapplePos = null;
	public BlockState grappleState = null;

	public GrappleFishingBobberEntity(EntityType<? extends GrappleFishingBobberEntity> type, World world, int luckOfTheSeaLevel, int lureLevel) {
		super(type, world);
		ignoreCameraFrustum = true;
		this.luckOfTheSeaLevel = Math.max(0, luckOfTheSeaLevel);
		this.lureLevel = Math.max(0, lureLevel);
	}

	public GrappleFishingBobberEntity(EntityType<? extends GrappleFishingBobberEntity> entityType, World world) {
		this(entityType, world, 0, 0);
	}

	@SuppressWarnings("SuspiciousNameCombination")
	public GrappleFishingBobberEntity(PlayerEntity thrower, World world, int luckOfTheSeaLevel, int lureLevel) {
		this(ModEntityTypes.GRAPPLE_FISHING_BOBBER, world, luckOfTheSeaLevel, lureLevel);
		setOwner(thrower);
		float throwerPitch = thrower.getPitch();
		float throwerYaw = thrower.getYaw();
		float x = MathHelper.sin(-throwerYaw * ((float) Math.PI / 180) - (float) Math.PI);
		float z = MathHelper.cos(-throwerYaw * ((float) Math.PI / 180) - (float) Math.PI);
		refreshPositionAndAngles(thrower.getX() - (double) x * 0.3, thrower.getEyeY(), thrower.getZ() - (double) z * 0.3, throwerYaw, throwerPitch);
		Vec3d velocity = new Vec3d(-x, MathHelper.clamp(-(MathHelper.sin(-throwerPitch * ((float) Math.PI / 180)) / -MathHelper.cos(-throwerPitch * ((float) Math.PI / 180))), -5.0f, 5.0f), -z);
		double length = velocity.length();
		velocity = velocity.multiply(0.6 / length + random.nextTriangular(0.5, 0.0103365), 0.6 / length + random.nextTriangular(0.5, 0.0103365), 0.6 / length + random.nextTriangular(0.5, 0.0103365)).multiply(2);
		setVelocity(velocity);
		setYaw((float) (MathHelper.atan2(velocity.x, velocity.z) * 57.2957763671875));
		setPitch((float) (MathHelper.atan2(velocity.y, velocity.horizontalLength()) * 57.2957763671875));
		prevYaw = getYaw();
		prevPitch = getPitch();

		if (ModConfig.accurateFishingBobbers) {
			accurateFishingBobbers(this, thrower, 2.75F);
		}
	}

	@Override
	protected void onBlockHit(BlockHitResult blockHitResult) {
		super.onBlockHit(blockHitResult);
		grapplePos = blockHitResult.getBlockPos();
		grappleState = getWorld().getBlockState(grapplePos);
		if (getWorld().isClient) {
			PlayerEntity owner = getPlayerOwner();
			if (owner != null) {
				owner.playSound(ModSoundEvents.ENTITY_FISHING_BOBBER_GRAPPLE, 1, 1);
			}
		}
	}

	@Override
	protected void onEntityHit(EntityHitResult entityHitResult) {
		super.onEntityHit(entityHitResult);
		if (getWorld().isClient) {
			PlayerEntity owner = getPlayerOwner();
			if (owner != null) {
				owner.playSound(ModSoundEvents.ENTITY_FISHING_BOBBER_GRAPPLE, 1, 1);
			}
		}
	}

	@Override
	protected void pullHookedEntity(Entity entity) {
		Entity owner = getOwner();
		if (owner != null) {
			Vec3d vec3d = new Vec3d(owner.getX() - getX(), owner.getY() - getY(), owner.getZ() - getZ()).multiply(0.1);
			entity.setVelocity(entity.getVelocity().add(vec3d).multiply(2));
			if (!entity.isOnGround()) {
				ModEntityComponents.BOUNCY.maybeGet(entity).ifPresent(bouncyComponent -> bouncyComponent.grappleTimer = 30);
			}
		}
	}

	@Override
	public int use(ItemStack usedItem) {
		int use = super.use(usedItem);
		if (grappleState != null) {
			PlayerEntity player = getPlayerOwner();
			if (player != null) {
				if (!getWorld().isClient) {
					if (getY() > player.getY()) {
						player.setVelocity(player.getVelocity().getX(), 0, player.getVelocity().getZ());
					}
					player.setVelocity(player.getVelocity().add(new Vec3d(Math.min(10, getX() - player.getX()), Math.min(10, getY() - player.getY()), Math.min(10, getZ() - player.getZ())).multiply(0.2)));
					player.velocityModified = true;
				}
				if (!player.isOnGround()) {
					ModEntityComponents.BOUNCY.get(player).grappleTimer = 30;
				}
			}
		}
		return getWorld().isClient ? use : 1;
	}

	@Override
	protected Text getDefaultName() {
		return EntityType.FISHING_BOBBER.getName();
	}

	public static void accurateFishingBobbers(FishingBobberEntity fishingBobber, PlayerEntity thrower, float speed) {
		fishingBobber.refreshPositionAndAngles(thrower.getX(), thrower.getEyeY(), thrower.getZ(), thrower.getYaw(), thrower.getPitch());
		fishingBobber.setVelocity(thrower, thrower.getPitch(), thrower.getYaw(), 0, speed, 0);
	}
}
