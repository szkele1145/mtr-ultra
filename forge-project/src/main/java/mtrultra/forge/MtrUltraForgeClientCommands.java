package mtrultra.forge;

import com.mojang.brigadier.CommandDispatcher;
import mtrultra.MtrUltraConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * [0.2.2 Forge] /mtrultra screen 客户端命令：打开原生配置屏。
 * <p>
 * Forge 生态没有 ModMenu 等价物，通过客户端命令打开同款配置屏
 * （纯原版控件，零额外依赖）。服务端 /mtrultra stats 由
 * {@link MtrUltraForgeCommands} 提供。
 */
@Mod.EventBusSubscriber(modid = "mtr_turbo", value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class MtrUltraForgeClientCommands {

	private MtrUltraForgeClientCommands() {
	}

	@SubscribeEvent
	public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
		final CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
		dispatcher.register(Commands.literal("mtrturbo")
				.then(Commands.literal("screen")
						.executes(context -> {
							Minecraft.getInstance().tell(() -> Minecraft.getInstance().setScreen(new MtrUltraForgeConfigScreen(null)));
							return 1;
						})));
	}

	/**
	 * 原生控件配置屏（与 Fabric 版 ModMenu 屏同款布局）。
	 */
	public static class MtrUltraForgeConfigScreen extends Screen {

		private final Screen parent;

		public MtrUltraForgeConfigScreen(Screen parent) {
			super(Component.literal("MTR-Turbo Settings"));
			this.parent = parent;
		}

		@Override
		protected void init() {
			final int x0 = this.width / 2 - 120;
			final int w = 240;
			int y = 40;

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
					MtrUltraConfig.simulationLodNearBlocks = Math.min(32 + (int) (this.value * 992), MtrUltraConfig.simulationLodMidBlocks - 1);
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
					MtrUltraConfig.simulationLodMidBlocks = Math.max(64 + (int) (this.value * 1984), MtrUltraConfig.simulationLodNearBlocks + 1);
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

			addRenderableWidget(Button.builder(Component.literal("Schedule period: every " + MtrUltraConfig.scheduleRebuildTicks + " ticks"), b -> {
				int p = MtrUltraConfig.scheduleRebuildTicks;
				MtrUltraConfig.scheduleRebuildTicks = p == 20 ? 40 : p == 40 ? 60 : p == 60 ? 10 : 20;
				b.setMessage(Component.literal("Schedule period: every " + MtrUltraConfig.scheduleRebuildTicks + " ticks"));
				MtrUltraConfig.save();
			}).bounds(x0, y, w, 20).build());
			y += 28;

			addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(x0, Math.min(y, this.height - 28), w, 20).build());
		}

		private static Component toggleLabel(String name, boolean on) {
			return Component.literal((on ? "[ON]  " : "[OFF]  ") + name);
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
