package mtrultra.mixin;

import mtrultra.MtrUltraConfig;
import mtrultra.MtrUltraStats;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * [U2] 车厢级渲染剔除。
 * <p>
 * 原版 TrainClient.simulateCar 对每节车厢无条件执行：
 * 音频调度（playAllCars，字节码偏移 33/54）→ 渲染（renderCar，偏移 136；
 * 半透明批走 trainTranslucentRenders 回调，偏移 18 的 lambda）→ 连接件渲染（ridingCar>0 时）。
 * <p>
 * 剔除策略：车厢中心与相机 XZ+Y 距离超出 RenderTrains.maxTrainRenderDistance
 * 时跳过整个 simulateCar。音频随之一并跳过——声音系统自带距离衰减，
 * 超距离车厢本就无声，可接受（模拟不受影响：simulateCar 是客户端渲染路径，
 * 服务器侧是 TrainServer.simulateCar，不同类不同方法）。
 * 骑乘者所在列车豁免（自己乘坐的车必须渲染）。
 * <p>
 * 快照：simulateTrain 入口刷新相机位置与乘坐状态（每帧每列车一次），
 * 避免每车厢重复查询相机与遍历乘客。
 */
@Mixin(targets = "mtr.data.TrainClient", remap = false)
public abstract class TrainClientMixin {

	@Shadow
	public abstract boolean isPlayerRiding(Player player);

	@Unique
	private Vec3 mtrultra$cameraPos = Vec3.ZERO;

	@Unique
	private boolean mtrultra$playerRiding = false;

	/**
	 * TrainClient.simulateTrain 入口：刷新列车级剔除快照（每帧一次）。
	 * RenderTrains 对每列车每帧调用一次 simulateTrain——
	 * simulateCar 的剔除判定直接读这里的两个字段。
	 * [修复 2026-10-21] require = 0：MTR 升级导致方法签名变化时
	 * 注入仅失效（剔除退化为距离 0 = 不剔除），不崩客户端。
	 */
	@Inject(method = "simulateTrain", at = @At("HEAD"), require = 0)
	private void mtrultra$refreshSnapshot(Object world, float ticksElapsed, Object speedCallback, Object announcementCallback, Object lightRailAnnouncementCallback, CallbackInfo ci) {
		final Minecraft client = Minecraft.getInstance();
		final Player player = client.player;
		mtrultra$cameraPos = client.gameRenderer.getMainCamera().getPosition();
		mtrultra$playerRiding = player != null && isPlayerRiding(player);
	}

	/**
	 * simulateCar HEAD 剔除：超距且非乘坐列车时取消整段（音频+渲染+连接件）。
	 * 参数表（15 参）按 TrainClient.simulateCar 的实际签名排列；
	 * 前三个坐标参数用于距离判定。
	 * [修复 2026-10-21] require = 0：MTR 升级导致签名变化时仅失效（不剔除），不崩客户端。
	 */
	@Inject(method = "simulateCar", at = @At("HEAD"), cancellable = true, require = 0)
	private void mtrultra$cullDistantCar(
			Object world, int ridingCar, float ticksElapsed,
			double carX, double carY, double carZ, float carYaw, float carPitch,
			double prevCarX, double prevCarY, double prevCarZ, float prevCarYaw, float prevCarPitch,
			boolean doorLeftOpen, boolean doorRightOpen, double realSpacing,
			CallbackInfo ci) {
		if (!MtrUltraConfig.enableCarRenderCulling) {
			return;
		}
		if (mtrultra$playerRiding) {
			return; // 骑乘者列车豁免
		}
		final double dist = RenderTrainsAccessor.mtrultra$maxTrainRenderDistance();
		final double dx = carX - mtrultra$cameraPos.x;
		final double dy = carY - mtrultra$cameraPos.y;
		final double dz = carZ - mtrultra$cameraPos.z;
		if (dx * dx + dy * dy + dz * dz > dist * dist) {
			MtrUltraStats.carsCulled.incrementAndGet();
			ci.cancel();
		}
	}
}
