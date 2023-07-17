package ru.otus.offheap.containers.containers;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class BigStringContainer implements Serializable {

    private final String title;
    private final StringContainer con1;
    private final StringContainer con2;

    @Override
    public String toString() {
        return title + " " + con1 + " " + con2;
    }
}
