package com.mesaayuda.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordGeneratorTest {

    @Test
    void generate_returnsTenCharsByDefault() {
        assertEquals(10, PasswordGenerator.generate().length());
    }

    @Test
    void generate_withLength_returnsExpectedSize() {
        assertEquals(12, PasswordGenerator.generate(12).length());
        assertEquals(8, PasswordGenerator.generate(8).length());
    }

    @Test
    void generate_containsAtLeastOneOfEachClass() {
        for (int i = 0; i < 50; i++) {
            String pwd = PasswordGenerator.generate();
            assertTrue(pwd.matches(".*[a-z].*"), "debe tener minúscula: " + pwd);
            assertTrue(pwd.matches(".*[A-Z].*"), "debe tener mayúscula: " + pwd);
            assertTrue(pwd.matches(".*[0-9].*"), "debe tener dígito: " + pwd);
            assertTrue(pwd.matches(".*[!@#$%].*"), "debe tener símbolo: " + pwd);
        }
    }

    @Test
    void generate_producesUniqueValues() {
        String a = PasswordGenerator.generate();
        String b = PasswordGenerator.generate();
        assertNotEquals(a, b);
    }

    @Test
    void generate_withShortLength_throws() {
        assertThrows(IllegalArgumentException.class, () -> PasswordGenerator.generate(7));
    }
}