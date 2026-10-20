package mtrultra;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * MTR Turbo 配置：每项优化独立开关（默认全开），JSON 持久化。
 * 出问题时可逐项关闭定位，或整体回退原版行为。
 */
public class MtrUltraConfig {

    // U1: scanDoors 开门窗口短路（服务端+客户端共享基类 Train.calculateCar）
    public static boolean enableDoorScanShortCircuit = true;

    // U2: 车厢级渲染剔除（按用户 MTR 配置的列车渲染距离）
    public static boolean enableCarRenderCulling = true;

    // U3: 轨道去重表零分配
    public static boolean enableRailDedupNoAlloc = true;

    // [0.3.0 / U6] Rail.getPosition 曲率缓存（0.25 格步长插值，视觉不可辨）
    public static boolean enableRailPositionCache = true;

    // U4: 服务端模拟 LOD（近=每tick，中≤256格=每2tick，远=每4tick）
    public static boolean enableSimulationLod = true;
    public static int simulationLodNearBlocks = 128;
    public static int simulationLodMidBlocks = 256;

    // U5: 排班重建节流（20 tick = 1 秒一次；客户端同步本就 60 tick 一次）
    public static boolean enableScheduleThrottle = true;
    public static int scheduleRebuildTicks = 20;

    // [1.0.5] 周期性把优化命中数写进日志（秒；0=关闭）。
    // 1.18.2/1.19.x 没有命令/配置屏，这是旧版本查看 stats 的唯一入口
    public static int statsLogIntervalSeconds = 0;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path configPath;

    public static void load() {
        try {
            final java.nio.file.Path newPath = FabricLoader.getInstance().getConfigDir().resolve("mtr-turbo.json");
            // [Turbo] 改名迁移：旧 mtr-ultra.json 存在而新文件不存在时自动搬运（保留用户开关设置）
            final java.nio.file.Path oldPath = FabricLoader.getInstance().getConfigDir().resolve("mtr-ultra.json");
            if (!java.nio.file.Files.exists(newPath) && java.nio.file.Files.exists(oldPath)) {
                java.nio.file.Files.copy(oldPath, newPath);
            }
            configPath = newPath;
            if (Files.exists(configPath)) {
                JsonObject obj = GSON.fromJson(Files.readString(configPath), JsonObject.class);
                enableDoorScanShortCircuit = get(obj, "enableDoorScanShortCircuit", enableDoorScanShortCircuit);
                enableCarRenderCulling = get(obj, "enableCarRenderCulling", enableCarRenderCulling);
                enableRailDedupNoAlloc = get(obj, "enableRailDedupNoAlloc", enableRailDedupNoAlloc);
                enableRailPositionCache = get(obj, "enableRailPositionCache", enableRailPositionCache);
                enableSimulationLod = get(obj, "enableSimulationLod", enableSimulationLod);
                simulationLodNearBlocks = get(obj, "simulationLodNearBlocks", simulationLodNearBlocks);
                simulationLodMidBlocks = get(obj, "simulationLodMidBlocks", simulationLodMidBlocks);
                enableScheduleThrottle = get(obj, "enableScheduleThrottle", enableScheduleThrottle);
                scheduleRebuildTicks = get(obj, "scheduleRebuildTicks", scheduleRebuildTicks);
                statsLogIntervalSeconds = get(obj, "statsLogIntervalSeconds", statsLogIntervalSeconds);
                // [0.2.2] 加载期一致性钳制：LOD 分级三目逻辑要求 near < mid；
                // 手编 JSON 可能写出 near>=mid（此时分级永假=全部按 mid 档处理），
                // scheduleRebuildTicks 至少为 1（0 会取模崩溃）
                // [修复 2026-10-21] 原式 near = min(near, mid - 1) 在 mid<=0 时产出非法值：
                //   mid=0 → near=-1；mid=1 → near=0。
                // 随后 TrainServerMixin 用 near*near 比较，(-1)²=1、0²=0
                // ⇒ 近档几乎永不命中，全部列车落到中/远档 ⇒ LOD 语义静默错乱。
                // 现先把 mid 钳到 >=1，再钳 near 到 >=0。
                simulationLodMidBlocks = Math.max(1, simulationLodMidBlocks);
                simulationLodNearBlocks = Math.min(simulationLodNearBlocks, simulationLodMidBlocks - 1);
                simulationLodNearBlocks = Math.max(0, simulationLodNearBlocks);
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
            obj.addProperty("enableRailPositionCache", enableRailPositionCache);
            obj.addProperty("enableSimulationLod", enableSimulationLod);
            obj.addProperty("simulationLodNearBlocks", simulationLodNearBlocks);
            obj.addProperty("simulationLodMidBlocks", simulationLodMidBlocks);
            obj.addProperty("enableScheduleThrottle", enableScheduleThrottle);
            obj.addProperty("scheduleRebuildTicks", scheduleRebuildTicks);
            obj.addProperty("statsLogIntervalSeconds", statsLogIntervalSeconds);
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
