package ru.otus.offheap.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.otus.offheap.exception.ObjectNotFoundException;
import ru.otus.offheap.model.MemoryBlock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static java.util.Comparator.comparingLong;

@Service
@Slf4j
public class MemoryBlockStorageImpl implements MemoryBlockStorage {

    private final TreeMap<Long, MemoryBlock> blocks = new TreeMap<>();
    private final Map<String, MemoryBlock> namedBlocks = new HashMap<>();
    private int size = 0;

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

                suitableBlocks.stream().map(MemoryBlock::getAddress).forEach(blocks::remove);

                block = block.clone(suitableBlocks.iterator().next().getAddress());
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
            if (!block.isDeleted())
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
                    long size = lastBlock.getAddress() + lastBlock.getSize();

                    deletedBlocks.forEach(block -> blocks.remove(block.getAddress()));

                    blocks.put(startAddr, MemoryBlock.builder()
                                    .address(startAddr)
                                    .size(size)
                                    .deleted(true)
                                    .fullClassName("")
                                    .name("deleted")
                            .build());
                });
    }

    @Override
    public void remove(MemoryBlock memoryBlock) {
        if (blocks.containsKey(memoryBlock.getAddress())) {
            var block = blocks.get(memoryBlock.getAddress());

            if (!block.isDeleted()) {
                block.setDeleted(true);
                namedBlocks.remove(memoryBlock.getName());

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

            if (!currentBlock.isDeleted())
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

        return selectedBlocks;
    }

    @Override
    public MemoryBlock getByName(String name) {
        if (!namedBlocks.containsKey(name))
            throw new ObjectNotFoundException("Could not find object: " + name);

        return namedBlocks.get(name);
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
        return size;
    }
}
