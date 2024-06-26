/*
 * Copyright (c) MoriyaShiine. All Rights Reserved.
 */
package moriyashiine.enchancement.mixin.vanillachanges.rebalancechanneling;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import moriyashiine.enchancement.common.ModConfig;
import net.minecraft.block.LightningRodBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LightningRodBlock.class)
public class LightningRodBlockMixin {
	@ModifyExpressionValue(method = "onProjectileHit", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;isThundering()Z"))
	private boolean enchancement$rebalanceChanneling(boolean value) {
		return value || ModConfig.rebalanceChanneling;
	}
}
