package mtrultra.mixin;

import mtr.data.TrainClient;
import mtrultra.MtrUltraConfig;
import mtrultra.MtrUltraStats;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
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

	// ★ [1.1.8 真机修复] 此前用 `@Shadow public abstract boolean isPlayerRiding(Player)`
	//   **从未定位成功**，每个客户端启动都报：
	//     InvalidMixinException: @Shadow method isPlayerRiding in mtr-ultra.mixins.json:TrainClientMixin
	//       from mod mtr-turbo was not located in the target class mtr.data.TrainClient
	//   根因：Mixin 的 @Shadow 解析走 TargetClassContext.findMethod(Deque, desc)，
	//   只遍历**目标类自身**的 classNode.methods，**不查父类**；
	//   isPlayerRiding 定义在父类 mtr.data.Train 上，TrainClient 只是继承它。
	//   ⇒ 该 mixin 整个失效，车厢级剔除从未生效。
	//   修法：改用 @Mixin 到 mtr.data.Train 的 @Accessor（见 TrainAccessor），
	//   再经普通继承调用。
	@Unique
	private Vec3 mtrultra$cameraPos = Vec3.ZERO;

	@Unique
	private boolean mtrultra$playerRiding = false;

	/** 读父类 Train 的 isPlayerRiding（@Invoker 桥接，见 TrainAccessor） */
	@Unique
	private boolean mtrultra$isPlayerRiding(Player player) {
		return ((TrainAccessor) this).mtrultra$isPlayerRiding(player);
	}

	/**
	 * TrainClient.simulateTrain 入口：刷新列车级剔除快照（每帧一次）。
	 * RenderTrains 对每列车每帧调用一次 simulateTrain——
	 * simulateCar 的剔除判定直接读这里的两个字段。
	 *
	 * <p><b>★ [1.1.8 真机修复]</b> 此前 handler 声明为
	 * {@code (Object, float, Object, Object, Object, CallbackInfo)}（6 参、参数全是 Object），
	 * 而目标 {@code TrainClient.simulateTrain} 的真实签名是 <b>5 参</b>：
	 * {@code (Level, float, SpeedCallback, AnnouncementCallback, AnnouncementCallback)}
	 * ⇒ 描述符逐字不等 ⇒
	 * {@code InvalidInjectionException: Invalid descriptor ... Expected
	 * (Lnet/minecraft/world/level/Level;FLmtr/data/TrainClient$SpeedCallback;...)V
	 * but found (Ljava/lang/Object;FLjava/lang/Object;...)V}
	 * <br>★ 危害比崩溃更隐蔽：该异常在**资源重载**时抛出 ⇒
	 * {@code Caught error loading resourcepacks, removing all selected resourcepacks}
	 * ⇒ MC 移除全部资源包并反复重试（实测循环 9 次）⇒ 用户看到「一直说重载资源失败」。
	 * <br>修法：handler 参数改为精确类型，参数个数与目标一致（5 参）。
	 * <br>（{@code require = 0} 只降级「注入点找不到」，<b>不</b>降级描述符不匹配 ⇒ 仍致命。）
	 */
	@Inject(method = "simulateTrain", at = @At("HEAD"), require = 0)
	private void mtrultra$refreshSnapshot(Level world, float ticksElapsed, TrainClient.SpeedCallback speedCallback,
			TrainClient.AnnouncementCallback announcementCallback, TrainClient.AnnouncementCallback lightRailAnnouncementCallback,
			CallbackInfo ci) {
		final Minecraft client = Minecraft.getInstance();
		final Player player = client.player;
		mtrultra$cameraPos = client.gameRenderer.getMainCamera().getPosition();
		mtrultra$playerRiding = player != null && mtrultra$isPlayerRiding(player);
	}

	/**
	 * simulateCar HEAD 剔除：超距且非乘坐列车时取消整段（音频+渲染+连接件）。
	 * 参数表按 TrainClient.simulateCar 的实际签名排列（javap 实证 17 参）；
	 * 前三个坐标参数用于距离判定。
	 *
	 * <p><b>★ [1.1.8 真机修复]</b> 首参此前是 {@code Object world}，
	 * 目标首参是 {@code Level} ⇒ 描述符逐字不等 ⇒
	 * {@code InvalidInjectionException: Invalid descriptor} ⇒
	 * 资源重载失败 → MC 移除全部资源包并反复重试。
	 * 修法：全部参数改精确类型。
	 */
	@Inject(method = "simulateCar", at = @At("HEAD"), cancellable = true, require = 0)
	private void mtrultra$cullDistantCar(
			Level world, int ridingCar, float ticksElapsed,
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
