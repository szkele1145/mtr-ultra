package mtrultra.forge;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * [0.2.2 Forge] /mtrturbo screen 客户端命令：打开原生配置屏。
 * <p>
 * Forge 生态没有 ModMenu 等价物，通过客户端命令打开同款配置屏
 * （纯原版控件，零额外依赖）。服务端 /mtrultra stats 由
 * {@link MtrUltraForgeCommands} 提供。
 * <p>
 * [multi-version] 配置屏实现 {@link MtrUltraForgeConfigScreen} 依赖
 * {@code net.minecraft.client.gui.GuiGraphics}（1.20 才引入），在 1.19.x
 * 构建中不参与编译（build.gradle 的 {@code hasConfigScreen} 开关排除它）。
 * 因此这里用**反射**打开，编译期不依赖该类；1.19.x 运行时给出明确提示，
 * 用户仍可手编 {@code config/mtr-turbo.json}。
 */
@Mod.EventBusSubscriber(modid = "mtr_turbo", value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class MtrUltraForgeClientCommands {

	private static final String CONFIG_SCREEN_CLASS = "mtrultra.forge.MtrUltraForgeConfigScreen";

	private MtrUltraForgeClientCommands() {
	}

	@SubscribeEvent
	public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
		final CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
		dispatcher.register(Commands.literal("mtrturbo")
				.then(Commands.literal("screen")
						.executes(context -> {
							openConfigScreen(context.getSource());
							return 1;
						})));
	}

	private static void openConfigScreen(CommandSourceStack source) {
		final Screen screen;
		try {
			final Class<?> cls = Class.forName(CONFIG_SCREEN_CLASS);
			screen = (Screen) cls.getConstructor(Screen.class).newInstance((Object) null);
		} catch (ClassNotFoundException e) {
			// 1.19.x：配置屏未编译进本版本
			source.sendFailure(Component.literal("[MTR-Turbo] 配置屏仅在 1.20.x 提供；1.19.x 请手编 config/mtr-turbo.json"));
			return;
		} catch (ReflectiveOperationException e) {
			source.sendFailure(Component.literal("[MTR-Turbo] 打开配置屏失败：" + e.getClass().getSimpleName()));
			return;
		}
		Minecraft.getInstance().tell(() -> Minecraft.getInstance().setScreen(screen));
	}
}
