const fs = require('fs');
const https = require('https');

const tok = fs.readFileSync('F:\\vibe coding工程\\sfx\\.ghtoken', 'utf-8').trim();
const repo = 'szkele1145/mtr-ultra';
const tag = 'v1.1.6';

const STAGE = 'F:\\vibe coding工程\\mtr-turbo\\release-116\\';
const assets = [
  STAGE + 'mtr-turbo-1.1.6-1.20.1-fabric.jar',
  STAGE + 'mtr-turbo-1.1.6-1.19.4-fabric.jar',
  STAGE + 'mtr-turbo-1.1.6-1.19.2-fabric.jar',
  STAGE + 'mtr-turbo-1.1.6-1.18.2-fabric.jar',
  STAGE + 'mtr-turbo-1.1.6-1.20.1-forge.jar',
  STAGE + 'mtr-turbo-1.1.6-1.19.4-forge.jar',
].filter(p => fs.existsSync(p));

const notes = [
  '## MTR Turbo v1.1.6',
  '',
  '**版本矩阵扩充 + 一处运行时崩溃级隐患修复。**',
  '',
  '### 1. ⚠️ 修复：@Shadow 可见性降级（潜在启动即崩）',
  '',
  '`TrainServerMixin`（fabric + forge 两份）把 MTR 目标类的成员声明成了**比目标更低的可见性**：',
  '',
  '| 成员 | 目标实际（javap 逐一核对 6 个 jar） | 原声明 |',
  '|---|---|---|',
  '| `path` | `public final List<PathData>` | `protected` ❌ |',
  '| `isPlayerRiding(Player)` | `public final boolean` | `protected abstract` ❌ |',
  '| `isCurrentlyManual` | `protected` | `protected` ✅ |',
  '',
  'Mixin **运行时**校验此规则并抛 `InvalidMixinException`',
  '（`cannot reduce visibiliy of ... target method`）⇒ **崩客户端 / 崩服务端**。',
  '',
  '注解处理器只在编译期给 `warning: Cannot find target for @Shadow`',
  '（静态分析限制 —— 成员在父类 `mtr.data.Train` 上），**运行期才炸**。',
  '所以「构建通过」不等于「运行时安全」。这在 1.19.4 Forge 新平台上尤其危险。',
  '',
  '已改为与目标逐字一致（`public` / `public abstract`）。',
  '',
  '### 2. 新增 Forge 1.19.4 支持',
  '',
  'Forge 端此前只有 1.20.1。现在新增 **1.19.4**',
  '（`net.minecraftforge:forge:1.19.4-45.4.5`），目标 mod 为官方 **MTR 3.2.2-hotfix-1**',
  '（YMTR 无 1.19.4 版本）。',
  '',
  '1.19.x Forge 端**不提供**图形化配置屏（`GuiGraphics` 是 1.20 才引入），',
  '`/mtrturbo screen` 会给出明确提示，请手编 `config/mtr-turbo.json`。',
  '1.20.1 Forge 端配置屏完全不变。',
  '',
  '### 3. 完整版本矩阵',
  '',
  '| 加载器 | 1.18.2 | 1.19.2 | 1.19.4 | 1.20.1 |',
  '|---|---|---|---|---|',
  '| Fabric | ✅ | ✅ | ✅ | ✅ |',
  '| Forge | — | — | ✅ | ✅ |',
  '',
  '全部 **10 个 mixin 挂钩点**在 6 个目标 jar 上逐一 javap 核对，签名完全一致。',
  '`mtr.data.Train` 的 5 个成员与 `RailwayData.schedulesForPlatform` 亦逐项核对。',
  '',
  '### 4. 1.18.2 / 1.19.x 的 ModMenu 入口处理',
  '',
  '这些版本的 fabric-api 无 ModMenu API，构建时自动移除 `modmenu` 入口点与 `suggests`，',
  '避免加载器报「未知入口点」。1.20.1 保持完整 ModMenu 集成。',
  '',
  '### 附件',
  '',
  '- mtr-turbo-1.1.6-1.20.1-fabric.jar',
  '- mtr-turbo-1.1.6-1.19.4-fabric.jar',
  '- mtr-turbo-1.1.6-1.19.2-fabric.jar',
  '- mtr-turbo-1.1.6-1.18.2-fabric.jar',
  '- mtr-turbo-1.1.6-1.20.1-forge.jar',
  '- mtr-turbo-1.1.6-1.19.4-forge.jar',
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
      tag_name: tag, name: 'MTR Turbo v1.1.6 - @Shadow 可见性修复 + 完整版本矩阵',
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
