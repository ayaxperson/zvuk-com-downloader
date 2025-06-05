package io.github.ayaxperson.zvukcomdownloader;

import org.jetbrains.annotations.NotNull;

public class Objects {

    @NotNull
    public static <T> T notNull(final T object) {
        if (object == null)
            throw new NullPointerException();
        return object;
    }

}
