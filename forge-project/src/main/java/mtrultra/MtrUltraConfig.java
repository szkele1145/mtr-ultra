package mtrultra;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * MTR-Ultra 配置：每项优化独立开关（默认全开），JSON 持久化。
 * 出问题时可逐项关闭定位，或整体回退原版行为。
 * <p>
 * Forge 版：config 目录改用 FMLPaths.CONFIGDIR（Fabric 版为 FabricLoader.getConfigDir）。
 */
public class MtrUltraConfig {

    // U1: scanDoors 开门窗口短路（服务端+客户端共享基类 Train.calculateCar）
    public static boolean enableDoorScanShortCircuit = true;

    // U2: 车厢级渲染剔除（按用户 MTR 配置的列车渲染距离）
    public static boolean enableCarRenderCulling = true;

    // U3: 轨道去重表零分配
    public static boolean enableRailDedupNoAlloc = true;

    // U4: 服务端模拟 LOD（近=每tick，中≤256格=每2tick，远=每4tick）
    public static boolean enableSimulationLod = true;
    public static int simulationLodNearBlocks = 128;
    public static int simulationLodMidBlocks = 256;

    // U5: 排班重建节流（20 tick = 1 秒一次；客户端同步本就 60 tick 一次）
    public static boolean enableScheduleThrottle = true;
    public static int scheduleRebuildTicks = 20;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path configPath;

    public static void load() {
        try {
            configPath = FMLPaths.CONFIGDIR.get().resolve("mtr-ultra.json");
            if (Files.exists(configPath)) {
                JsonObject obj = GSON.fromJson(Files.readString(configPath), JsonObject.class);
                enableDoorScanShortCircuit = get(obj, "enableDoorScanShortCircuit", enableDoorScanShortCircuit);
                enableCarRenderCulling = get(obj, "enableCarRenderCulling", enableCarRenderCulling);
                enableRailDedupNoAlloc = get(obj, "enableRailDedupNoAlloc", enableRailDedupNoAlloc);
                enableSimulationLod = get(obj, "enableSimulationLod", enableSimulationLod);
                simulationLodNearBlocks = get(obj, "simulationLodNearBlocks", simulationLodNearBlocks);
                simulationLodMidBlocks = get(obj, "simulationLodMidBlocks", simulationLodMidBlocks);
                enableScheduleThrottle = get(obj, "enableScheduleThrottle", enableScheduleThrottle);
                scheduleRebuildTicks = get(obj, "scheduleRebuildTicks", scheduleRebuildTicks);
                // [0.2.2] 一致性钳制: near < mid; period >= 1
                simulationLodNearBlocks = Math.min(simulationLodNearBlocks, simulationLodMidBlocks - 1);
                scheduleRebuildTicks = Math.max(1, scheduleRebuildTicks);
            }
            save(); // 回写补全新字段
        } catch (Exception e) {
            MtrUltraMod.LOGGER.error("Failed to load config, using defaults", e);
        }
    }

    public static void save() {
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("enableDoorScanShortCircuit", enableDoorScanShortCircuit);
            obj.addProperty("enableCarRenderCulling", enableCarRenderCulling);
            obj.addProperty("enableRailDedupNoAlloc", enableRailDedupNoAlloc);
            obj.addProperty("enableSimulationLod", enableSimulationLod);
            obj.addProperty("simulationLodNearBlocks", simulationLodNearBlocks);
            obj.addProperty("simulationLodMidBlocks", simulationLodMidBlocks);
            obj.addProperty("enableScheduleThrottle", enableScheduleThrottle);
            obj.addProperty("scheduleRebuildTicks", scheduleRebuildTicks);
            Files.writeString(configPath, GSON.toJson(obj));
        } catch (Exception e) {
            MtrUltraMod.LOGGER.error("Failed to save config", e);
        }
    }

    private static boolean get(JsonObject obj, String key, boolean def) {
        return obj.has(key) ? obj.get(key).getAsBoolean() : def;
    }

    private static int get(JsonObject obj, String key, int def) {
        return obj.has(key) ? obj.get(key).getAsInt() : def;
    }
}
