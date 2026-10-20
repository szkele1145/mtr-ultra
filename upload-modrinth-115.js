const fs = require('fs');
const https = require('https');
const crypto = require('crypto');
const mr = fs.readFileSync('F:\\vibe coding工程\\sfx\\.mrtoken', 'utf-8').trim();

// turbo 1.1.5: 双渠道 fabric(1.19.2/1.19.4/1.20.1) + forge(1.20.1)
const builds = [
  {
    version_number: '1.1.5-mc1.19.2',
    jar: 'F:\\vibe coding工程\\mtr-turbo\\build\\libs\\mtr-turbo-1.1.5-1.19.2-fabric.jar',
    name: 'MTR Turbo 1.1.5 (Fabric 1.19.2) - LOD 阈值钳制 + Mixin 降级加固',
    game_versions: ['1.19.2'], loaders: ['fabric'],
  },
  {
    version_number: '1.1.5-mc1.19.4',
    jar: 'F:\\vibe coding工程\\mtr-turbo\\build\\libs\\mtr-turbo-1.1.5-1.19.4-fabric.jar',
    name: 'MTR Turbo 1.1.5 (Fabric 1.19.4) - LOD 阈值钳制 + Mixin 降级加固',
    game_versions: ['1.19.4'], loaders: ['fabric'],
  },
  {
    version_number: '1.1.5',
    jar: 'F:\\vibe coding工程\\mtr-turbo\\build\\libs\\mtr-turbo-1.1.5-1.20.1-fabric.jar',
    name: 'MTR Turbo 1.1.5 (Fabric 1.20.1) - LOD 阈值钳制 + Mixin 降级加固',
    game_versions: ['1.20.1'], loaders: ['fabric'],
  },
  {
    version_number: '1.1.5-forge',
    jar: 'F:\\vibe coding工程\\mtr-turbo\\forge-project\\build\\libs\\mtr-turbo-1.1.5-1.20.1-forge.jar',
    name: 'MTR Turbo 1.1.5 (Forge 1.20.1) - LOD 阈值钳制 + Mixin 降级加固',
    game_versions: ['1.20.1'], loaders: ['forge'],
  },
];

const changelog = [
  '## MTR Turbo 1.1.5',
  '',
  '代码审查报告修复（2 项），兼容性加固（1 项）。',
  '',
  '### 1. LOD 阈值钳制在 mid<=0 时产出非法值（中）',
  '',
  '原式 `near = min(near, mid - 1)`：',
  '',
  '- `mid=0` → `near=-1`；`mid=1` → `near=0`',
  '- 随后 `TrainServerMixin` 用 `near*near` 比较：`(-1)²=1`、`0²=0`',
  '- ⇒ 近档几乎永不命中，全部列车落到中/远档',
  '- ⇒ **LOD 语义静默错乱**（不崩溃，但用户配置不生效）',
  '',
  '现先钳 `mid >= 1`，再钳 `near >= 0`。fabric/forge 双端同步。',
  '',
  '### 2. 排班节流 `period=1` 恒假（随 1.1.5 首发确认）',
  '',
  'subagent 审查发现 `scheduleRebuildTicks=1` 时 `% 1 == 1` 恒为 false，',
  '排班表永久冻结。已改为 `(counter - 1) % period == 0`（每 tick 都重建 = 关闭节流）。',
  '',
  '### 3. Mixin 注入全部加 `require = 0`（兼容性加固）',
  '',
  '`@Redirect` 不带 ordinal 时要求目标方法内**唯一**匹配：',
  'MTR 升级后只要多出一处 `new HashMap()`，注入就失败 ⇒ 直接崩客户端。',
  '',
  '全部注入点降级为 `require = 0`：注入失败时功能退化为「不剔除 / 不去重 / 全速模拟」，',
  '不再崩客户端 —— 与本工程 `RailwayDataMixin` 的既有降级策略一致。',
  '',
  '### 验证',
  '',
  '- `gradle build` BUILD SUCCESSFUL（fabric 38s / forge 独立构建）。',
  '- 1.19.2 / 1.19.4 / 1.20.1 fabric + 1.20.1 forge 四个产物。',
  '',
  'SHA-512 各文件见 GitHub Release。'
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
        'User-Agent': 'szkele1145/mtr-turbo/1.1.5 (contact: szkele1145)',
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
