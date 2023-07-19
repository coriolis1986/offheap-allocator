package ru.otus.offheap.common;

import ru.otus.offheap.containers.containers.BigStringContainer;
import ru.otus.offheap.containers.containers.StringContainer;
import ru.otus.offheap.model.MemoryBlock;
import ru.otus.offheap.service.AllocatorService;
import ru.otus.offheap.service.AllocatorServiceImpl;
import ru.otus.offheap.service.MemoryBlockStorageImpl;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static ru.otus.offheap.containers.util.RandomStringGenerator.generate;

public class CommonTestMehods {

    private static final Random RANDOM = new Random();

    public static final int TEST_OBJECTS_QUANTITY = 50;
    public static final int PARENT_OBJECT_POS = 4;
    public static final int DELETION_FIRST_POS = 16;
    public static final int DELETION_LAST_POS = 29;
    public static final int DELETED_RANGE = 10;

    public static List<String> prepareTestObjects(AllocatorService allocatorService,
                                            List<String> namesForDeletion,
                                            int selectNum) {

        List<String> sixObjects = new ArrayList<>();

        for (int i = 1; i <= TEST_OBJECTS_QUANTITY; i++) {
            Serializable obj = i % 2 == 0 ? generateStringContainer() : generateBigStringContainer();
            String objectName = allocatorService.set(obj);

            if (i >= PARENT_OBJECT_POS && i <= PARENT_OBJECT_POS + selectNum - 1)
                sixObjects.add(objectName);

            if (i >= DELETION_FIRST_POS && i <= DELETION_LAST_POS)
                namesForDeletion.add(objectName);
        }

        return sixObjects;
    }

    public static StringContainer generateStringContainer() {
        return StringContainer.builder()
                .str1(generate(RANDOM.nextInt(10)))
                .str2(generate(RANDOM.nextInt(10)))
                .build();
    }

    public static BigStringContainer generateBigStringContainer() {
        return BigStringContainer.builder()
                .con1(generateStringContainer())
                .con2(generateStringContainer())
                .build();
    }

    public static Map<Long, MemoryBlock> getBlocks(AllocatorService allocatorService) {
        return ((MemoryBlockStorageImpl) (((AllocatorServiceImpl) allocatorService).getBlockStorage())).getBlocks();
    }

    public static MemoryBlock getRootBlock(AllocatorService allocatorService) {
        return getBlocks(allocatorService).get(0L);
    }

    public static MemoryBlock getBlockByNum(AllocatorService allocatorService, int num) {
        return getBlocks(allocatorService).values().stream()
                .skip(num)
                .findFirst()
                .orElse(null);
    }

    public static long countDeletedBlocks(AllocatorService allocatorService) {
        return getBlocks(allocatorService).values().stream()
                .filter(MemoryBlock::isDeleted)
                .count();
    }
}
