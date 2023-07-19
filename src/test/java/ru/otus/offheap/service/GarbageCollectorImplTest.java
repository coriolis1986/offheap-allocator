package ru.otus.offheap.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import ru.otus.offheap.AllocatorConfiguration;
import ru.otus.offheap.containers.containers.BlobContainer;
import ru.otus.offheap.model.MemoryBlock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static ru.otus.offheap.common.CommonTestMehods.DELETED_RANGE;
import static ru.otus.offheap.common.CommonTestMehods.TEST_OBJECTS_QUANTITY;
import static ru.otus.offheap.common.CommonTestMehods.countDeletedBlocks;
import static ru.otus.offheap.common.CommonTestMehods.getBlocks;
import static ru.otus.offheap.common.CommonTestMehods.getRootBlock;
import static ru.otus.offheap.common.CommonTestMehods.prepareTestObjects;

@SpringBootTest(classes = AllocatorConfiguration.class)
@EnableAutoConfiguration
class GarbageCollectorImplTest {

    @Autowired
    private AllocatorService allocatorService;

    @Autowired
    private GarbageCollector garbageCollector;

    @Test
    void garbageCollectionTest() {
        // Подготовили 50 объектов, взяли из них 21
        final List<String> namesForDeletion = new ArrayList<>();
        final List<String> selectedObjects = prepareTestObjects(allocatorService, namesForDeletion, 21);
        // У корневого объекта появилось 50 ссылок
        assertEquals(TEST_OBJECTS_QUANTITY, getRootBlock(allocatorService).getLinks().size());

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
        assertEquals(rootLinks, getRootBlock(allocatorService).getLinks().size());

        // Из 30 удаляем 1 объект после десятого, должны появиться 11 удаленных блоков.
        // И 10 связанных объектов, не связанных с корневым объектов
        selectedObjects.stream().skip(DELETED_RANGE).limit(1).forEach(name -> allocatorService.remove(name));

        // Произвели сборку мусора
        garbageCollector.performGC();
        // Должно появиться 11 удаленных блоков
        assertEquals(DELETED_RANGE + 1, countDeletedBlocks(allocatorService));

        // Произвели еще одну сборку мусора, новых удаленных блоков появиться не должно,
        // 11 объединились в 1
        garbageCollector.performGC();
        assertEquals(1, countDeletedBlocks(allocatorService));

        var deletedBlocks = getBlocks(allocatorService).values().stream()
                .filter(MemoryBlock::isDeleted)
                .toList();

        // Добавим еще один объект
        var newBlockName = allocatorService.set(BlobContainer.builder().arr(new int[250]).build());

        // Новый объект "влезает" по размеру в старый блок и переиспользует его
        assertEquals(deletedBlocks.iterator().next().getAddress(),
                getBlocks(allocatorService).values().stream()
                        .filter(block -> block.getName().equals(newBlockName))
                        .map(MemoryBlock::getAddress)
                        .findFirst()
                        .orElse(null)
        );

        // Поскольку размер нового объекта меньше суммы размеров переиспользуемых, после него
        // должен появиться новый удаленный объект
        assertEquals(1, countDeletedBlocks(allocatorService));
        var blocksCount = getBlocks(allocatorService).size();

        // Добавим еще один большой объект
        allocatorService.set(BlobContainer.builder().arr(new int[5000]).build());

        // Поскольку размер нового объекта больше суммы размеров переиспользуемых, удаленный объект
        // останется на месте
        assertEquals(1, countDeletedBlocks(allocatorService));

        // Должно получиться на один больше блоков, чем до добавления большого
        assertEquals(blocksCount + 1, getBlocks(allocatorService).size());

        System.out.println(allocatorService);
    }
}