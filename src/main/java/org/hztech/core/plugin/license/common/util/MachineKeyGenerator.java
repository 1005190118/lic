package org.hztech.core.plugin.license.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Set;

/**
 * 纯 Java 的机器码算法，可用固定输入进行单元测试。
 *
 * 注意：此处的 V2 与旧 CPU-ID+UUID 算法不兼容；签发端和校验端必须共用版本与算法。
 * 这不是不可伪造的硬件身份证明，License 本身仍应使用非对称签名验证。
 */
public final class MachineKeyGenerator {

    public static final int VERSION = 2;

    // 已知重复/占位 UUID；统一转为 32 位十六进制再比对。
    private static final Set<String> INVALID_UUIDS = Set.of(
            "00000000000000000000000000000000",
            "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
            "03000200040005000006000700080009",
            "4203018E580FC1B59525B745CECA79EB"
    );

    private static final Set<String> INVALID_SERIALS = Set.of(
            "UNKNOWN", "NONE", "NULL", "NA", "NOTAVAILABLE", "NOTSPECIFIED",
            "NOTAPPLICABLE", "UNDEFINED", "DEFAULTSTRING", "SYSTEMSERIALNUMBER",
            "TOBEFILLEDBYOEM", "NOSERIALNUMBER", "OEMPENDING", "0123456789",
            "123456789", "1234567890"
    );

    private MachineKeyGenerator() {
    }

    public enum Source {
        HARDWARE_UUID("硬件 UUID"),
        SYSTEM_SERIAL("整机序列号"),
        BOARD_SERIAL("主板序列号");

        private final String displayName;

        Source(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    public record Result(String machineKey, Source source, int version) {
    }

    /** 优先用规范化硬件 UUID；若不可用，再降级到整机、主板序列号。 */
    public static Result generate(String hardwareUuid, String systemSerial, String boardSerial) {
        String uuid = canonicalUuid(hardwareUuid);
        if (uuid != null) {
            return result(Source.HARDWARE_UUID, uuid);
        }

        String serial = canonicalSerial(systemSerial);
        if (serial != null) {
            return result(Source.SYSTEM_SERIAL, serial);
        }

        String board = canonicalSerial(boardSerial);
        if (board != null) {
            return result(Source.BOARD_SERIAL, board);
        }

        throw new IllegalStateException("无法读取有效的硬件 UUID、整机序列号或主板序列号。请在真实设备上检查系统权限。");
    }

    private static Result result(Source source, String identifier) {
        String input = "HZTECH|MACHINE|V2|" + source.name() + "|" + identifier;
        return new Result(sha256(input), source, VERSION);
    }

    private static String canonicalUuid(String value) {
        String cleaned = normalize(value);
        if (cleaned.isEmpty()) {
            return null;
        }
        // UUID 的两种常见格式（有/无连字符）统一到 32 位大写十六进制。
        String compact = cleaned.replace("-", "");
        if (!compact.matches("[0-9A-F]{32}") || INVALID_UUIDS.contains(compact)) {
            return null;
        }
        return compact;
    }

    private static String canonicalSerial(String value) {
        String cleaned = normalize(value);
        String compact = cleaned.replaceAll("[^A-Z0-9]", "");
        if (compact.length() < 5 || INVALID_SERIALS.contains(compact)
                || compact.matches("0+|F+")) {
            return null;
        }
        return cleaned;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    static String sha256(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            char[] hex = new char[hash.length * 2];
            final char[] digits = "0123456789ABCDEF".toCharArray();
            for (int i = 0; i < hash.length; i++) {
                int b = hash[i] & 0xFF;
                hex[i * 2] = digits[b >>> 4];
                hex[i * 2 + 1] = digits[b & 0x0F];
            }
            return new String(hex);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
