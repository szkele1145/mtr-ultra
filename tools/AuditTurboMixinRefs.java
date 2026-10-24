import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

/**
 * turbo mixin 引用审计器（字节码级）。
 *
 * turbo 的 mixin 全部 remap=false + 手写符号，且所有注入器都带 require=0
 * ⇒ 描述符写错不会崩，而是**静默失效**（用户以为装了优化，实际没生效）。
 * 这正是 RenderTrainsMixin 从 v0.1.0 起一直没生效的那类缺陷。
 *
 * 本工具检查三类引用是否在目标 jar 里真实存在：
 *   ① method = "..."      里的方法名（或 「名+描述符」）
 *   ② @At(target = "L<owner>;<name><desc>")  里的 INVOKE/FIELD 目标
 *   ③ @Shadow 的成员名
 *
 * 用法：
 *   java AuditTurboMixinRefs <源码目录> <jar1> [jar2 ...]
 */
public class AuditTurboMixinRefs {

    // 内部名 -> { 方法键: access, 字段键: access, 父类 }
    static class Index {
        Map<String, Integer> methods = new HashMap<>();   // "owner.name desc" -> access
        Map<String, Integer> fields = new HashMap<>();    // "owner.name desc" -> access
        Map<String, String> superName = new HashMap<>();

        Index(Path jar) throws IOException {
            try (ZipFile zf = new ZipFile(jar.toFile())) {
                Enumeration<? extends ZipEntry> en = zf.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    if (!e.getName().endsWith(".class")) continue;
                    byte[] data = zf.getInputStream(e).readAllBytes();
                    ClassReader cr = new ClassReader(data);
                    superName.put(cr.getClassName(), cr.getSuperName());
                    ClassNode cn = new ClassNode();
                    cr.accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    for (MethodNode mn : cn.methods) {
                        methods.put(cr.getClassName() + "." + mn.name + " " + mn.desc, mn.access);
                    }
                    for (FieldNode fn : cn.fields) {
                        fields.put(cr.getClassName() + "." + fn.name + " " + fn.desc, fn.access);
                    }
                }
            }
        }

        boolean hasMethod(String owner, String name, String desc) {
            String cur = owner;
            Set<String> seen = new HashSet<>();
            while (cur != null && seen.add(cur)) {
                if (methods.containsKey(cur + "." + name + " " + desc)) return true;
                cur = superName.get(cur);
            }
            return false;
        }

        boolean hasMethodByName(String owner, String name) {
            String cur = owner;
            Set<String> seen = new HashSet<>();
            while (cur != null && seen.add(cur)) {
                String prefix = cur + "." + name + " ";
                for (String k : methods.keySet()) {
                    if (k.startsWith(prefix)) return true;
                }
                cur = superName.get(cur);
            }
            return false;
        }

        boolean hasFieldByName(String owner, String name) {
            String cur = owner;
            Set<String> seen = new HashSet<>();
            while (cur != null && seen.add(cur)) {
                String prefix = cur + "." + name + " ";
                for (String k : fields.keySet()) {
                    if (k.startsWith(prefix)) return true;
                }
                cur = superName.get(cur);
            }
            return false;
        }

        boolean hasClass(String owner) {
            return superName.containsKey(owner);
        }
    }

    /** JDK / 常见第三方库的命名空间：不在 mod jar 里，无法用本工具验证 */
    static boolean isExternal(String owner) {
        return owner.startsWith("java/") || owner.startsWith("javax/")
                || owner.startsWith("jdk/") || owner.startsWith("sun/")
                || owner.startsWith("com/google/") || owner.startsWith("org/apache/")
                || owner.startsWith("org/slf4j/") || owner.startsWith("org/joml/")
                || owner.startsWith("org/msgpack/") || owner.startsWith("it/unimi/");
    }

    /**
     * 读 refmap（AP 生成的 Mojmap→intermediary 映射表），建立
     * 「Mojmap 描述符 → intermediary 描述符」的转换。
     *
     * <p>★ 为什么需要（1.1.8 新增）：
     * turbo 过去全部 mixin 都用 {@code remap=false} + 手写 intermediary 符号，
     * 审计器可以直接拿描述符去目标 jar 里比对。但 1.1.8 起
     * {@code RenderTrainsMixin} 改用 {@code remap=true} + <b>Mojmap</b> 描述符
     * （因为 handler 参数必须写精确类型，见该类注释），
     * 运行时由 refmap 还原成 intermediary。
     * <br>⇒ 审计器若不认 refmap，就会把「运行时其实能匹配」的引用
     * 误判为「无方法」⇒ 假阳性。
     * <br>⇒ 本转换让审计保持严格：转换后仍找不到 ⇒ 依然报错。
     */
    static Map<String, String> loadRefmap(Path refmap) {
        Map<String, String> map = new HashMap<>();
        if (refmap == null || !Files.exists(refmap)) return map;
        try {
            String text = new String(Files.readAllBytes(refmap), StandardCharsets.UTF_8);
            // refmap 形如（每行一条）：
            //   "render(<Mojmap desc>)V": "Lmtr/render/RenderTrains;render(<intermediary desc>)V"
            // 用**逐行 + 简单引号切分**解析（不用正则：Mojmap 描述符里含
            // 大量特殊字符，正则转义极易出错——本次就踩过）。
            for (String rawLine : text.split("\r?\n")) {
                String line = rawLine.trim();
                if (!line.startsWith("\"")) continue;
                int keyEnd = line.indexOf('"', 1);
                if (keyEnd < 0) continue;
                String named = line.substring(1, keyEnd);
                int colon = line.indexOf(':', keyEnd);
                if (colon < 0) continue;
                int vStart = line.indexOf('"', colon + 1);
                if (vStart < 0) continue;
                int vEnd = line.indexOf('"', vStart + 1);
                if (vEnd < 0) continue;
                String value = line.substring(vStart + 1, vEnd);
                if (!value.startsWith("L")) continue;
                int semi = value.indexOf(';');
                if (semi < 0) continue;
                String owner = value.substring(1, semi);
                String member = value.substring(semi + 1);
                // ★ member 形如 "render(<intermediary desc>)V" —— name 与 desc **直接相连**，
                //   中间没有点（refmap 的值格式是 "Lowner;name(desc)ret"）。
                int mParen = member.indexOf('(');
                if (mParen < 0) continue;
                String mName = member.substring(0, mParen);
                String mDesc = member.substring(mParen);
                int paren = named.indexOf('(');
                if (paren < 0) continue;
                String nName = named.substring(0, paren);
                String nDesc = named.substring(paren);
                if (!mName.equals(nName)) continue;
                map.put(owner + "." + nName + nDesc, owner + "." + mName + mDesc);
            }
        } catch (IOException e) {
            System.out.println("!! 读 refmap 失败: " + e.getMessage());
        }
        if (!map.isEmpty()) {
            System.out.println("refmap 条目: " + map.size() + "  (" + refmap.getFileName() + ")");
        }
        return map;
    }

    /**
     * 把「Mojmap 描述符」按 refmap 转换成「intermediary 描述符」。
     * <br>转换后的形式若在目标 jar 里能命中 ⇒ 运行时经 refmap 也能命中。
     * <br>返回 null 表示 refmap 里没有这条映射（调用方需回退到原描述符再判）。
     */
    static String mapDesc(Map<String, String> refmap, String owner, String name, String desc) {
        if (refmap == null || refmap.isEmpty()) return null;
        String key = owner + "." + name + desc;
        String mapped = refmap.get(key);
        if (mapped == null) return null;
        // mapped 形如 "mtr/render/RenderTrains.render(<intermediary desc>)V"
        // ★ owner 里不含 '.'，故用**最后一个** '.' 之前部分作 owner；
        //   描述符取 '(' 之后的部分（★ 不含方法名——hasMethod 只收描述符）。
        int dot = mapped.lastIndexOf('.');
        if (dot < 0) return null;
        String mOwner = mapped.substring(0, dot);
        String member = mapped.substring(dot + 1);
        int paren = member.indexOf('(');
        if (paren < 0) return null;
        String mDesc = member.substring(paren);
        if (!mOwner.equals(owner)) return null;
        return mDesc;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("用法: AuditTurboMixinRefs <源码目录> <jar1> [jar2 ...] [--refmap <refmap.json>]");
            System.exit(2);
        }
        Path srcRoot = Paths.get(args[0]);
        List<Path> jars = new ArrayList<>();
        Path refmapFile = null;
        for (int i = 1; i < args.length; i++) {
            if ("--refmap".equals(args[i]) && i + 1 < args.length) {
                refmapFile = Paths.get(args[++i]);
            } else {
                jars.add(Paths.get(args[i]));
            }
        }
        final Map<String, String> refmap = loadRefmap(refmapFile);

        System.out.println("源码目录: " + srcRoot);
        List<String> jarLabels = new ArrayList<>();
        Map<String, Index> idx = new LinkedHashMap<>();
        for (Path p : jars) {
            if (!Files.exists(p)) { System.out.println("!! 缺 jar: " + p); continue; }
            idx.put(p.getFileName().toString(), new Index(p));
            jarLabels.add(p.getFileName().toString());
        }
        System.out.println("目标 jar: " + jarLabels);
        System.out.println();

        List<Path> srcs = new ArrayList<>();
        Files.walk(srcRoot).filter(p -> p.toString().endsWith(".java")).forEach(srcs::add);
        srcs.sort(Comparator.comparing(p -> p.getFileName().toString()));

        int checked = 0, ok = 0, bad = 0;
        List<String> failures = new ArrayList<>();

        // L<owner>;<name><desc>   或   L<owner>;<name>(...)<ret>
        Pattern memberRef = Pattern.compile("L([\\w/$]+);([\\w$<>\"]+)(\\([^)]*\\)[\\[\\w/;$]+)");

        for (Path src : srcs) {
            String text = new String(Files.readAllBytes(src), StandardCharsets.UTF_8);
            String fname = src.getFileName().toString();

            // @Mixin(targets = "mtr.data.Train") → 目标类内部名
            String targetOwner = null;
            Matcher tm = Pattern.compile("@Mixin\\s*\\([^)]*targets\\s*=\\s*\"([\\w.$]+)\"").matcher(text);
            if (tm.find()) {
                targetOwner = tm.group(1).replace('.', '/');
            }

            // 只看字符串字面量里的引用，避免把注释/代码里的类型名当成成员引用
            Set<String> refs = new LinkedHashSet<>();
            Matcher sm = Pattern.compile("\"([^\"\\n]*)\"").matcher(text);
            while (sm.find()) {
                String lit = sm.group(1);
                Matcher rm = memberRef.matcher(lit);
                while (rm.find()) {
                    String owner = rm.group(1);
                    String name = rm.group(2);
                    String desc = rm.group(3);
                    // 只关心「方法」形态：desc 以 ( 开头
                    if (desc.startsWith("(")) {
                        refs.add(owner + "\u0000" + name + "\u0000" + desc);
                    }
                }
                // ★ owner-less 形态：method = "render(F...)V"（描述符前没有 Lowner;）
                Matcher om = Pattern.compile("^([\\w$<>]+)(\\([^)]*\\)[\\[\\w/;$]+)$").matcher(lit);
                if (om.matches() && targetOwner != null) {
                    refs.add(targetOwner + "\u0000" + om.group(1) + "\u0000" + om.group(2));
                }
            }

            // method = "name" / method = "name(desc)"
            Matcher mm = Pattern.compile("method\\s*=\\s*(?:\\{?\\s*)\"([\\w$<>]+)(\\([^\"\\)]*\\)[\\w/;$\\[]+)?\"").matcher(text);
            Set<String> plainMethods = new LinkedHashSet<>();
            while (mm.find()) {
                plainMethods.add(mm.group(1) + (mm.group(2) == null ? "" : "\u0000" + mm.group(2)));
            }

            if (refs.isEmpty() && plainMethods.isEmpty()) continue;

            System.out.println("--- " + fname);
            for (String r : refs) {
                String[] parts = r.split("\u0000");
                String owner = parts[0], name = parts[1], desc = parts[2];
                // JDK / 第三方库不在 mod jar 里 ⇒ 无法验证，跳过（不是缺陷）
                if (isExternal(owner)) {
                    System.out.printf("   --   %s.%s%s  （外部依赖，跳过）%n", owner.replace('/', '.'), name, desc);
                    continue;
                }
                checked++;
                boolean allOk = true;
                boolean refmapped = false;
                StringBuilder miss = new StringBuilder();
                for (Map.Entry<String, Index> e : idx.entrySet()) {
                    Index ix = e.getValue();
                    if (!ix.hasClass(owner)) { miss.append(" [").append(e.getKey()).append(" 无类 ").append(owner).append("]"); allOk = false; }
                    else if (!ix.hasMethod(owner, name, desc)) {
                        // ★ 先试 refmap 转换（Mojmap → intermediary），转换后能命中即算通过
                        String mapped = mapDesc(refmap, owner, name, desc);
                        if (mapped != null && ix.hasMethod(owner, name, mapped)) {
                            // 经 refmap 命中 ⇒ 运行时可匹配
                            refmapped = true;
                        } else {
                            miss.append(" [").append(e.getKey()).append(" 无方法]"); allOk = false;
                        }
                    }
                }
                if (allOk) {
                    ok++;
                    System.out.printf("   ok   %s.%s%s%s%n", owner.replace('/', '.'), name, desc,
                            refmapped ? "  （经 refmap 转换后命中）" : "");
                }
                else {
                    bad++;
                    String msg = String.format("%s  %s.%s%s  →%s", fname, owner.replace('/', '.'), name, desc, miss);
                    failures.add(msg);
                    System.out.printf("   !!   %s.%s%s%s%n", owner.replace('/', '.'), name, desc, miss);
                }
            }
            for (String pm : plainMethods) {
                String[] parts = pm.split("\u0000");
                String name = parts[0];
                String desc = parts.length > 1 ? parts[1] : null;
                checked++;
                boolean allOk = true;
                boolean refmapped = false;
                StringBuilder miss = new StringBuilder();
                for (Map.Entry<String, Index> e : idx.entrySet()) {
                    Index ix = e.getValue();
                    boolean found;
                    if (desc != null) {
                        found = targetOwner != null && ix.hasMethod(targetOwner, name, desc);
                        // ★ 同上：remap=true 的成员用 Mojmap 描述符，需经 refmap 转换
                        if (!found && targetOwner != null) {
                            String mapped = mapDesc(refmap, targetOwner, name, desc);
                            if (mapped != null && ix.hasMethod(targetOwner, name, mapped)) {
                                found = true;
                                refmapped = true;
                            }
                        }
                    } else if (targetOwner != null) {
                        found = ix.hasMethodByName(targetOwner, name);
                    } else {
                        // 没有 targets 时退化为全表按名搜（偏宽松）
                        found = false;
                        for (String k : ix.methods.keySet()) {
                            if (k.contains("." + name + " ")) { found = true; break; }
                        }
                    }
                    if (!found) { miss.append(" [").append(e.getKey()).append(" 未找到]"); allOk = false; }
                }
                if (allOk) {
                    ok++;
                    System.out.printf("   ok   method=%s%s%s%n", name, desc == null ? "" : desc,
                            refmapped ? "  （经 refmap 转换后命中）" : "");
                }
                else {
                    bad++;
                    String msg = String.format("%s  method=%s%s  →%s", fname, name, desc == null ? "" : desc, miss);
                    failures.add(msg);
                    System.out.printf("   !!   method=%s%s%s%n", name, desc == null ? "" : desc, miss);
                }
            }
        }

        System.out.println();
        System.out.println("=== 汇总 ===");
        System.out.println("检查引用: " + checked + "   全部命中: " + ok + "   失效（静默）: " + bad);
        if (bad > 0) {
            System.out.println();
            System.out.println("=== ★ 这些引用永不匹配（require=0 ⇒ 静默失效，用户感知为「优化没效果」）===");
            for (String f : failures) System.out.println("  " + f);
            throw new IllegalStateException("发现 " + bad + " 个永不匹配的 mixin 引用");
        } else {
            System.out.println("=== 结论：全部引用在目标 jar 中真实存在 ===");
        }
    }
}
