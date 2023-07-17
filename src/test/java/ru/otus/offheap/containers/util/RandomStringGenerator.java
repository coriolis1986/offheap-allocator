package ru.otus.offheap.containers.util;

import java.util.Random;

public class RandomStringGenerator {

    private static final Random RANDOM = new Random();

    public static String generate(int length) {
        int leftLimit = 97;
        int rightLimit = 122;

        return RANDOM.ints(leftLimit, rightLimit + 1)
                .limit(length)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }
}
