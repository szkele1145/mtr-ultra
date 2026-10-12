package mtrultra.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * [U2 配套] RenderTrains.maxTrainRenderDistance 的读取入口（Forge 版）。
 * 该字段是 public static int（非 final），本可用 @Mixin+@Shadow 读取；
 * 独立 Accessor 更稳（不与 RenderTrainsMixin 的主注入冲突）。
 * <p>
 * Forge 版差异：无（@Accessor 无 @At target，不涉及映射名）。
 */
@Mixin(targets = "mtr.render.RenderTrains", remap = false)
public interface RenderTrainsAccessor {

	@Accessor("maxTrainRenderDistance")
	static int mtrultra$maxTrainRenderDistance() {
		throw new AssertionError();
	}
}
