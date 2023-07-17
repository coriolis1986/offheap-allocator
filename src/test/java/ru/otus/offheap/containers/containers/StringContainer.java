package ru.otus.offheap.containers.containers;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class StringContainer implements Serializable {

    private final String str1;
    private final String str2;

    @Override
    public String toString() {
        return str1 + " " + str2;
    }
}
