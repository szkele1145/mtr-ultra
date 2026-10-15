# MTR Turbo

Aggressive performance optimizations for large **Minecraft Transit Railway** worlds. Standalone mixin addon (NOT a fork of MTR/YMTR) working on top of MTR 3.2.x / YMTR (Yomi's MTR).

> MTR 婢堆冪摠濡楋絾绺烘潻娑欌偓褑鍏樻导妯哄闂勫嫬鐫橀妴鍌滃缁?mixin 闂勫嫬鐫橀敍鍫ユ姜 MTR/YMTR 閸掑棙鏁敍澶涚礉閸忣參銆嶆导妯哄閸忋劑鍎撮悪顒傜彌閸欘垰绱戦崗鐐解偓淇搉> 閺€顖涘瘮 MC 1.18.2 / 1.19.2 / 1.19.4 / 1.20.1閿涘湗abric閿? 1.20.1閿涘湗orge閿涘鈧繐n
## What it does

Large MTR worlds (dozens of trains, hundreds of rails) stress the server tick loop
and client renderer in ways vanilla MTR/YMTR handles inefficiently. MTR Turbo
targets the six hottest bottlenecks measured in real saves:

| # | Optimization | Effect |
|---|---|---|
| U1 | **Door-scan window short-circuit** 閳?`Train.calculateCar` scans ~600 blocks per car per tick even when doors are closed; now only scanned while doors are opening/open | ~150k fewer `getBlockState` calls per tick on a 30-train save |
| U2 | **Car-level render culling** 閳?cars beyond the configured train render distance skip all render calls (riding players' train exempt) | Distant trains stop submitting vertices |
| U3 | **Render container reuse** 閳?the per-frame rail dedup `HashMap` is reused instead of reallocated | Less GC churn at 60 FPS |
| U4 | **Server simulation LOD** 閳?trains far from all players simulate at 1/2 or 1/4 rate with proportional tick-steps (manual-driven and passenger-carrying trains never degrade) | Remote trains cost 1/2~1/4 MSPT |
| U5 | **Schedule rebuild throttle** 閳?platform schedules rebuilt once per 20 ticks instead of every tick, keeping the previous copy in between (no blank window on PIDS/sensors/web) | 1/20 schedule overhead near players |
| U6 | **Rail position curve cache** 閳?`Rail.getPosition` (87-instruction trig chain, ~270 calls/tick on a 30-train save) replaced by 0.25-block pre-sampled linear interpolation; rails are geometrically immutable so the cache never invalidates | ~270 trig chains per tick eliminated; deviation <0.125 block (visually indistinguishable) |

Every optimization is individually toggleable in `config/mtr-turbo.json`
(defaults: all on). Behavior guarantees: manual/occupied trains always simulate
per-tick; riding trains always render; anti-collision checks degrade at most to
4-tick spacing (閳? blocks, shorter than any rail segment); curve cache deviation
stays under half the minimum rail texture resolution.

## Measuring it yourself

Run `/mtrturbo stats` (OP/cheats, 1.19.2+ Fabric / 1.20.1 both loaders) for live
hit counters per optimization, with estimated savings and per-second rates. `/mtrturbo dump` (1.1.3+) additionally writes a standardized JSON snapshot (loader, MC version, all counters) to config/mtrturbo-stats.json for sharing benchmark data. On any
version 閳?including 1.18.2 where no command exists 閳?set `statsLogIntervalSeconds`
in the config (e.g. 300) to log a cumulative line every N seconds for hands-off
benchmark collection.

## Status

**v1.1.1** 閳?23 real-server smoke tests across 4 MC versions + 3-way coexistence
test with ANTE (which rewrites `simulateTrains` 閳?MTR Turbo detects ANTE and
gracefully hands over only U5, everything else stays active, announced in the log).
All mixin target signatures bytecode-verified identical across MTR 3.2.2-hotfix-2
and YMTR 3.6.3/3.6.4.2 (see compatibility-matrix.md in the repo).

## Requirements

- Minecraft **1.18.2 / 1.19.2 / 1.19.4 / 1.20.1** (Fabric) or **1.20.1** (Forge)
- **MTR 3.2.x or YMTR** 閳?either works; MTR Turbo hooks the shared `mtr` classes
  (1.18.2/1.19.4 builds target official MTR 3.2.2-hotfix-1; 1.19.2 targets YMTR 3.6.2)
- Fabric Loader 閳?0.14.21 / Forge 47+

Notes: on 1.18.2/1.19.x the in-game config screen and (1.18.2 only) the stats
command are unavailable due to older fabric-api 閳?edit `config/mtr-turbo.json`
manually; the periodic log works everywhere.

## Compatibility

- Designed against **YMTR 3.6.4.2**; hooks are signature-stable against MTR 3.2.2-family classes (`mtr.data.Train`, `TrainServer`, `TrainClient`, `mtr.data.RailwayData`, `mtr.render.RenderTrains`)
- If a target class changes in a future YMTR update, the affected optimization fails loudly at startup (mixin `required`) instead of silently misbehaving 閳?check the log
- **Safe alongside ANTE (Continued)** 閳?auto-hands-over the one conflicting mixin (U5), verified in a live 3-way test
- **Companion: [MTR Next](https://modrinth.com/mod/mtr-next)** - 4.x features (rail preview, 4.0 vehicle packs, formations) on the same YMTR; quad-test verified coexistence (YMTR + Turbo + Next + ANTE in one server, zero conflicts)
- Safe with other MTR 3.x addons (JCM, YUM etc. 閳?no shared target classes)

## Source

[GitHub 閳?szkele1145/mtr-ultra](https://github.com/szkele1145/mtr-ultra) (MIT)
