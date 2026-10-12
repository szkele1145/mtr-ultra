package mtrultra;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * MTR-Ultra 配置：每项优化独立开关（默认全开），JSON 持久化。
 * 出问题时可逐项关闭定位，或整体回退原版行为。
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
            configPath = FabricLoader.getInstance().getConfigDir().resolve("mtr-ultra.json");
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
                // [0.2.2] 加载期一致性钳制：LOD 分级三目逻辑要求 near < mid；
                // 手编 JSON 可能写出 near>=mid（此时分级永假=全部按 mid 档处理），
                // scheduleRebuildTicks 至少为 1（0 会取模崩溃）
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
