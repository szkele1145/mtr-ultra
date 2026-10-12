package mtrultra.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import mtrultra.MtrUltraConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * [0.2.1] ModMenu 配置屏：五项优化开关 + 两个 LOD 距离滑杆。
 * <p>
 * 无 cloth-config 依赖（保持零依赖），用原版控件搭一个简洁屏：
 * 每行一个开关按钮（ON/OFF 切换），LOD 距离用滑杆（32~1024）。
 * 关屏时写回 JSON。
 */
public class MtrUltraModMenuApi implements ModMenuApi {

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return MtrUltraConfigScreen::new;
	}

	public static class MtrUltraConfigScreen extends Screen {

		private final Screen parent;

		public MtrUltraConfigScreen(Screen parent) {
			super(Component.literal("MTR-Ultra Settings"));
			this.parent = parent;
		}

		@Override
		protected void init() {
			final int x0 = this.width / 2 - 120;
			final int w = 240;
			int y = 40;

			// 五个开关
			addRenderableWidget(Button.builder(toggleLabel("Door-scan short-circuit (U1)", MtrUltraConfig.enableDoorScanShortCircuit), b -> {
				MtrUltraConfig.enableDoorScanShortCircuit = !MtrUltraConfig.enableDoorScanShortCircuit;
				b.setMessage(toggleLabel("Door-scan short-circuit (U1)", MtrUltraConfig.enableDoorScanShortCircuit));
				MtrUltraConfig.save();
			}).bounds(x0, y, w, 20).build());
			y += 24;

			addRenderableWidget(Button.builder(toggleLabel("Car render culling (U2)", MtrUltraConfig.enableCarRenderCulling), b -> {
				MtrUltraConfig.enableCarRenderCulling = !MtrUltraConfig.enableCarRenderCulling;
				b.setMessage(toggleLabel("Car render culling (U2)", MtrUltraConfig.enableCarRenderCulling));
				MtrUltraConfig.save();
			}).bounds(x0, y, w, 20).build());
			y += 24;

			addRenderableWidget(Button.builder(toggleLabel("Render container reuse (U3)", MtrUltraConfig.enableRailDedupNoAlloc), b -> {
				MtrUltraConfig.enableRailDedupNoAlloc = !MtrUltraConfig.enableRailDedupNoAlloc;
				b.setMessage(toggleLabel("Render container reuse (U3)", MtrUltraConfig.enableRailDedupNoAlloc));
				MtrUltraConfig.save();
			}).bounds(x0, y, w, 20).build());
			y += 24;

			addRenderableWidget(Button.builder(toggleLabel("Simulation LOD (U4)", MtrUltraConfig.enableSimulationLod), b -> {
				MtrUltraConfig.enableSimulationLod = !MtrUltraConfig.enableSimulationLod;
				b.setMessage(toggleLabel("Simulation LOD (U4)", MtrUltraConfig.enableSimulationLod));
				MtrUltraConfig.save();
			}).bounds(x0, y, w, 20).build());
			y += 28;

			addRenderableWidget(new AbstractSliderButton(x0, y, w, 20, Component.literal("LOD near: " + MtrUltraConfig.simulationLodNearBlocks + " blocks"), (MtrUltraConfig.simulationLodNearBlocks - 32) / 992.0) {
				@Override
				protected void updateMessage() {
					this.setMessage(Component.literal("LOD near: " + MtrUltraConfig.simulationLodNearBlocks + " blocks"));
				}

				@Override
				protected void applyValue() {
					MtrUltraConfig.simulationLodNearBlocks = 32 + (int) (this.value * 992);
					this.updateMessage();
					MtrUltraConfig.save();
				}
			});
			y += 24;

			addRenderableWidget(new AbstractSliderButton(x0, y, w, 20, Component.literal("LOD mid: " + MtrUltraConfig.simulationLodMidBlocks + " blocks"), (MtrUltraConfig.simulationLodMidBlocks - 64) / 1984.0) {
				@Override
				protected void updateMessage() {
					this.setMessage(Component.literal("LOD mid: " + MtrUltraConfig.simulationLodMidBlocks + " blocks"));
				}

				@Override
				protected void applyValue() {
					MtrUltraConfig.simulationLodMidBlocks = 64 + (int) (this.value * 1984);
					this.updateMessage();
					MtrUltraConfig.save();
				}
			});
			y += 28;

			addRenderableWidget(Button.builder(toggleLabel("Schedule throttle (U5)", MtrUltraConfig.enableScheduleThrottle), b -> {
				MtrUltraConfig.enableScheduleThrottle = !MtrUltraConfig.enableScheduleThrottle;
				b.setMessage(toggleLabel("Schedule throttle (U5)", MtrUltraConfig.enableScheduleThrottle));
				MtrUltraConfig.save();
			}).bounds(x0, y, w, 20).build());
			y += 24;

			addRenderableWidget(Button.builder(toggleLabel("Schedule period: every " + MtrUltraConfig.scheduleRebuildTicks + " ticks", null), b -> {
				// 周期循环: 20 → 40 → 60 → 10 → 20
				int p = MtrUltraConfig.scheduleRebuildTicks;
				MtrUltraConfig.scheduleRebuildTicks = p == 20 ? 40 : p == 40 ? 60 : p == 60 ? 10 : 20;
				b.setMessage(toggleLabel("Schedule period: every " + MtrUltraConfig.scheduleRebuildTicks + " ticks", null));
				MtrUltraConfig.save();
			}).bounds(x0, y, w, 20).build());
			y += 28;

			addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(x0, Math.min(y, this.height - 28), w, 20).build());
		}

		private static Component toggleLabel(String name, boolean on) {
			return Component.literal((on ? "[ON]  " : "[OFF]  ") + name);
		}

		private static Component toggleLabel(String name, Object ignored) {
			return Component.literal(name);
		}

		@Override
		public void onClose() {
			MtrUltraConfig.save();
			Minecraft.getInstance().setScreen(parent);
		}

		@Override
		public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
			renderBackground(guiGraphics);
			super.render(guiGraphics, mouseX, mouseY, delta);
			guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 18, 0xFFFFFF);
		}
	}
}
