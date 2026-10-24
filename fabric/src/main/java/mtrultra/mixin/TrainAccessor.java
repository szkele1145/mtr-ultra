package mtrultra.mixin;

import mtr.path.PathData;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/**
 * [1.1.8 真机修复] {@code mtr.data.Train.isPlayerRiding(Player)} 的读取入口。
 *
 * <p><b>为什么不能用 {@code @Shadow}</b>：
 * Mixin 的 {@code @Shadow} 成员解析走
 * {@code TargetClassContext.findMethod(Deque, desc)}，它只遍历
 * <b>目标类自身</b> 的 {@code classNode.methods} 列表，<b>不查父类</b>。
 * {@code isPlayerRiding} 定义在父类 {@code mtr.data.Train} 上，
 * {@code mtr.data.TrainClient} / {@code mtr.data.TrainServer} 只是继承它
 * ⇒ 此前在 {@code TrainClientMixin} / {@code TrainServerMixin} 里
 * {@code @Shadow public abstract boolean isPlayerRiding(Player)} 每次启动都报：
 * <pre>
 * InvalidMixinException: @Shadow method isPlayerRiding in mtr-ultra.mixins.json:TrainClientMixin
 *   from mod mtr-turbo was not located in the target class mtr.data.TrainClient
 * </pre>
 * ⇒ 这两个 mixin 的对应功能<b>从未生效过</b>（车厢级剔除 / LOD 模拟节流实际是空转）。
 *
 * <p><b>修法</b>：本 mixin 的目标类是 {@code mtr.data.Train}（<b>方法自身的类</b>），
 * 于是 {@code @Accessor} 能正确定位到该方法；调用方通过
 * {@code ((Train) this).isPlayerRiding(player)} 走普通继承调用。
 *
 * <p>目标签名经 6 个目标 jar（MTR 3.2.2-hf1 / YMTR 3.6.x × fabric/forge）
 * 逐一 javap 核实一致：
 * {@code public final boolean isPlayerRiding(Player)}。
 */
@Mixin(targets = "mtr.data.Train", remap = false)
public interface TrainAccessor {

	/**
	 * {@code mtr.data.Train.isPlayerRiding(Player)}（{@code public final boolean}）。
	 * <p>用 {@code @Invoker} 而非 {@code @Accessor}：目标是 **final** 方法，
	 * @Accessor 只支持可覆写成员。
	 * <p>本 accessor 目标类是 {@code mtr.data.Train}（方法自身所在类），
	 * 而非 TrainClient / TrainServer —— 因为 Mixin 的 @Shadow / @Accessor
	 * 都只在**目标类自身**的成员表里查找，**不查父类**。
	 */
	@Invoker("isPlayerRiding")
	boolean mtrultra$isPlayerRiding(Player player);

	/**
	 * {@code mtr.data.Train.path}（{@code public final List<PathData>}）。
	 * <p>与 isPlayerRiding 同因（@Shadow 不查父类）而无法直接声明在 TrainServerMixin，
	 * 改由本 accessor（目标是字段自身所在的父类）暴露。
	 */
	@Accessor("path")
	List<PathData> mtrultra$getPath();

	/**
	 * {@code mtr.data.Train.isCurrentlyManual}（{@code protected boolean} 字段）。
	 * <p>与上面两个成员同因（@Shadow 不查父类）而无法直接声明在 TrainServerMixin。
	 */
	@Accessor("isCurrentlyManual")
	boolean mtrultra$isCurrentlyManual();
}
