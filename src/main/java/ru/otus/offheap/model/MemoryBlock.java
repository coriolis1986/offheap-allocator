package ru.otus.offheap.model;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.EqualsAndHashCode.Include;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Long.toHexString;
import static java.lang.String.format;

@Data
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MemoryBlock {

    @Include private final long size;
    @Include private final long address;
    private List<MemoryBlock> links;

    private String name;
    private String fullClassName;
    private boolean deleted;
    private boolean root;

    public MemoryBlock clone(long address) {
        MemoryBlock newBlock = MemoryBlock.builder()
                .size(this.size)
                .address(address)
                .build();

        newBlock.name = this.name;
        newBlock.fullClassName = this.fullClassName;
        newBlock.deleted = this.deleted;
        newBlock.links = new ArrayList<>(links);

        return newBlock;
    }

    @Override
    public String toString() {
        return format("%s:\n    size [%d], address [0x%s], class [%s], childs [%s]\n\n",
                deleted ? "_deleted_" : name,
                size,
                toHexString(address),
                fullClassName,
                links.size());
    }
}
