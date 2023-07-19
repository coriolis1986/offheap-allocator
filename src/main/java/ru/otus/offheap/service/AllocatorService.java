package ru.otus.offheap.service;

import java.io.Serializable;
import java.util.List;

public interface AllocatorService {

    String set(Serializable obj);

    List<Serializable> get(String name);

    void remove(String name);

    void link(String parent, String child);

    void unlink(String parent, String child);

    long free();
}
