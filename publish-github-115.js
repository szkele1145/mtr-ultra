const fs = require('fs');
const https = require('https');
const crypto = require('crypto');

const tok = fs.readFileSync('F:\\vibe coding工程\\sfx\\.ghtoken', 'utf-8').trim();
const repo = 'szkele1145/mtr-ultra';   // 仓库实际名（2026-10-21 API 核实：mtr-turbo 404，mtr-ultra 存在）
const tag = 'v1.1.5';

const assets = [
  'F:\\vibe coding工程\\mtr-turbo\\build\\libs\\mtr-turbo-1.1.5-1.20.1-fabric.jar',
  'F:\\vibe coding工程\\mtr-turbo\\build\\libs\\mtr-turbo-1.1.5-1.19.4-fabric.jar',
  'F:\\vibe coding工程\\mtr-turbo\\build\\libs\\mtr-turbo-1.1.5-1.19.2-fabric.jar',
  'F:\\vibe coding工程\\mtr-turbo\\forge-project\\build\\libs\\mtr-turbo-1.1.5-1.20.1-forge.jar',
].filter(p => fs.existsSync(p));

const notes = [
  '## MTR Turbo v1.1.5',
  '',
  '代码审查报告修复 + 兼容性加固。',
  '',
  '### 1. LOD 阈值钳制在 `mid<=0` 时产出非法值（中）',
  '',
  '原式 `near = min(near, mid - 1)`：`mid=0` → `near=-1`；`mid=1` → `near=0`。',
  '随后 `TrainServerMixin` 用 `near*near` 比较（`(-1)²=1`、`0²=0`）⇒',
  '近档几乎永不命中，全部列车落到中/远档 ⇒ **LOD 语义静默错乱**',
  '（不崩溃，但用户配置不生效）。',
  '',
  '现先钳 `mid >= 1`，再钳 `near >= 0`。fabric/forge 双端同步。',
  '',
  '### 2. 排班节流 `period=1` 恒假',
  '',
  '`scheduleRebuildTicks=1` 时 `% 1 == 1` 恒为 false ⇒ 排班表永久冻结',
  '（客户端到站信息不更新）。已改为 `(counter - 1) % period == 0`。',
  '',
  '### 3. Mixin 注入全部加 `require = 0`（兼容性加固）',
  '',
  '`@Redirect` 不带 ordinal 时要求目标方法内**唯一**匹配：',
  'MTR 升级后只要多出一处 `new HashMap()`，注入就失败 ⇒ 直接崩客户端。',
  '',
  '全部注入点降级为 `require = 0`：注入失败时功能退化为',
  '「不剔除 / 不去重 / 全速模拟」，不再崩客户端 ——',
  '与本工程 `RailwayDataMixin` 的既有降级策略一致。',
  '',
  '### 附件',
  '',
  '- mtr-turbo-1.1.5-1.20.1-fabric.jar',
  '- mtr-turbo-1.1.5-1.19.4-fabric.jar',
  '- mtr-turbo-1.1.5-1.19.2-fabric.jar',
  '- mtr-turbo-1.1.5-1.20.1-forge.jar',
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
  // 裸 body + ?name= 查询串（multipart 会把边界字节写进资产，size/sha512 会不符）
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
          resolve();
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
  if (!sha) { console.error('用法: node publish-github-115.js <commit-sha>'); process.exit(1); }
  console.log('assets=' + assets.length);

  try {
    await api('GET', '/repos/' + repo + '/git/ref/tags/' + tag);
    console.log('TAG_EXISTS');
  } catch (e) {
    const r = await api('POST', '/repos/' + repo + '/git/refs', { ref: 'refs/tags/' + tag, sha });
    console.log('TAG_CREATED ' + r.ref);
  }

  let rel;
  try {
    rel = await api('GET', '/repos/' + repo + '/releases/tags/' + tag);
    console.log('RELEASE_EXISTS id=' + rel.id);
  } catch (e) {
    rel = await api('POST', '/repos/' + repo + '/releases', {
      tag_name: tag, name: 'MTR Turbo v1.1.5 - LOD 阈值钳制 + Mixin 降级加固',
      body: notes, draft: false, prerelease: false
    });
    console.log('RELEASE_CREATED id=' + rel.id + ' url=' + rel.html_url);
  }

  for (const a of assets) {
    const name = a.split('\\').pop();
    const existing = (rel.assets || []).find(x => x.name === name);
    if (existing) {
      console.log('SKIP（已存在，校验字节）: ' + name + ' size=' + existing.size);
      continue;
    }
    await uploadAsset(rel, a);
  }
  console.log('DONE');
})().catch(e => { console.error('ERROR:', e.message); process.exit(1); });
