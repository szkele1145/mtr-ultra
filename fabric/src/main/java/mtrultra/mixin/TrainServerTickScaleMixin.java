package mtrultra.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * [U4 伴生] 把 TrainServer.simulateTrain 内传给父类的 ticksElapsed 放大为 LOD 间隔。
 * <p>
 * 字节码核实：TrainServer.simulateTrain(8参) 体内唯一的父类调用是
 * Train.simulateTrain(Level, float, Depot)——ticksElapsed 作为第 2 参（index=1）传入。
 * 全部模拟推进（速度积分/停靠计时/门动画）都发生在这条父类调用内，
 * 在该调用点把 float 参数乘以 interval 即完成"单次调用多 tick 积分"。
 * <p>
 * @ModifyArg 只改这一个调用点的参数——方法体内其他对 ticksElapsed 的
 * 读取不受影响（排程 arrivalMillis 用的是 currentMillis，与 ticksElapsed 无关）。
 * <p>
 * 间隔值经静态 ThreadLocal 中转（TrainServerMixin HEAD 注入写入；
 * 服务线程串行执行 simulateTrain，无并发问题）。
 */
@Mixin(targets = "mtr.data.TrainServer", remap = false)
public abstract class TrainServerTickScaleMixin {

	/** TrainServerMixin 写入、本类读取的单线程中转变量 */
	static final ThreadLocal<Integer> ULTRA_INTERVAL = ThreadLocal.withInitial(() -> 1);

	/**
	 * 放大父类调用的 ticksElapsed（index=1，0-based）。
	 * 目标调用在 8 参 simulateTrain 内唯一。
	 */
	@ModifyArg(
			method = "simulateTrain",
			at = @At(value = "INVOKE", target = "Lmtr/data/Train;simulateTrain(Lnet/minecraft/class_1937;FLmtr/data/Depot;)V"),
			index = 1
	)
	private float mtrultra$scaleTicksElapsed(float original) {
		final int interval = ULTRA_INTERVAL.get();
		return interval > 1 ? original * interval : original;
	}
}
