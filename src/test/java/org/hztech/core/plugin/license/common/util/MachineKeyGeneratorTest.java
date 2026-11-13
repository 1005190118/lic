package org.hztech.core.plugin.license.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MachineKeyGeneratorTest {

    @Test
    void sameUuidWithDifferentFormattingProducesSameKey() {
        var a = MachineKeyGenerator.generate("abcd1234-1234-4567-8910-abcdabcdabcd", "SYS123", "BOARD123");
        var b = MachineKeyGenerator.generate(" ABCD1234123445678910ABCDABCDABCD ", "SYS789", "BOARD789");
        assertEquals(a.machineKey(), b.machineKey());
        assertEquals(MachineKeyGenerator.Source.HARDWARE_UUID, a.source());
        assertEquals(64, a.machineKey().length());
        assertTrue(a.machineKey().matches("[A-F0-9]{64}"));
    }

    @Test
    void invalidUuidFallsBackToSystemSerial() {
        var a = MachineKeyGenerator.generate("00000000-0000-0000-0000-000000000000", "System-AB12", "Board-XY99");
        assertEquals(MachineKeyGenerator.Source.SYSTEM_SERIAL, a.source());
    }

    @Test
    void invalidSystemSerialFallsBackToBoard() {
        var a = MachineKeyGenerator.generate("unknown", "To be filled by O.E.M.", "Board-XY99");
        assertEquals(MachineKeyGenerator.Source.BOARD_SERIAL, a.source());
    }

    @Test
    void missingIdentifiersAreRejected() {
        assertThrows(IllegalStateException.class, () -> MachineKeyGenerator.generate(null, "unknown", "00000000"));
    }

    @Test
    void resultIsDeterministic() {
        var a = MachineKeyGenerator.generate("abcd1234-1234-4567-8910-abcdabcdabcd", null, null);
        var b = MachineKeyGenerator.generate("abcd1234-1234-4567-8910-abcdabcdabcd", null, null);
        assertEquals(a, b);
        assertEquals(2, a.version());
    }
    @Test
    void v2KnownHashVectorMustNeverChangeSilently() {
        var result = MachineKeyGenerator.generate(
                "ABCD1234-1234-4567-8910-ABCDABCDABCD", "SYSTEM123", "BOARD123");
        assertEquals("248CA17637812C7487BCEF91A7537F4B8B1D312E3DB48496B34358CEB849C7D8",
                result.machineKey());
    }

}
