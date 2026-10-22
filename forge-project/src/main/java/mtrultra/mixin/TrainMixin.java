package mtrultra.mixin;

import mtrultra.MtrUltraConfig;
import mtrultra.MtrUltraStats;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * [U1] scanDoors 开门窗口短路（Forge 版）。
 * <p>
 * 原版 Train.calculateCar 每车每 tick 无条件执行 scanDoors 两次（左右各一），
 * 每次扫描 3×6×(spacing+1)≈600+ 格方块。其返回值只在 doorValue>0（开门动画期间）
 * 才有语义（两侧 boolean && doorValue>0 才传给渲染回调），列车移动中是纯浪费。
 * <p>
 * 短路条件：仅开门窗口（doorTarget 已激活或 doorValue 非 0）放行扫描，否则直接 false
 * ——与"原逻辑 doorValue==0 时结果恒为 false"等价；doorTarget=true 的第一 tick 即
 * 恢复扫描，PSD 门 setOpen 驱动不丢失。
 * <p>
 * 挂点：Train.calculateCar 内两次 scanDoors 调用（字节码偏移 162/204，参数一致）。
 * <p>
 * Forge 版实现差异：Forge 47 无 MixinExtras（ymtr-forge 不捆绑、内置 Mixin 0.8.4 无
 * @ModifyExpressionValue），改用原生 @Redirect + 无 ordinal 宽匹配（同签名调用点仅
 * 此两处，redirect 要求全匹配且逐点回调，语义等价）。开门窗口外不调原方法直接
 * 返回 false；窗口内透传原方法返回值。
 */
@Mixin(targets = "mtr.data.Train", remap = false)
public abstract class TrainMixin {

	@Shadow
	protected boolean doorTarget;

	@Shadow
	protected float doorValue;

	// scanDoors 是 Train 的 private 方法，shadow 后可在 redirect handler 内转发原调用
	@Shadow
	private boolean scanDoors(Level world, double x, double y, double z, float checkWidth, float checkHeight, double yaw, int blocksToCheck) {
		throw new AssertionError();
	}

	@Redirect(
			method = "calculateCar",
			at = @At(value = "INVOKE", target = "Lmtr/data/Train;scanDoors(Lnet/minecraft/world/level/Level;DDDFFDI)Z"),
			// [修复 2026-10-22] 补 require=0：MTR 升级改 scanDoors 签名时
			// 注入失败仅告警不崩（短路失效 = 退回原版每 tick 扫门，行为退化但不崩）。
			require = 0
	)
	private boolean mtrultra$shortCircuitScanDoors(TrainMixin instance, Level world, double x, double y, double z, float checkWidth, float checkHeight, double yaw, int blocksToCheck) {
		if (!MtrUltraConfig.enableDoorScanShortCircuit) {
			return scanDoors(world, x, y, z, checkWidth, checkHeight, yaw, blocksToCheck);
		}
		// 开门窗口外直接短路（等价于原逻辑在 doorValue==0 时的结果）
		if (doorTarget || doorValue > 0) {
			return scanDoors(world, x, y, z, checkWidth, checkHeight, yaw, blocksToCheck);
		}
		MtrUltraStats.doorScanSkipped.incrementAndGet();
		return false;
	}
}
