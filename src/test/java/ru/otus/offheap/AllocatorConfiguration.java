package ru.otus.offheap;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import ru.otus.offheap.service.AllocatorService;
import ru.otus.offheap.service.AllocatorServiceImpl;
import ru.otus.offheap.service.GarbageCollector;
import ru.otus.offheap.service.GarbageCollectorImpl;
import ru.otus.offheap.service.MemoryBlockStorage;
import ru.otus.offheap.service.MemoryBlockStorageImpl;

@SpringJUnitConfig
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class AllocatorConfiguration {

    @Bean
    @Primary
    public MemoryBlockStorage memoryBlockStorage() {
        return new MemoryBlockStorageImpl();
    }

    @Bean
    public AllocatorService allocatorService(MemoryBlockStorage memoryBlockStorage) {
        return new AllocatorServiceImpl(memoryBlockStorage);
    }

    @Bean
    public GarbageCollector gaGrabageCollector(MemoryBlockStorage memoryBlockStorage) {
        return new GarbageCollectorImpl(memoryBlockStorage);
    }
}
