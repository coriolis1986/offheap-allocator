package ru.otus.offheap.storage;

import ru.otus.offheap.model.MemoryBlock;

import java.util.stream.Stream;

public interface MemoryBlockStorage {

    MemoryBlock insert(MemoryBlock memoryBlock);

    void remove(MemoryBlock memoryBlock);

    MemoryBlock getByName(String name);

    void mergeDeletedBlocks();

    void clear();

    Stream<MemoryBlock> stream();

    int totalSize();
}
