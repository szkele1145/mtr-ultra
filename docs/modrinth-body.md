# MTR-Ultra

Aggressive performance optimizations for large **Minecraft Transit Railway** worlds — simulation LOD, car-level render culling, schedule throttling. Works on top of MTR 3.2.x / YMTR (Yomi's MTR) on MC 1.20.1 Fabric.

> MTR 大存档激进性能优化附属：服务端模拟 LOD、车厢级渲染剔除、排班节流。
> 独立 mixin 附属（非 MTR/YMTR 分支），每项优化可独立开关。

## What it does

Large MTR worlds (dozens of trains, hundreds of rails) stress the server tick loop
and client renderer in ways vanilla MTR/YMTR handles inefficiently. MTR-Ultra
targets the five hottest bottlenecks measured in real saves:

| # | Optimization | Effect |
|---|---|---|
| U1 | **Door-scan window short-circuit** — `Train.calculateCar` scans ~600 blocks per car per tick even when doors are closed; now only scanned while doors are opening/open | ~150k fewer `getBlockState` calls per tick on a 30-train save |
| U2 | **Car-level render culling** — cars beyond the configured train render distance skip all render calls (riding players' train exempt) | Distant trains stop submitting vertices |
| U3 | **Render container reuse** — the per-frame rail dedup `HashMap` is reused instead of reallocated | Less GC churn at 60 FPS |
| U4 | **Server simulation LOD** — trains far from all players simulate at 1/2 or 1/4 rate with proportional tick-steps (manual-driven and passenger-carrying trains never degrade) | Remote trains cost 1/2~1/4 MSPT |
| U5 | **Schedule rebuild throttle** — platform schedules rebuilt once per 20 ticks instead of every tick, keeping the previous copy in between (no blank window on PIDS/sensors/web) | 1/20 schedule overhead near players |

Every optimization is individually toggleable in `config/mtr-ultra.json`
(defaults: all on). Behavior guarantees: manual/occupied trains always simulate
per-tick; riding trains always render; anti-collision checks degrade at most to
4-tick spacing (≤5 blocks, shorter than any rail segment).

## Status

**v0.1.0** — passed a real server smoke test: full boot to `Done`, MTR data loaded,
all mixins applied cleanly, tick loop stable across the throttled paths.

## Requirements

- Minecraft **1.20.1** (Fabric)
- **MTR 3.2.x or YMTR** (Yomi's Minecraft Transit Railway) — either works; MTR-Ultra
  hooks the shared `mtr` classes
- Fabric Loader ≥ 0.15

## Compatibility

- Designed against **YMTR 3.6.4.2**; hooks are signature-stable against MTR 3.2.2-family classes (`mtr.data.Train`, `TrainServer`, `TrainClient`, `mtr.data.RailwayData`, `mtr.render.RenderTrains`)
- If a target class changes in a future YMTR update, the affected optimization fails loudly at startup (mixin `required`) instead of silently misbehaving — check the log
- Safe alongside ANTE (Continued) and other MTR 3.x addons

## Source

[GitHub — szkele1145/mtr-ultra](https://github.com/szkele1145/mtr-ultra) (MIT)
