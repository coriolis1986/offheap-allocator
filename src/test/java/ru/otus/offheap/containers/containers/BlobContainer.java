package ru.otus.offheap.containers.containers;

import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;

@Builder
public class BlobContainer implements Serializable {

    @Getter private int[] arr;
}
