package org.hztech.core.plugin.license.common.util;

import oshi.SystemInfo;
import oshi.hardware.Baseboard;
import oshi.hardware.CentralProcessor;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HardwareAbstractionLayer;

import java.util.List;

/**
 * 硬件信息读取层（OSHI）。仅本机读取，不发送任何网络请求。
 * 兼容原工具类的公开接口。
 */
public final class GetMachineUtil {

    private static final SystemInfo SYSTEM_INFO = new SystemInfo();
    private static final HardwareAbstractionLayer HARDWARE = SYSTEM_INFO.getHardware();

    private GetMachineUtil() {
    }

    /** OSHI 的处理器特征 ID；并非每颗 CPU 唯一的序列号。 */
    public static String getCpuId() {
        CentralProcessor processor = HARDWARE.getProcessor();
        return processor.getProcessorIdentifier().getProcessorID();
    }

    public static String getMainboardSerialNumber() {
        Baseboard board = HARDWARE.getComputerSystem().getBaseboard();
        return board.getSerialNumber();
    }

    /** OSHI ComputerSystem.hardwareUUID；并不保证永远唯一或永远不变。 */
    public static String getMainboardUuid() {
        return HARDWARE.getComputerSystem().getHardwareUUID();
    }

    public static String getSystemSerialNumber() {
        return HARDWARE.getComputerSystem().getSerialNumber();
    }

    /** 仅展示用途，磁盘枚举顺序不保证稳定。 */
    public static String getDiskSerialNumber() {
        List<HWDiskStore> disks = HARDWARE.getDiskStores();
        return disks == null || disks.isEmpty() ? null : disks.get(0).getSerial();
    }

    public static MachineKeyGenerator.Result getMachineKeyResult() {
        return MachineKeyGenerator.generate(
                getMainboardUuid(),
                getSystemSerialNumber(),
                getMainboardSerialNumber()
        );
    }

    /** 新版（V2）机器码，64 位大写 SHA-256 十六进制。 */
    public static String getMachineKey() {
        return getMachineKeyResult().machineKey();
    }

    /**
     * 旧版 V1 算法，逐字兼容原 CPU ID + UUID 的拼接与 SHA-256 计算。
     * 仅用于已发授权的迁移；请勿用于新签发授权。
     */
    @Deprecated
    public static String getLegacyMachineKey() {
        String cpu = getCpuId();
        String uuid = getMainboardUuid();
        StringBuilder raw = new StringBuilder();
        if (cpu != null && !cpu.isEmpty()) {
            raw.append(cpu);
        }
        if (uuid != null && !uuid.isEmpty()) {
            raw.append(uuid);
        }
        if (raw.length() == 0) {
            throw new IllegalStateException("旧版机器码获取失败：CPU ID 和 UUID 均为空");
        }
        return MachineKeyGenerator.sha256(raw.toString());
    }

    public static String cleanMachineSn(String machineSn) {
        return machineSn == null ? null : machineSn.replaceAll("\\s+", "");
    }
}
