package ru.otus.offheap.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.otus.offheap.exception.ObjectNotFoundException;
import ru.otus.offheap.model.MemoryBlock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Comparator.comparingLong;

@Service
@Slf4j
public class MemoryBlockStorageImpl implements MemoryBlockStorage {

    @Getter
    private final TreeMap<Long, MemoryBlock> blocks = new TreeMap<>();
    private final Map<String, MemoryBlock> namedBlocks = new HashMap<>();
    private int size = 0;

    private static final MemoryBlock ROOT_BLOCK = MemoryBlock.builder()
            .name("root_block")
            .links(new ArrayList<>())
            .root(true)
            .fullClassName("")
            .build();

    private static final String DELETED_NAME = "deleted";

    @Override
    public MemoryBlock insert(MemoryBlock block) {
        if (namedBlocks.containsKey(block.getName()))
            throw new RuntimeException("Block [" + block.getName() + "] already exists");

        if (!blocks.isEmpty()) {
            var suitableBlocks = findBlocksForMerging(block.getSize());

            if (!suitableBlocks.isEmpty()) {
                log.info("Found {} blocks for merging. Reusing {} bytes",
                        suitableBlocks.size(),
                        suitableBlocks.stream().mapToLong(MemoryBlock::getSize).sum()
                );

                var usedSpace = new AtomicLong();

                suitableBlocks.stream().map(MemoryBlock::getAddress)
                        .forEach(address -> {
                            var currentDeletedBlock = blocks.get(address);

                            usedSpace.addAndGet(currentDeletedBlock.getSize());
                            blocks.remove(address);
                        });

                final var unusedSpace = - (block.getSize() - usedSpace.get());

                block = block.clone(suitableBlocks.iterator().next().getAddress());

                if (unusedSpace > 0) {
                    var newDeletedBlock = MemoryBlock.builder()
                            .deleted(true)
                            .root(false)
                            .links(new ArrayList<>())
                            .name(DELETED_NAME)
                            .fullClassName("")
                            .size(unusedSpace)
                            .address(block.getAddress() + block.getSize())
                            .build();

                    insert(newDeletedBlock);
                }
            }
        }

        blocks.put(block.getAddress(), block);
        namedBlocks.put(block.getName(), block);
        size += block.getSize();

        return block;
    }

    public void mergeDeletedBlocks() {
        List<List<MemoryBlock>> groupedDeletedBlocks = new ArrayList<>();
        List<MemoryBlock> currentList = new ArrayList<>();

        boolean first = true;
        long prevLastAddress = -1;

        for (MemoryBlock block : blocks.values()) {
            if (!block.isDeleted() || block.isRoot())
                continue;

            final var currentAddress = block.getAddress();

            if (!first && currentAddress - prevLastAddress != 0) {
                if (!currentList.isEmpty()) {
                    groupedDeletedBlocks.add(currentList);
                    currentList = new ArrayList<>();
                }

                continue;
            }

            first = false;
            currentList.add(block);

            prevLastAddress = currentAddress + block.getSize();
        }

        groupedDeletedBlocks.add(currentList);

        groupedDeletedBlocks.stream()
                .filter(deletedBlocks -> deletedBlocks.size() > 1)
                .forEach(deletedBlocks -> {
                    long startAddr = deletedBlocks.get(0).getAddress();

                    var lastBlock = deletedBlocks.get(deletedBlocks.size() - 1);
                    long size = lastBlock.getAddress() + lastBlock.getSize() - startAddr;

                    deletedBlocks.forEach(block -> blocks.remove(block.getAddress()));

                    blocks.put(startAddr, MemoryBlock.builder()
                                    .address(startAddr)
                                    .size(size)
                                    .links(new ArrayList<>())
                                    .deleted(true)
                                    .fullClassName("")
                                    .name(DELETED_NAME)
                            .build());
                });
    }

    @Override
    public void remove(MemoryBlock memoryBlock) {
        if (memoryBlock.isRoot())
            return;

        if (blocks.containsKey(memoryBlock.getAddress())) {
            var block = blocks.get(memoryBlock.getAddress());

            if (!block.isDeleted()) {
                block.setDeleted(true);
                namedBlocks.remove(memoryBlock.getName());
                blocks.values().forEach(parentBlock -> parentBlock.getLinks().remove(block));

                size -= block.getSize();
            }
        }
    }

    private List<MemoryBlock> findBlocksForMerging(long requiredSize) {

        int sumSize = 0;
        long prevLastAddress = -1;
        var selectedBlocks = new ArrayList<MemoryBlock>();

        boolean first = true;

        for (Map.Entry<Long, MemoryBlock> e : blocks.entrySet()) {
            final var currentAddress = e.getKey();
            final var currentBlock = e.getValue();

            if (!currentBlock.isDeleted() || currentBlock.isRoot())
                continue;

            if (!first && currentAddress - prevLastAddress != 0) {
                sumSize = 0;
                selectedBlocks.clear();
                continue;
            }

            first = false;
            selectedBlocks.add(currentBlock);

            sumSize += currentBlock.getSize();

            if (sumSize >= requiredSize)
                break;

            prevLastAddress = currentAddress + currentBlock.getSize();
        }

        var suitable = selectedBlocks.stream()
                .map(MemoryBlock::getSize)
                .mapToLong(a -> a)
                .sum() >= requiredSize;

        return suitable ? selectedBlocks : emptyList();
    }

    @Override
    public MemoryBlock getByName(String name) {
        if (!namedBlocks.containsKey(name))
            throw new ObjectNotFoundException("Could not find object: " + name);

        var block = namedBlocks.get(name);

        if (block.isRoot())
            throw new ObjectNotFoundException("Could not find object: " + name);

        return block;
    }

    @Override
    public void clear() {
        blocks.clear();
        namedBlocks.clear();
    }

    @Override
    public Stream<MemoryBlock> stream() {
        return blocks.values().stream().sorted(comparingLong(MemoryBlock::getAddress));
    }

    @Override
    public int totalSize() {
        return size - 1;
    }

    @Override
    public MemoryBlock getRootBlock() {
        var rootBlock = blocks.values().stream()
                .filter(MemoryBlock::isRoot)
                .findFirst()
                .orElse(null);

        if (rootBlock == null)
            return insert(ROOT_BLOCK);

        return rootBlock;
    }
}
