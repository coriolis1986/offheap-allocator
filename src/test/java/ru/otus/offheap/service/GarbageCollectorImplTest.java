package ru.otus.offheap.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import ru.otus.offheap.AllocatorConfiguration;

@SpringBootTest(classes = AllocatorConfiguration.class)
@EnableAutoConfiguration
class GarbageCollectorImplTest {

    @Test
    void test() {

    }
}