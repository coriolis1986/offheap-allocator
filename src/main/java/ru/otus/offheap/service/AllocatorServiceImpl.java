package ru.otus.offheap.service;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import ru.otus.offheap.exception.NotEnoughMemoryException;
import ru.otus.offheap.model.MemoryBlock;
import ru.otus.offheap.storage.MemoryBlockStorage;
import sun.misc.Unsafe;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Queue;

import static java.lang.String.format;
import static ru.otus.offheap.constants.AllocatorConstants.BUFFER_SIZE;

@SuppressWarnings("unchecked")
@Service
@RequiredArgsConstructor
public final class AllocatorServiceImpl implements AllocatorService {

    private final MemoryBlockStorage blockStorage;

    private long basePointer;
    private volatile long offset;
    private volatile boolean initialized;

    private static final Unsafe UNSAFE;

    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe) f.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @PostConstruct
    private void init() {
        this.basePointer = UNSAFE.allocateMemory(BUFFER_SIZE);
        this.initialized = true;
    }

    @SneakyThrows
    public synchronized String set(final Serializable obj) {
        if (!initialized)
            throw new RuntimeException("Off heap buffer is not initialized");

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {

            oos.writeObject(obj);
            oos.flush();

            final Queue<Byte> queue = new ArrayDeque<>();
            long length = prepareBytesQueue(baos, queue);

            final String name = prepareName(obj);

            if (length > free())
                throw new NotEnoughMemoryException("Needed " + length + ", but has " + free());

            var memoryBlock = MemoryBlock.builder()
                    .address(basePointer + offset)
                    .size(length)
                    .name(name)
                    .fullClassName(obj.getClass().getCanonicalName())
                    .build();

            memoryBlock = blockStorage.insert(memoryBlock);

            int cnt = 0;

            for (byte b : queue)
                UNSAFE.putByte(memoryBlock.getAddress() + cnt++, b);

            offset += cnt;

            blockStorage.mergeDeletedBlocks();

            return name;
        }
    }

    @SneakyThrows
    public synchronized <T> T get(String name, Class<T> cl) {
        var memoryBlock = blockStorage.getByName(name);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            for (int i = 0; i < memoryBlock.getSize(); i++)
                baos.write(UNSAFE.getByte(memoryBlock.getAddress() + i));

            baos.flush();

            try (ByteArrayInputStream is = new ByteArrayInputStream(baos.toByteArray());
                ObjectInputStream ois = new ObjectInputStream(is)) {
                return (T) ois.readObject();
            }
        }
    }

    public synchronized void remove(String name) {
        var memoryBlock = blockStorage.getByName(name);

        blockStorage.remove(memoryBlock);
    }

    public long free() {
        return BUFFER_SIZE - blockStorage.totalSize();
    }

    @PreDestroy
    private void desctruct() {
        blockStorage.clear();
        UNSAFE.freeMemory(basePointer);
    }

    @Override
    public String toString() {
        final var res = new StringBuilder();

        res.append(format("Total: [%d] bytes\n", BUFFER_SIZE));
        res.append(format("Free:  [%d] bytes\n", free()));

        blockStorage.stream().forEach(res::append);

        return res.toString();
    }

    @SneakyThrows
    private long prepareBytesQueue(ByteArrayOutputStream baos, Queue<Byte> queue) {
        long length = 0;

        try (ByteArrayInputStream is = new ByteArrayInputStream(baos.toByteArray())) {

            while (true) {
                var bytes = is.readNBytes(2048);

                if (bytes.length == 0)
                    break;

                length += bytes.length;

                for (byte b : bytes)
                    queue.add(b);
            }
        }

        return length;
    }

    private String prepareName(Serializable obj) {
        return obj.getClass().getSimpleName() + "_" + Long.toHexString(obj.hashCode());
    }
}
