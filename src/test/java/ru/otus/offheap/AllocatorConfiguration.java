package ru.otus.offheap;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import ru.otus.offheap.service.AllocatorService;
import ru.otus.offheap.service.AllocatorServiceImpl;
import ru.otus.offheap.storage.MemoryBlockStorage;
import ru.otus.offheap.storage.MemoryBlockStorageImpl;

@SpringJUnitConfig
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
}
