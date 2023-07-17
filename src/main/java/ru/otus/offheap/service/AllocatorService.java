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
import java.io.ObjectOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Queue;

import static java.lang.Long.toHexString;
import static java.lang.String.format;
import static org.apache.commons.lang3.SerializationUtils.deserialize;
import static ru.otus.offheap.constants.AllocatorConstants.BUFFER_SIZE;

@Service
@RequiredArgsConstructor
public final class AllocatorService {

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

            final String name = prepareName(obj, length);

            if (length > memoryLeft())
                throw new NotEnoughMemoryException("Needed " + length + ", but has " + memoryLeft());

            var memoryBlock = blockStorage.insert(MemoryBlock.builder()
                    .address(basePointer + offset)
                    .size(length)
                    .name(name)
                    .fullClassName(obj.getClass().getCanonicalName())
                    .build());

            for (byte b : queue)
                UNSAFE.putByte(memoryBlock.getAddress() + offset++, b);

            return name;
        }
    }

    @SneakyThrows
    private long prepareBytesQueue(ByteArrayOutputStream baos, Queue<Byte> queue) {
        long length = 0;

        try (ByteArrayInputStream is = new ByteArrayInputStream(baos.toByteArray())) {

            while (true) {
                final int value = is.read();

                if (value == -1)
                    break;

                queue.add((byte) value);
                length++;
            }
        }

        return length;
    }

    @SneakyThrows
    public synchronized <T> T get(String name, Class<T> cl) {
        var memoryBlock = blockStorage.getByName(name);

        try (var os = new PipedOutputStream()) {
            for (int i = 0; i < memoryBlock.getSize(); i++)
                os.write(UNSAFE.getByte(memoryBlock.getAddress() + i));

            try (var is = new PipedInputStream(os)) {
                return deserialize(is);
            }
        }
    }

    public synchronized void remove(String name) {
        var memoryBlock = blockStorage.getByName(name);

        blockStorage.remove(memoryBlock);
    }

    public long memoryLeft() {
        return BUFFER_SIZE - blockStorage.totalSize();
    }

    public void freeMemory() {
        UNSAFE.freeMemory(basePointer);
    }

    @PreDestroy
    private void desctruct() {
        blockStorage.clear();
        freeMemory();
    }

    @Override
    public String toString() {
        final StringBuffer res = new StringBuffer();

        res.append(format("Total: [%d] bytes\n", BUFFER_SIZE));
        res.append(format("Free:  [%d] bytes\n", memoryLeft()));

        blockStorage.stream().forEach(block ->
                res.append(String.format("%s : size [%d], address [0x%s], class [%s]\n",
                    block.isDeleted() ? "_deleted_" : block.getName(),
                    block.getSize(),
                    toHexString(block.getAddress()),
                    block.getFullClassName())
                )
        );

        return res.toString();
    }

    private String prepareName(Serializable obj, long length) {
        return obj.getClass().getSimpleName() + "_" + (offset + length);
    }
}
