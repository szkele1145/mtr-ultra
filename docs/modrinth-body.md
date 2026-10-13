# MTR Turbo

Aggressive performance optimizations for large **Minecraft Transit Railway** worlds. Standalone mixin addon (NOT a fork of MTR/YMTR) working on top of MTR 3.2.x / YMTR (Yomi's MTR).

> MTR 大存档激进性能优化附属。独立 mixin 附属（非 MTR/YMTR 分支），六项优化全部独立可开关。
> 支持 MC 1.18.2 / 1.19.2 / 1.19.4 / 1.20.1（Fabric）+ 1.20.1（Forge）。

## What it does

Large MTR worlds (dozens of trains, hundreds of rails) stress the server tick loop
and client renderer in ways vanilla MTR/YMTR handles inefficiently. MTR Turbo
targets the six hottest bottlenecks measured in real saves:

| # | Optimization | Effect |
|---|---|---|
| U1 | **Door-scan window short-circuit** — `Train.calculateCar` scans ~600 blocks per car per tick even when doors are closed; now only scanned while doors are opening/open | ~150k fewer `getBlockState` calls per tick on a 30-train save |
| U2 | **Car-level render culling** — cars beyond the configured train render distance skip all render calls (riding players' train exempt) | Distant trains stop submitting vertices |
| U3 | **Render container reuse** — the per-frame rail dedup `HashMap` is reused instead of reallocated | Less GC churn at 60 FPS |
| U4 | **Server simulation LOD** — trains far from all players simulate at 1/2 or 1/4 rate with proportional tick-steps (manual-driven and passenger-carrying trains never degrade) | Remote trains cost 1/2~1/4 MSPT |
| U5 | **Schedule rebuild throttle** — platform schedules rebuilt once per 20 ticks instead of every tick, keeping the previous copy in between (no blank window on PIDS/sensors/web) | 1/20 schedule overhead near players |
| U6 | **Rail position curve cache** — `Rail.getPosition` (87-instruction trig chain, ~270 calls/tick on a 30-train save) replaced by 0.25-block pre-sampled linear interpolation; rails are geometrically immutable so the cache never invalidates | ~270 trig chains per tick eliminated; deviation <0.125 block (visually indistinguishable) |

Every optimization is individually toggleable in `config/mtr-turbo.json`
(defaults: all on). Behavior guarantees: manual/occupied trains always simulate
per-tick; riding trains always render; anti-collision checks degrade at most to
4-tick spacing (≤5 blocks, shorter than any rail segment); curve cache deviation
stays under half the minimum rail texture resolution.

## Measuring it yourself

Run `/mtrturbo stats` (OP/cheats, 1.19.2+ Fabric / 1.20.1 both loaders) for live
hit counters per optimization, with estimated savings and per-second rates. `/mtrturbo dump` (1.1.3+) additionally writes a standardized JSON snapshot (loader, MC version, all counters) to config/mtrturbo-stats.json for sharing benchmark data. On any
version — including 1.18.2 where no command exists — set `statsLogIntervalSeconds`
in the config (e.g. 300) to log a cumulative line every N seconds for hands-off
benchmark collection.

## Status

**v1.1.1** — 23 real-server smoke tests across 4 MC versions + 3-way coexistence
test with ANTE (which rewrites `simulateTrains` — MTR Turbo detects ANTE and
gracefully hands over only U5, everything else stays active, announced in the log).
All mixin target signatures bytecode-verified identical across MTR 3.2.2-hotfix-2
and YMTR 3.6.3/3.6.4.2 (see compatibility-matrix.md in the repo).

## Requirements

- Minecraft **1.18.2 / 1.19.2 / 1.19.4 / 1.20.1** (Fabric) or **1.20.1** (Forge)
- **MTR 3.2.x or YMTR** — either works; MTR Turbo hooks the shared `mtr` classes
  (1.18.2/1.19.4 builds target official MTR 3.2.2-hotfix-1; 1.19.2 targets YMTR 3.6.2)
- Fabric Loader ≥ 0.14.21 / Forge 47+

Notes: on 1.18.2/1.19.x the in-game config screen and (1.18.2 only) the stats
command are unavailable due to older fabric-api — edit `config/mtr-turbo.json`
manually; the periodic log works everywhere.

## Compatibility

- Designed against **YMTR 3.6.4.2**; hooks are signature-stable against MTR 3.2.2-family classes (`mtr.data.Train`, `TrainServer`, `TrainClient`, `mtr.data.RailwayData`, `mtr.render.RenderTrains`)
- If a target class changes in a future YMTR update, the affected optimization fails loudly at startup (mixin `required`) instead of silently misbehaving — check the log
- **Safe alongside ANTE (Continued)** — auto-hands-over the one conflicting mixin (U5), verified in a live 3-way test
- Safe with other MTR 3.x addons (JCM, YUM etc. — no shared target classes)

## Source

[GitHub — szkele1145/mtr-ultra](https://github.com/szkele1145/mtr-ultra) (MIT)
