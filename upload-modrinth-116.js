const fs = require('fs');
const https = require('https');
const crypto = require('crypto');
const mr = fs.readFileSync('F:\\vibe coding工程\\sfx\\.mrtoken', 'utf-8').trim();

// turbo 1.1.6: fabric(1.18.2/1.19.2/1.19.4/1.20.1) + forge(1.19.4/1.20.1)
const STAGE = 'F:\\vibe coding工程\\mtr-turbo\\release-116\\';
const builds = [
  {
    version_number: '1.1.6-mc1.18.2',
    jar: STAGE + 'mtr-turbo-1.1.6-1.18.2-fabric.jar',
    name: 'MTR Turbo 1.1.6 (Fabric 1.18.2) - @Shadow 可见性修复 + 完整版本矩阵',
    game_versions: ['1.18.2'], loaders: ['fabric'],
  },
  {
    version_number: '1.1.6-mc1.19.2',
    jar: STAGE + 'mtr-turbo-1.1.6-1.19.2-fabric.jar',
    name: 'MTR Turbo 1.1.6 (Fabric 1.19.2) - @Shadow 可见性修复 + 完整版本矩阵',
    game_versions: ['1.19.2'], loaders: ['fabric'],
  },
  {
    version_number: '1.1.6-mc1.19.4',
    jar: STAGE + 'mtr-turbo-1.1.6-1.19.4-fabric.jar',
    name: 'MTR Turbo 1.1.6 (Fabric 1.19.4) - @Shadow 可见性修复 + 完整版本矩阵',
    game_versions: ['1.19.4'], loaders: ['fabric'],
  },
  {
    version_number: '1.1.6',
    jar: STAGE + 'mtr-turbo-1.1.6-1.20.1-fabric.jar',
    name: 'MTR Turbo 1.1.6 (Fabric 1.20.1) - @Shadow 可见性修复 + 完整版本矩阵',
    game_versions: ['1.20.1'], loaders: ['fabric'],
  },
  {
    version_number: '1.1.6-forge-mc1.19.4',
    jar: STAGE + 'mtr-turbo-1.1.6-1.19.4-forge.jar',
    name: 'MTR Turbo 1.1.6 (Forge 1.19.4) - @Shadow 可见性修复 + 完整版本矩阵',
    game_versions: ['1.19.4'], loaders: ['forge'],
  },
  {
    version_number: '1.1.6-forge',
    jar: STAGE + 'mtr-turbo-1.1.6-1.20.1-forge.jar',
    name: 'MTR Turbo 1.1.6 (Forge 1.20.1) - @Shadow 可见性修复 + 完整版本矩阵',
    game_versions: ['1.20.1'], loaders: ['forge'],
  },
];

const changelog = [
  '## MTR Turbo 1.1.6',
  '',
  '**版本矩阵扩充 + 一处运行时崩溃级隐患修复。**',
  '',
  '### 1. ⚠️ 修复：@Shadow 可见性降级（潜在启动即崩）',
  '',
  '`TrainServerMixin`（fabric + forge 两份）把 MTR 目标类的成员声明成了**比目标更低的可见性**：',
  '',
  '| 成员 | 目标实际（MTR 3.2.2-hf1 / YMTR 3.6.x，javap 逐一核对 6 个 jar） | 本插件原先声明 |',
  '|---|---|---|',
  '| `path` | `public final List<PathData>` | `protected` ❌ |',
  '| `isPlayerRiding(Player)` | `public final boolean` | `protected abstract` ❌ |',
  '| `isCurrentlyManual` | `protected` | `protected` ✅ |',
  '',
  'Mixin **运行时**会校验这个规则：',
  '`cannot reduce visibiliy of ... target method`（`InvalidMixinException`）',
  '⇒ 目标类被 mixin 应用时直接抛异常，**崩客户端 / 崩服务端**。',
  '',
  '注解处理器只在编译期给 `warning: Cannot find target for @Shadow`（静态分析限制，',
  '因为成员在父类 `mtr.data.Train` 上），**运行期才炸** —— 所以"构建通过"不等于"运行时安全"。',
  '这在 1.19.4 Forge 这个新平台上尤其危险。',
  '',
  '现已改为与目标逐字一致（`public` / `public abstract`），可见性经 6 个目标 jar 逐一 javap 核对。',
  '',
  '### 2. 新增 Forge 1.19.4 支持',
  '',
  'Forge 端此前只有 1.20.1。现在新增 **1.19.4**（`net.minecraftforge:forge:1.19.4-45.4.5`），',
  '目标 mod 为官方 **MTR 3.2.2-hotfix-1**（YMTR 没有 1.19.4 版本）。',
  '',
  '1.19.x 的 Forge 端**不提供**图形化配置屏（`GuiGraphics` 是 1.20 才引入的），',
  '`/mtrturbo screen` 会给出明确提示，请手编 `config/mtr-turbo.json`。',
  '（1.20.1 Forge 端配置屏完全不变。）',
  '',
  '### 3. 完整版本矩阵',
  '',
  '| 加载器 | 1.18.2 | 1.19.2 | 1.19.4 | 1.20.1 |',
  '|---|---|---|---|---|',
  '| Fabric | ✅ | ✅ | ✅ | ✅ |',
  '| Forge | — | — | ✅ | ✅ |',
  '',
  '全部 **10 个 mixin 挂钩点**在 6 个目标 jar 上逐一 javap 核对，签名完全一致：',
  '`Train.calculateCar` / `Train.scanDoors` / `Train.getRoutePosition` /',
  '`TrainServer.simulateTrain` / `TrainServer.lambda$simulateTrain$8` /',
  '`RailwayData.simulateTrains` / `RenderTrains.render` / `RenderTrains.maxTrainRenderDistance` /',
  '`TrainClient.simulateTrain` / `TrainClient.simulateCar`。',
  '',
  '### 4. 1.18.2 / 1.19.x 的 ModMenu 入口处理',
  '',
  '这两个版本的 fabric-api 没有 ModMenu API，构建时自动移除 `modmenu` 入口点与 `suggests`，',
  '避免加载器报"未知入口点"。1.20.1 保持完整 ModMenu 集成。',
  '',
  '### 验证',
  '',
  '- 6 个产物全部 `gradle clean build` BUILD SUCCESSFUL。',
  '- 6 个目标 jar（mtr-fabric-1.18.2 / ymtr-fabric-1.19.2 / mtr-fabric-1.19.4 /',
  '  ymtr-fabric-1.20.1 / mtr-forge-1.19.4 / ymtr-forge-1.20.1）的 mixin 目标签名逐一核对。',
  '',
  'SHA-512 各文件见 GitHub Release。',
].join('\n');

function upload(b) {
  return new Promise((resolve, reject) => {
    const fileBuf = fs.readFileSync(b.jar);
    const fileName = b.jar.split('\\').pop();
    const sha512 = 'sha512:' + crypto.createHash('sha512').update(fileBuf).digest('hex');
    const data = {
      name: b.name,
      version_number: b.version_number,
      game_versions: b.game_versions,
      loaders: b.loaders,
      featured: false,
      dependencies: [],
      changelog,
      release_channel: 'release',
      project_id: 'r8onA7GV',
      file_parts: [fileName]
    };
    const boundary = '----DSHBoundary' + Date.now() + Math.random().toString(36).slice(2);
    const chunks = [];
    chunks.push(Buffer.from('--' + boundary + '\r\nContent-Disposition: form-data; name="data"\r\n\r\n' + JSON.stringify(data) + '\r\n', 'utf8'));
    chunks.push(Buffer.from('--' + boundary + '\r\nContent-Disposition: form-data; name="file"; filename="' + fileName + '"\r\nContent-Type: application/java-archive\r\n\r\n', 'utf8'));
    chunks.push(fileBuf);
    chunks.push(Buffer.from('\r\n--' + boundary + '--\r\n', 'utf8'));
    const body = Buffer.concat(chunks);
    const req = https.request({
      hostname: 'api.modrinth.com', port: 443, method: 'POST', path: '/v2/version',
      headers: {
        'Authorization': mr,
        'User-Agent': 'szkele1145/mtr-turbo/1.1.6 (contact: szkele1145)',
        'Content-Type': 'multipart/form-data; boundary=' + boundary,
        'Content-Length': body.length
      }
    }, res => {
      let out = '';
      res.on('data', c => out += c);
      res.on('end', () => {
        console.log(b.version_number + ' -> Status ' + res.statusCode);
        if (res.statusCode === 200) {
          const j = JSON.parse(out);
          console.log('  id=' + j.id + ' file=' + j.files[0].filename + ' size=' + j.files[0].size + ' sha512=' + j.files[0].hashes.sha512.slice(0, 16) + '...');
          resolve();
        } else { console.log(out.slice(0, 400)); reject(new Error('HTTP ' + res.statusCode)); }
      });
    });
    req.on('error', reject);
    req.write(body);
    req.end();
  });
}

(async () => {
  for (const b of builds) {
    await upload(b);
  }
  console.log('ALL DONE');
})().catch(e => { console.error('ERROR:', e.message); process.exit(1); });
