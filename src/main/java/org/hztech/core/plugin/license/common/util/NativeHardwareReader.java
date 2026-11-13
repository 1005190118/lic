package org.hztech.core.plugin.license.common.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Native Image 硬件读取器：不依赖 OSHI/JNA/JNI。
 *
 * 机器码哈希规则不变：将读取结果交给 MachineKeyGenerator.generate。
 * 硬件读取来源与 OSHI 尽量对应，但不能承诺任何平台的底层结果永远完全一致。
 */
public final class NativeHardwareReader {
    private static final long COMMAND_TIMEOUT_SECONDS = 10;
    private static final Pattern IOREG_PAIR = Pattern.compile("\"([^\"]+)\"\\s*=\\s*\"([^\"]*)\"");

    private NativeHardwareReader() {
    }

    public record HardwareInfo(String uuid, String systemSerial, String boardSerial) {
        public MachineKeyGenerator.Result machineKeyResult() {
            return MachineKeyGenerator.generate(uuid, systemSerial, boardSerial);
        }
    }

    public static HardwareInfo read() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return readWindows();
        }
        if (os.contains("mac")) {
            return readMac();
        }
        if (os.contains("linux")) {
            return readLinux();
        }
        throw new UnsupportedOperationException("暂不支持此操作系统：" + os);
    }

    private static HardwareInfo readLinux() {
        return new HardwareInfo(
                readDmi("product_uuid"),
                readDmi("product_serial"),
                readDmi("board_serial")
        );
    }

    private static String readDmi(String filename) {
        for (String root : new String[]{"/sys/devices/virtual/dmi/id/", "/sys/class/dmi/id/"}) {
            try {
                Path file = Path.of(root, filename);
                if (Files.isReadable(file)) {
                    String value = Files.readString(file, StandardCharsets.UTF_8).strip();
                    if (!value.isEmpty()) {
                        return value;
                    }
                }
            } catch (IOException | SecurityException ignored) {
                // 某些 Linux 配置默认限制读取序列号。继续尝试其他位置。
            }
        }
        return "unknown";
    }

    private static HardwareInfo readWindows() {
        // 固定脚本，不接受外部输入；只读取硬件信息，不修改计算机设置。
        String script = String.join("; ",
                "$ErrorActionPreference='SilentlyContinue'",
                "$p=Get-CimInstance -ClassName Win32_ComputerSystemProduct",
                "$b=Get-CimInstance -ClassName Win32_BaseBoard",
                "$bios=Get-CimInstance -ClassName Win32_BIOS",
                "Write-Output ('UUID=' + $p.UUID)",
                "Write-Output ('SYSTEM=' + $p.IdentifyingNumber)",
                "Write-Output ('BIOS=' + $bios.SerialNumber)",
                "Write-Output ('BOARD=' + $b.SerialNumber)"
        );
        Map<String, String> values = parseKeyValue(run("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-Command", script));
        String systemSerial = firstCandidate(values.get("SYSTEM"), values.get("BIOS"));
        return new HardwareInfo(values.get("UUID"), systemSerial, values.get("BOARD"));
    }

    private static HardwareInfo readMac() {
        // macOS IOPlatformExpertDevice 与 OSHI 的平台硬件 UUID 数据来源相同。
        String output = run("/usr/sbin/ioreg", "-rd1", "-c", "IOPlatformExpertDevice");
        Map<String, String> values = parseIoreg(output);
        return new HardwareInfo(
                values.get("IOPlatformUUID"),
                values.get("IOPlatformSerialNumber"),
                values.getOrDefault("board-serial-number", "unknown")
        );
    }

    /** 解析 CIM 输出；仅用已知的固定键。 */
    static Map<String, String> parseKeyValue(String output) {
        Map<String, String> result = new HashMap<>();
        for (String line : output.split("\\R")) {
            int idx = line.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            String key = line.substring(0, idx).trim();
            if (key.equals("UUID") || key.equals("SYSTEM") || key.equals("BIOS") || key.equals("BOARD")) {
                result.put(key, line.substring(idx + 1).trim());
            }
        }
        return result;
    }

    /** 解析 ioreg 的引号字符串键值。 */
    static Map<String, String> parseIoreg(String output) {
        Map<String, String> values = new HashMap<>();
        Matcher matcher = IOREG_PAIR.matcher(output);
        while (matcher.find()) {
            values.put(matcher.group(1), matcher.group(2));
        }
        return values;
    }

    private static String firstCandidate(String preferred, String fallback) {
        // 与 V2 算法使用同一套有效性判断，不把 OEM 占位值当作真实序列号。
        try {
            MachineKeyGenerator.generate(null, preferred, null);
            return preferred;
        } catch (IllegalStateException ignored) {
            return fallback;
        }
    }

    /**
     * 执行系统自带工具；限制超时、合并 stdout/stderr，失败时回退至备用来源。
     */
    private static String run(String... argv) {
        try {
            Process process = new ProcessBuilder(argv).redirectErrorStream(true).start();
            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "";
            }
            if (process.exitValue() != 0) {
                return "";
            }
            return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }
}
