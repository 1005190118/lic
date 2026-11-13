package org.hztech.core.plugin.license.common.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeHardwareReaderTest {
    @Test
    void windowsCimOutputParses() {
        var map = NativeHardwareReader.parseKeyValue("UUID=ABCD-1234\nSYSTEM=SN-1\nBOARD=BD-2\n");
        assertEquals("ABCD-1234", map.get("UUID"));
        assertEquals("SN-1", map.get("SYSTEM"));
        assertEquals("BD-2", map.get("BOARD"));
    }

    @Test
    void macIoregOutputParses() {
        String output = "    \"IOPlatformUUID\" = \"ABCD-1234\"\n    \"IOPlatformSerialNumber\" = \"SYS123\"";
        var map = NativeHardwareReader.parseIoreg(output);
        assertEquals("ABCD-1234", map.get("IOPlatformUUID"));
        assertEquals("SYS123", map.get("IOPlatformSerialNumber"));
    }

    @Test
    void nativeAndJvmV2UseIdenticalHashFunction() {
        var nativeInput = new NativeHardwareReader.HardwareInfo(
                "abcd1234-1234-4567-8910-abcdabcdabcd", "SN123", "BD123");
        var jvmInput = MachineKeyGenerator.generate(
                "ABCD1234123445678910ABCDABCDABCD", "OTHER123", "OTHER456");
        assertEquals(jvmInput.machineKey(), nativeInput.machineKeyResult().machineKey());
    }
}
