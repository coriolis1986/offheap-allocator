package ru.otus.offheap.service;

import java.io.Serializable;

public interface AllocatorService {

    String set(Serializable obj);

    <T> T get(String name, Class<T> cl);

    void remove(String name);

    long free();
}
