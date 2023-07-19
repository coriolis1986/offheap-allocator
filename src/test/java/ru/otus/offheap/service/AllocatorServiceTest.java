package ru.otus.offheap.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import ru.otus.offheap.AllocatorConfiguration;
import ru.otus.offheap.containers.containers.BlobContainer;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static ru.otus.offheap.common.CommonTestMehods.DELETION_FIRST_POS;
import static ru.otus.offheap.common.CommonTestMehods.DELETION_LAST_POS;
import static ru.otus.offheap.common.CommonTestMehods.PARENT_OBJECT_POS;
import static ru.otus.offheap.common.CommonTestMehods.TEST_OBJECTS_QUANTITY;
import static ru.otus.offheap.common.CommonTestMehods.countDeletedBlocks;
import static ru.otus.offheap.common.CommonTestMehods.getBlockByNum;
import static ru.otus.offheap.common.CommonTestMehods.getRootBlock;
import static ru.otus.offheap.common.CommonTestMehods.prepareTestObjects;

@SpringBootTest(classes = AllocatorConfiguration.class)
@EnableAutoConfiguration
public class AllocatorServiceTest {

    @Autowired
    private AllocatorService allocatorService;

    @Test
    void insertDeleteReuseTest() {
        // Подготовили 50 объектов, взяли из них 6
        final List<String> namesForDeletion = new ArrayList<>();
        final List<String> selectedObjects = prepareTestObjects(allocatorService, namesForDeletion, 6);
        // У корневого объекта появилось 50 ссылок
        assertEquals(TEST_OBJECTS_QUANTITY, getRootBlock(allocatorService).getLinks().size());

        // Из выбранных 6:
        //  - первый будет новым родителем для остальных 5
        //  - соответственно они должны будут отвязаться от корневого объекта
        final var newParent = selectedObjects.iterator().next();

        for (int i = 1; i < selectedObjects.size(); i++)
            allocatorService.link(newParent, selectedObjects.get(i));

        int rootLinks = TEST_OBJECTS_QUANTITY - selectedObjects.size() + 1;
        // У корневого объекта осталось 45 ссылок
        assertEquals(rootLinks, getRootBlock(allocatorService).getLinks().size());
        // У нового родителя появилось 5 ссылок
        assertEquals(selectedObjects.size() - 1,
                getBlockByNum(allocatorService, PARENT_OBJECT_POS).getLinks().size());

        // Удалем из 50 созданных 14 объектов где-то из середины
        namesForDeletion.forEach(name -> allocatorService.remove(name));
        var deletedBlocksCount = DELETION_LAST_POS - DELETION_FIRST_POS + 1;
        rootLinks -= deletedBlocksCount;
        // Ссылок у корневого объекта должно остаться 31
        assertEquals(rootLinks, getRootBlock(allocatorService).getLinks().size());
        // Должно появиться 14 удаленных блоков
        assertEquals(deletedBlocksCount, countDeletedBlocks(allocatorService));

        // Добавили новый объект. Он получился на моей машине в сериализованном представлении размером в 1106 байт
        allocatorService.set(BlobContainer.builder().arr(new int[250]).build());
        rootLinks++;
        assertEquals(rootLinks, getRootBlock(allocatorService).getLinks().size());
        // Новый объект под себя переиспользует и объединит 6 блоков, должно остаться 5
        assertEquals(deletedBlocksCount - 9, countDeletedBlocks(allocatorService));

        System.out.println(allocatorService);
    }


}