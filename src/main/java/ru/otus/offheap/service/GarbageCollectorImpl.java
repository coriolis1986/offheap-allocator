package ru.otus.offheap.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.otus.offheap.model.MemoryBlock;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class GarbageCollectorImpl implements GarbageCollector {

    private final MemoryBlockStorage memoryBlockStorage;

    @Override
    public void performGC() {
        memoryBlockStorage.mergeDeletedBlocks();

        var blocks = ((MemoryBlockStorageImpl) memoryBlockStorage).getBlocks();
        var aliveBlockAddressess = new HashSet<Long>();

        collectAliveLinks(memoryBlockStorage.getRootBlock(), aliveBlockAddressess, blocks);

        AtomicInteger collectedBlocks = new AtomicInteger();

        blocks.forEach((address, block) -> {
            if (block.isDeleted() || block.isRoot())
                return;

            if (!aliveBlockAddressess.contains(address)) {
                block.setDeleted(true);
                collectedBlocks.incrementAndGet();
            }
        });

        log.info("Collected {} blocks", collectedBlocks);
    }

    private void collectAliveLinks(MemoryBlock memoryBlock, Set<Long> blockAddresses, Map<Long, MemoryBlock> blocks) {
        if (!memoryBlock.isRoot())
            blockAddresses.add(memoryBlock.getAddress());

        memoryBlock.getLinks().forEach(link -> {
            var childBlock = blocks.get(link.getAddress());

            collectAliveLinks(childBlock, blockAddresses, blocks);
        });
    }
}
