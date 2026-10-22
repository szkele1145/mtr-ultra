const fs = require('fs');
const https = require('https');
const crypto = require('crypto');
const mr = fs.readFileSync('F:\\vibe coding工程\\sfx\\.mrtoken', 'utf-8').trim();

// turbo 1.1.7: fabric(1.18.2/1.19.2/1.19.4/1.20.1) + forge(1.19.4/1.20.1)
const STAGE = 'F:\\vibe coding工程\\mtr-turbo\\release-117\\';
const builds = [
  {
    version_number: '1.1.7-mc1.18.2',
    jar: STAGE + 'mtr-turbo-1.1.7-1.18.2-fabric.jar',
    name: 'MTR Turbo 1.1.7 (Fabric 1.18.2) - @Shadow 可见性修复 + 完整版本矩阵',
    game_versions: ['1.18.2'], loaders: ['fabric'],
  },
  {
    version_number: '1.1.7-mc1.19.2',
    jar: STAGE + 'mtr-turbo-1.1.7-1.19.2-fabric.jar',
    name: 'MTR Turbo 1.1.7 (Fabric 1.19.2) - @Shadow 可见性修复 + 完整版本矩阵',
    game_versions: ['1.19.2'], loaders: ['fabric'],
  },
  {
    version_number: '1.1.7-mc1.19.4',
    jar: STAGE + 'mtr-turbo-1.1.7-1.19.4-fabric.jar',
    name: 'MTR Turbo 1.1.7 (Fabric 1.19.4) - @Shadow 可见性修复 + 完整版本矩阵',
    game_versions: ['1.19.4'], loaders: ['fabric'],
  },
  {
    version_number: '1.1.7',
    jar: STAGE + 'mtr-turbo-1.1.7-1.20.1-fabric.jar',
    name: 'MTR Turbo 1.1.7 (Fabric 1.20.1) - @Shadow 可见性修复 + 完整版本矩阵',
    game_versions: ['1.20.1'], loaders: ['fabric'],
  },
  {
    version_number: '1.1.7-forge-mc1.19.4',
    jar: STAGE + 'mtr-turbo-1.1.7-1.19.4-forge.jar',
    name: 'MTR Turbo 1.1.7 (Forge 1.19.4) - @Shadow 可见性修复 + 完整版本矩阵',
    game_versions: ['1.19.4'], loaders: ['forge'],
  },
  {
    version_number: '1.1.7-forge',
    jar: STAGE + 'mtr-turbo-1.1.7-1.20.1-forge.jar',
    name: 'MTR Turbo 1.1.7 (Forge 1.20.1) - @Shadow 可见性修复 + 完整版本矩阵',
    game_versions: ['1.20.1'], loaders: ['forge'],
  },
];

const changelog = [
  '## MTR Turbo 1.1.7',
  '',
  '**修复一个自 v0.1.0 起就存在的注入点描述符错误 + 全量注入器降级加固。**',
  '',
  '### 1. 🔴 修复：RenderTrainsMixin 描述符第一参数写错（fabric 全系从未生效 + 启动即崩）',
  '',
  '**问题**：fabric 版 `RenderTrainsMixin` 的 `@Inject` / `@Redirect` 描述符把',
  '`RenderTrains.render` 静态版的**第一参数**写成了 `Lnet/minecraft/class_1297;`',
  '（MC `Entity`），而目标 jar 里（4 个 fabric 目标 jar 逐一 javap 核对）实际是',
  '**`Lmtr/entity/EntitySeat;`**（MTR 自己的座位实体类，**是** Entity 的子类）。',
  '',
  '**为什么子类也不行**：反编译 Mixin 0.8.5 的 `MemberInfo.matches`（字节码证据）：',
  '描述符匹配是 **`String.equals` 精确比较**，**不做子类型兼容** ——',
  '`class_1297` 与 `EntitySeat` 字符串不相等 ⇒ **永不匹配**。',
  '',
  '**后果**（自 v0.1.0 起的每个 fabric 构建）：',
  '',
  '- `@Redirect`（带 `require=0`）静默失效 ⇒ U3 轨道去重表优化从未生效；',
  '- `@Inject`（**没带** `require=0`，config `defaultRequire=1`）⇒',
  '  **注入失败直接崩客户端**（`Critical injection failure`）。',
  '',
  '**修复**：描述符改为与目标逐字一致的',
  '`render(Lmtr/entity/EntitySeat;FLnet/minecraft/class_4587;Lnet/minecraft/class_4597;)V`。',
  'forge 版本来就写对了（mojang 名 `Lmtr/entity/EntitySeat;FLcom/mojang/blaze3d/vertex/PoseStack;...`），不受影响。',
  '',
  '### 2. 全量注入器 `require=0` 加固（防 MTR 升级崩服）',
  '',
  '审查发现 6 处注入器**没有** `require=0`（config `defaultRequire=1` 时',
  'MTR 升级改签名 ⇒ 注入失败 ⇒ 直接崩）：',
  '',
  '| Mixin | 注入器 | fabric | forge |',
  '|---|---|---|---|',
  '| `RenderTrainsMixin` | `@Inject`（render HEAD） | ✅ 已补 | ✅ 已补 |',
  '| `RailPositionCacheMixin` | `@Redirect`（Rail.getPosition） | ✅ 已补 | ✅ 已补 |',
  '| `TrainMixin` | `@ModifyExpressionValue` / `@Redirect`（scanDoors） | ✅ 已补 | ✅ 已补 |',
  '| `TrainServerMixin` | `@Redirect`（lambda$simulateTrain$8） | ✅ 已补 | ✅ 已补 |',
  '| `TrainServerTickScaleMixin` | `@ModifyArg`（父类调用步长） | ✅ 已补 | ✅ 已补 |',
  '',
  '统一策略：**注入失败 = 功能退化（回原版行为），绝不崩游戏**。',
  '例外说明：`RenderTrainsMixin` 的 `@Inject` 与 `@Redirect` 必须同生共死',
  '（若只 `@Redirect` 成功，复用容器会跨帧残留脏数据），两者都补了 `require=0`。',
  '',
  '### 3. 全部 mixin 挂钩点签名复核',
  '',
  '对 6 个目标 jar（mtr-fabric-1.18.2 / ymtr-fabric-1.19.2 / mtr-fabric-1.19.4 /',
  'ymtr-fabric-1.20.1 / mtr-forge-1.19.4 / ymtr-forge-1.20.1）重新 javap 核对：',
  '',
  '| 目标 | 签名 | 结果 |',
  '|---|---|---|',
  '| `Train.scanDoors` | `(class_1937, D, D, D, F, F, D, I)Z` | ✅ 逐字匹配 |',
  '| `Train.simulateTrain` | `(class_1937, F, Depot)V` | ✅ 逐字匹配 |',
  '| `Rail.getPosition` | `(D)class_243` | ✅ 逐字匹配 |',
  '| `RenderTrains.render`（静态） | `(EntitySeat, F, class_4587, class_4597)V` | ✅ 本版修正 |',
  '| 其余挂钩点 | 已在 1.1.6 核对 | ✅ 不变 |',
  '',
  '### 验证',
  '',
  '- fabric 1.20.1 `gradle build -x test` BUILD SUCCESSFUL（1m 1s）；',
  '- 其余 5 个版本构建中（发布时全部附上）；',
  '- `MemberInfo.matches` 字节码反编译确认 desc 匹配为 `String.equals`（精确比较）。',
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
        'User-Agent': 'szkele1145/mtr-turbo/1.1.7 (contact: szkele1145)',
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
