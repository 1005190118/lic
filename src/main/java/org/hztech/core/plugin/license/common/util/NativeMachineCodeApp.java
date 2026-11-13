package org.hztech.core.plugin.license.common.util;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * GraalVM Native Image 入口：只依赖 JDK，不加载 Swing / OSHI / JNA。
 * 默认输出 V2 机器码，Windows 双击无参数时尝试弹出原生系统对话框。
 */
public final class NativeMachineCodeApp {
    private static final String SAMPLE_UUID = "ABCD1234-1234-4567-8910-ABCDABCDABCD";

    private NativeMachineCodeApp() {
    }

    public static void main(String[] args) {
        if (args.length == 1 && ("--help".equals(args[0]) || "-h".equals(args[0]))) {
            System.out.println("HzTech Machine Code — GraalVM Native Image");
            System.out.println("  --cli        输出一行机器码（默认行为）");
            System.out.println("  --show       使用系统自带对话框展示机器码（需要 PowerShell/osascript/zenity）");
            System.out.println("  --info       输出 V2 算法版本、硬件标识来源及机器码");
            System.out.println("  --self-test  不读取真实硬件，验证内置算法");
            System.out.println("  --help       查看帮助");
            System.out.println("  V1 --legacy 请使用保留的 Java/JAR 版。");
            return;
        }
        if (args.length == 1 && "--self-test".equals(args[0])) {
            var result = MachineKeyGenerator.generate(SAMPLE_UUID, null, null);
            System.out.println(result.machineKey());
            return;
        }
        if (args.length > 1 || (args.length == 1 && !Arrays.asList("--cli", "--show", "--info").contains(args[0]))) {
            System.err.println("参数错误。请使用 --help 查看用法。");
            System.exit(2);
            return;
        }
        try {
            MachineKeyGenerator.Result result = NativeHardwareReader.read().machineKeyResult();
            if (args.length == 1 && "--info".equals(args[0])) {
                System.out.println("version=V" + result.version());
                System.out.println("source=" + result.source().name());
                System.out.println("machineKey=" + result.machineKey());
                return;
            }
            boolean show = args.length == 1 && "--show".equals(args[0]);
            // Windows 客户双击 exe（无参数）时默认尝试弹窗；--cli 始终只输出机器码。
            boolean windowsDefault = args.length == 0
                    && System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
            if ((show || windowsDefault) && showDialog(result.machineKey())) {
                return;
            }
            System.out.println(result.machineKey());
        } catch (RuntimeException e) {
            System.err.println("生成机器码失败：" + e.getMessage());
            System.exit(1);
        }
    }

    /** 可用时显示系统原生对话框，失败则交由主流程输出 CLI 结果。 */
    private static boolean showDialog(String machineKey) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            // 仅将 SHA-256 大写十六进制值插入固定脚本，避免命令注入。
            String script = "Add-Type -AssemblyName System.Windows.Forms; "
                    + "$f = New-Object System.Windows.Forms.Form; $f.Text='HzTech 机器码 V2'; "
                    + "$f.Width=700; $f.Height=200; $f.StartPosition='CenterScreen'; "
                    + "$t=New-Object System.Windows.Forms.TextBox; $t.Left=20; $t.Top=20; $t.Width=640; "
                    + "$t.ReadOnly=$true; $t.Text='" + machineKey + "'; "
                    + "$b=New-Object System.Windows.Forms.Button; $b.Text='复制机器码'; $b.Left=20; $b.Top=65; "
                    + "$b.Width=120; $b.Add_Click({[System.Windows.Forms.Clipboard]::SetText($t.Text)}); "
                    + "$f.Controls.Add($t); $f.Controls.Add($b); [void]$f.ShowDialog()";
            return run("powershell.exe", "-NoLogo", "-NoProfile", "-STA", "-Command", script);
        }
        if (os.contains("mac")) {
            String script = "display dialog \"机器码（可复制）\\n" + machineKey
                    + "\" with title \"HzTech 机器码 V2\" buttons {\"确定\"} default button \"确定\"";
            return run("/usr/bin/osascript", "-e", script);
        }
        if (os.contains("linux")) {
            return run("zenity", "--info", "--title=HzTech 机器码 V2", "--text=" + machineKey);
        }
        return false;
    }

    private static boolean run(String... argv) {
        try {
            Process process = new ProcessBuilder(argv).inheritIO().start();
            if (!process.waitFor(120, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
