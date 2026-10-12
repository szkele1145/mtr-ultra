package mtrultra.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import mtrultra.MtrUltraConfig;
import mtrultra.MtrUltraStats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * [U1] scanDoors 开门窗口短路。
 * <p>
 * 原版 Train.calculateCar 每车每 tick 无条件执行 scanDoors 两次（左右各一），
 * 每次扫描 3×6×(spacing+1)≈600+ 格方块。其返回值只在 doorValue>0（开门动画期间）
 * 才有语义（两侧 boolean && doorValue>0 才传给渲染回调），列车移动中是纯浪费。
 * <p>
 * 短路条件：仅开门窗口（doorTarget 已激活或 doorValue 非 0）放行扫描，否则直接 false
 * ——与"原逻辑 doorValue==0 时结果恒为 false"等价；doorTarget=true 的第一 tick 即
 * 恢复扫描，PSD 门 setOpen 驱动不丢失。
 * <p>
 * 挂点：Train.calculateCar 内两次 scanDoors 调用（字节码仅此两处、参数一致）。
 * MixinExtras @ModifyExpressionValue 同时覆盖两次调用，无需 ordinal 区分。
 */
@Mixin(targets = "mtr.data.Train", remap = false)
public abstract class TrainMixin {

	@Shadow
	protected boolean doorTarget;

	@Shadow
	protected float doorValue;

	@ModifyExpressionValue(
			method = "calculateCar",
			at = @At(value = "INVOKE", target = "Lmtr/data/Train;scanDoors(Lnet/minecraft/class_1937;DDDFFDI)Z")
	)
	private boolean mtrultra$shortCircuitScanDoors(boolean original) {
		if (!MtrUltraConfig.enableDoorScanShortCircuit) {
			return original;
		}
		// 开门窗口外直接短路（等价于原逻辑在 doorValue==0 时的结果）
		if (doorTarget || doorValue > 0) {
			return original;
		}
		MtrUltraStats.doorScanSkipped.incrementAndGet();
		return false;
	}
}
