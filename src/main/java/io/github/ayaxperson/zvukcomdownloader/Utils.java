package io.github.ayaxperson.zvukcomdownloader;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadLocalRandom;

public class Utils {

    @NotNull
    public static <T> T notNull(final T object) {
        if (object == null)
            throw new NullPointerException();
        return object;
    }

    private static final String AZ09 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";

    public static String getRandomString(final int length) {
        final char[] result = new char[length];

        for (int i = 0; i < length; i++) {
            result[i] = AZ09.charAt(ThreadLocalRandom.current().nextInt(AZ09.length()));

            if (ThreadLocalRandom.current().nextBoolean())
                result[i] = Character.toLowerCase(result[i]);
        }

        return new String(result);
    }

}
