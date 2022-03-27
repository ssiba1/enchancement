package moriyashiine.enchancement.mixin.slide;

import moriyashiine.enchancement.common.registry.ModComponents;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(PlayerEntity.class)
public class PlayerEntityMixin {
	@ModifyArg(method = "updatePose", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;setPose(Lnet/minecraft/entity/EntityPose;)V"))
	private EntityPose enchancement$slide(EntityPose value) {
		if (value == EntityPose.CROUCHING && ModComponents.SLIDE.get(this).shouldSlide()) {
			return EntityPose.SWIMMING;
		}
		return value;
	}
}