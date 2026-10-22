const fs = require('fs');
const https = require('https');

const tok = fs.readFileSync('F:\\vibe coding工程\\sfx\\.ghtoken', 'utf-8').trim();
const repo = 'szkele1145/mtr-ultra';
const tag = 'v1.1.7';

const STAGE = 'F:\\vibe coding工程\\mtr-turbo\\release-117\\';
const assets = [
  STAGE + 'mtr-turbo-1.1.7-1.20.1-fabric.jar',
  STAGE + 'mtr-turbo-1.1.7-1.19.4-fabric.jar',
  STAGE + 'mtr-turbo-1.1.7-1.19.2-fabric.jar',
  STAGE + 'mtr-turbo-1.1.7-1.18.2-fabric.jar',
  STAGE + 'mtr-turbo-1.1.7-1.20.1-forge.jar',
  STAGE + 'mtr-turbo-1.1.7-1.19.4-forge.jar',
].filter(p => fs.existsSync(p));

const notes = [
  '## MTR Turbo v1.1.7',
  '',
  '**修复一个自 v0.1.0 起就存在的注入点描述符错误 + 全量注入器降级加固。**',
  '',
  '### 1. 🔴 修复：RenderTrainsMixin 描述符第一参数写错',
  '',
  'fabric 版 `RenderTrainsMixin` 的 `@Inject` / `@Redirect` 描述符把',
  '`RenderTrains.render` 静态版的**第一参数**写成了 `Lnet/minecraft/class_1297;`',
  '（MC `Entity`），而目标 jar 里实际是 **`Lmtr/entity/EntitySeat;`**',
  '（MTR 自己的座位实体类，**是** Entity 的子类）。',
  '',
  '**子类也不行**：反编译 Mixin 0.8.5 的 `MemberInfo.matches`（字节码证据），',
  '描述符匹配是 **`String.equals` 精确比较**，**不做子类型兼容**',
  '⇒ `class_1297` 与 `EntitySeat` 字符串不相等 ⇒ **永不匹配**。',
  '',
  '**后果**（自 v0.1.0 起的每个 fabric 构建）：',
  '',
  '- `@Redirect`（带 `require=0`）静默失效 ⇒ U3 轨道去重表优化从未生效；',
  '- `@Inject`（**没带** `require=0`，config `defaultRequire=1`）',
  '  ⇒ **注入失败直接崩客户端**。',
  '',
  '**修复**：描述符改为与目标逐字一致的',
  '`render(Lmtr/entity/EntitySeat;FLnet/minecraft/class_4587;Lnet/minecraft/class_4597;)V`。',
  'forge 版本来就写对了，不受影响。',
  '',
  '### 2. 全量注入器 `require=0` 加固',
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
  '',
  '### 3. 全部 mixin 挂钩点签名复核',
  '',
  '对 6 个目标 jar（mtr-fabric-1.18.2 / ymtr-fabric-1.19.2 / mtr-fabric-1.19.4 /',
  'ymtr-fabric-1.20.1 / mtr-forge-1.19.4 / ymtr-forge-1.20.1）重新 javap 核对，',
  '`Train.scanDoors` / `Train.simulateTrain` / `Rail.getPosition` 全部逐字匹配，',
  '`RenderTrains.render` 本版修正。',
  '',
  '### 附件',
  '',
  '- mtr-turbo-1.1.7-1.20.1-fabric.jar',
  '- mtr-turbo-1.1.7-1.19.4-fabric.jar',
  '- mtr-turbo-1.1.7-1.19.2-fabric.jar',
  '- mtr-turbo-1.1.7-1.18.2-fabric.jar',
  '- mtr-turbo-1.1.7-1.20.1-forge.jar',
  '- mtr-turbo-1.1.7-1.19.4-forge.jar',
  '',
  '所有开关在 `config/mtr-turbo.json`。'
].join('\n');

function api(method, path, bodyObj) {
  return new Promise((resolve, reject) => {
    const data = bodyObj ? Buffer.from(JSON.stringify(bodyObj), 'utf8') : null;
    const req = https.request({
      hostname: 'api.github.com', port: 443, method, path,
      headers: {
        'Authorization': 'token ' + tok,
        'User-Agent': 'szkele1145',
        'Accept': 'application/vnd.github+json',
        'Content-Type': 'application/json',
        'Content-Length': data ? data.length : 0
      }
    }, res => {
      let raw = '';
      res.on('data', c => raw += c);
      res.on('end', () => {
        let parsed = null;
        try { parsed = JSON.parse(raw); } catch (e) { parsed = raw; }
        if (res.statusCode >= 200 && res.statusCode < 300) resolve(parsed);
        else reject(new Error('HTTP ' + res.statusCode + ' ' + String(raw).slice(0, 300)));
      });
    });
    req.on('error', reject);
    if (data) req.write(data);
    req.end();
  });
}

function uploadAsset(rel, filePath) {
  const fileBuf = fs.readFileSync(filePath);
  const fileName = filePath.split('\\').pop();
  const uploadUrl = rel.upload_url.replace(/\{.*\}$/, '') + '?name=' + encodeURIComponent(fileName);
  return new Promise((resolve, reject) => {
    const u = new URL(uploadUrl);
    const req = https.request({
      hostname: u.hostname, port: 443, method: 'POST', path: u.pathname + u.search,
      headers: {
        'Authorization': 'token ' + tok,
        'User-Agent': 'szkele1145',
        'Content-Type': 'application/octet-stream',
        'Content-Length': fileBuf.length
      }
    }, res => {
      let raw = '';
      res.on('data', c => raw += c);
      res.on('end', () => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          const j = JSON.parse(raw);
          console.log('UPLOAD_OK ' + j.name + ' size=' + j.size + ' id=' + j.id);
          resolve(j);
        } else reject(new Error('HTTP ' + res.statusCode + ' ' + raw.slice(0, 200)));
      });
    });
    req.on('error', reject);
    req.write(fileBuf);
    req.end();
  });
}

(async () => {
  const sha = process.argv[2];
  if (!sha) { console.error('用法: node publish-github-116.js <commit-sha>'); process.exit(1); }
  console.log('assets=' + assets.length + ' sha=' + sha);

  try {
    await api('GET', '/repos/' + repo + '/git/ref/tags/' + tag);
    console.log('TAG_EXISTS');
  } catch (e) {
    const r = await api('POST', '/repos/' + repo + '/git/refs', { ref: 'refs/tags/' + tag, sha });
    console.log('TAG_CREATED ' + r.ref + ' -> ' + r.object.sha);
  }

  let rel;
  try {
    rel = await api('GET', '/repos/' + repo + '/releases/tags/' + tag);
    console.log('RELEASE_EXISTS id=' + rel.id);
  } catch (e) {
    rel = await api('POST', '/repos/' + repo + '/releases', {
      tag_name: tag, name: 'MTR Turbo v1.1.7 - RenderTrains 描述符修复 + 注入器全量降级加固',
      body: notes, draft: false, prerelease: false
    });
    console.log('RELEASE_CREATED id=' + rel.id + ' url=' + rel.html_url);
  }

  for (const a of assets) {
    const name = a.split('\\').pop();
    const existing = (rel.assets || []).find(x => x.name === name);
    if (existing) {
      console.log('SKIP（已存在）: ' + name + ' size=' + existing.size);
      continue;
    }
    const up = await uploadAsset(rel, a);
    const local = fs.statSync(a).size;
    console.log('  verify: local=' + local + ' remote=' + up.size + ' ' + (local === up.size ? 'MATCH' : 'MISMATCH!'));
  }
  console.log('DONE');
})().catch(e => { console.error('ERROR:', e.message); process.exit(1); });
