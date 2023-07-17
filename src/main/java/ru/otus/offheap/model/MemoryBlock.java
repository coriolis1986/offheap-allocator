package ru.otus.offheap.model;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.EqualsAndHashCode.Include;

@Data
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MemoryBlock {

    @Include private final long size;
    @Include private final long address;

    private String name;
    private String fullClassName;
    private boolean deleted;

    public MemoryBlock clone(long address) {
        MemoryBlock newBlock = MemoryBlock.builder()
                .size(this.size)
                .address(address)
                .build();

        newBlock.name = this.name;
        newBlock.fullClassName = this.fullClassName;
        newBlock.deleted = this.deleted;

        return newBlock;
    }

    @Override
    public String toString() {
        return address + " - " + (address + size) + " - " + deleted + " - " + name;
    }
}
