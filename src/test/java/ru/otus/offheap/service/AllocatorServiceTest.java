package ru.otus.offheap.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import ru.otus.offheap.AllocatorConfiguration;
import ru.otus.offheap.containers.containers.BigStringContainer;
import ru.otus.offheap.containers.containers.BlobContainer;
import ru.otus.offheap.containers.containers.StringContainer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static ru.otus.offheap.containers.util.RandomStringGenerator.generate;

@SpringBootTest(classes = AllocatorConfiguration.class)
@EnableAutoConfiguration
public class AllocatorServiceTest {

    private static final Random RANDOM = new Random();

    @Autowired
    private AllocatorService allocatorService;

    @Test
    void test() {
        List<String> namesForDeletion = new ArrayList<>();

        for (int i = 0; i < 50; i++) {
            Serializable obj = i % 2 == 0 ? generateStringContainer() : generateBigStringContainer();
            String objectName = allocatorService.set(obj);

            if (i > 15 && i < 30)
                namesForDeletion.add(objectName);
        }

        namesForDeletion.forEach(name -> allocatorService.remove(name));

        allocatorService.set(BlobContainer.builder().arr(new int[250]).build());

        System.out.println(allocatorService);
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
}