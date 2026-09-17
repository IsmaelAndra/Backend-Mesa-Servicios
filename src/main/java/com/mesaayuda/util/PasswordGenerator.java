package com.mesaayuda.util;

import java.security.SecureRandom;

public final class PasswordGenerator {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "!@#$%";

    private PasswordGenerator() {
    }

    public static String generate(int length) {
        if (length < 8) {
            throw new IllegalArgumentException("Longitud mínima de contraseña: 8");
        }
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);

        sb.append(LOWER.charAt(random.nextInt(LOWER.length())));
        sb.append(UPPER.charAt(random.nextInt(UPPER.length())));
        sb.append(DIGITS.charAt(random.nextInt(DIGITS.length())));
        sb.append(DIGITS.charAt(random.nextInt(DIGITS.length())));
        sb.append(SYMBOLS.charAt(random.nextInt(SYMBOLS.length())));

        String all = LOWER + UPPER + DIGITS + SYMBOLS;
        for (int i = 5; i < length; i++) {
            sb.append(all.charAt(random.nextInt(all.length())));
        }

        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }

    public static String generate() {
        return generate(10);
    }
}