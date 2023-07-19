package ru.otus.offheap.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import ru.otus.offheap.AllocatorConfiguration;
import ru.otus.offheap.containers.containers.BigStringContainer;
import ru.otus.offheap.containers.containers.BlobContainer;
import ru.otus.offheap.containers.containers.StringContainer;
import ru.otus.offheap.model.MemoryBlock;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static ru.otus.offheap.containers.util.RandomStringGenerator.generate;

@SpringBootTest(classes = AllocatorConfiguration.class)
@EnableAutoConfiguration
public class AllocatorServiceTest {

    private static final Random RANDOM = new Random();

    @Autowired
    private AllocatorService allocatorService;

    @Autowired
    private GarbageCollector garbageCollector;

    private static final int TEST_OBJECTS_QUANTITY = 50;
    private static final int PARENT_OBJECT_POS = 4;
    private static final int DELETION_FIRST_POS = 16;
    private static final int DELETION_LAST_POS = 29;
    private static final int DELETED_RANGE = 10;

    @Test
    void insertDeleteReuseTest() {
        // Подготовили 50 объектов, взяли из них 6
        final List<String> namesForDeletion = new ArrayList<>();
        final List<String> selectedObjects = prepareTestObjects(namesForDeletion, 6);
        // У корневого объекта появилось 50 ссылок
        assertEquals(TEST_OBJECTS_QUANTITY, getRootBlock().getLinks().size());

        // Из выбранных 6:
        //  - первый будет новым родителем для остальных 5
        //  - соответственно они должны будут отвязаться от корневого объекта
        final var newParent = selectedObjects.iterator().next();

        for (int i = 1; i < selectedObjects.size(); i++)
            allocatorService.link(newParent, selectedObjects.get(i));

        int rootLinks = TEST_OBJECTS_QUANTITY - selectedObjects.size() + 1;
        // У корневого объекта осталось 45 ссылок
        assertEquals(rootLinks, getRootBlock().getLinks().size());
        // У нового родителя появилось 5 ссылок
        assertEquals(selectedObjects.size() - 1, getBlockByNum(PARENT_OBJECT_POS).getLinks().size());

        // Удалем из 50 созданных 14 объектов где-то из середины
        namesForDeletion.forEach(name -> allocatorService.remove(name));
        var deletedBlocksCount = DELETION_LAST_POS - DELETION_FIRST_POS + 1;
        rootLinks -= deletedBlocksCount;
        // Ссылок у корневого объекта должно остаться 31
        assertEquals(rootLinks, getRootBlock().getLinks().size());
        // Должно появиться 14 удаленных блоков
        assertEquals(deletedBlocksCount, countDeletedBlocks());

        // Добавили новый объект. Он получился на моей машине в сериализованном представлении размером в 1106 байт
        allocatorService.set(BlobContainer.builder().arr(new int[250]).build());
        rootLinks++;
        assertEquals(rootLinks, getRootBlock().getLinks().size());
        // Новый объект под себя переиспользует и объединит 6 блоков, должно остаться 8
        assertEquals(deletedBlocksCount - 6, countDeletedBlocks());

//        System.out.println(allocatorService);
    }

    @Test
    void garbageCollectionTest() {
        // Подготовили 50 объектов, взяли из них 21
        final List<String> namesForDeletion = new ArrayList<>();
        final List<String> selectedObjects = prepareTestObjects(namesForDeletion, 21);
        // У корневого объекта появилось 50 ссылок
        assertEquals(TEST_OBJECTS_QUANTITY, getRootBlock().getLinks().size());

        // Из выбранных 21:
        //  - первый будет новым родителем цепочки из остальных
        //  - соответственно они должны будут отвязаться от корневого объекта
        var newParent = selectedObjects.iterator().next();
        final var aa = newParent;

        for (int i = 1; i < selectedObjects.size(); i++) {
            var child = selectedObjects.get(i);
            allocatorService.link(newParent, child);
            newParent = child;
        }

        int rootLinks = TEST_OBJECTS_QUANTITY - selectedObjects.size() + 1;
        // У корневого объекта осталось 30 ссылок
        assertEquals(rootLinks, getRootBlock().getLinks().size());

        // Из 30 удаляем 1 объект после десятого, должны появиться 11 удаленных блоков.
        // И 10 связанных объектов, не связанных с корневым объектов
        selectedObjects.stream().skip(DELETED_RANGE).limit(1).forEach(name -> allocatorService.remove(name));

        // Произвели сборку мусора
        garbageCollector.performGC();
        // Должно появиться 11 удаленных блоков
        assertEquals(DELETED_RANGE + 1, countDeletedBlocks());

        // Произвели еще одну сборку мусора, новых удаленных блоков появиться не должно,
        // 11 объединились в 1
        garbageCollector.performGC();
        assertEquals(1, countDeletedBlocks());

        var deletedBlocks = getBlocks().values().stream()
                .filter(MemoryBlock::isDeleted)
                .toList();

        // Добавим еще один объект
        var newBlockName = allocatorService.set(BlobContainer.builder().arr(new int[250]).build());

        // Новый объект "влезает" по размеру в старый блок и переиспользует его
        assertEquals(deletedBlocks.iterator().next().getAddress(),
                getBlocks().values().stream()
                        .filter(block -> block.getName().equals(newBlockName))
                        .map(MemoryBlock::getAddress)
                        .findFirst()
                        .orElse(null)
        );

        // Поскольку размер нового объекта меньше суммы размеров переиспользуемых, после него
        // должен появиться новый удаленный объект
        assertEquals(1, countDeletedBlocks());
        var blocksCount = getBlocks().size();

        // Добавим еще один большой объект
        allocatorService.set(BlobContainer.builder().arr(new int[5000]).build());

        // Поскольку размер нового объекта больше суммы размеров переиспользуемых, удаленный объект
        // останется на месте
        assertEquals(1, countDeletedBlocks());

        // Должно получиться на один больше блоков, чем до добавления большого
        assertEquals(blocksCount + 1, getBlocks().size());

        System.out.println(allocatorService);
    }

    private List<String> prepareTestObjects(List<String> namesForDeletion, int selectNum) {

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

    private StringContainer generateStringContainer() {
        return StringContainer.builder()
                .str1(generate(RANDOM.nextInt(10)))
                .str2(generate(RANDOM.nextInt(10)))
                .build();
    }

    private BigStringContainer generateBigStringContainer() {
        return BigStringContainer.builder()
                .con1(generateStringContainer())
                .con2(generateStringContainer())
                .build();
    }

    private Map<Long, MemoryBlock> getBlocks() {
        return ((MemoryBlockStorageImpl) (((AllocatorServiceImpl) allocatorService).getBlockStorage())).getBlocks();
    }

    private MemoryBlock getRootBlock() {
        return getBlocks().get(0L);
    }

    private MemoryBlock getBlockByNum(int num) {
        return getBlocks().values().stream()
                .skip(num)
                .findFirst()
                .orElse(null);
    }

    private long countDeletedBlocks() {
        return getBlocks().values().stream()
                .filter(MemoryBlock::isDeleted)
                .count();
    }
}