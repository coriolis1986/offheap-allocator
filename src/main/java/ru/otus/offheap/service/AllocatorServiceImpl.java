package ru.otus.offheap.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import ru.otus.offheap.exception.NotEnoughMemoryException;
import ru.otus.offheap.model.MemoryBlock;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Long.toHexString;
import static java.lang.String.format;
import static ru.otus.offheap.constants.AllocatorConstants.BUFFER_SIZE;

@Service
@RequiredArgsConstructor
public final class AllocatorServiceImpl implements AllocatorService {

    @Getter
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

            var rootBlock = blockStorage.getRootBlock();

            if (obj.getClass().getSimpleName().contains("Blob"))
                System.out.println();

            var memoryBlock = MemoryBlock.builder()
                    .address(basePointer + offset)
                    .size(length)
                    .name(name)
                    .links(new ArrayList<>())
                    .fullClassName(obj.getClass().getCanonicalName())
                    .build();

            rootBlock.getLinks().add(memoryBlock);

            memoryBlock = blockStorage.insert(memoryBlock);

            int cnt = 0;

            for (byte b : queue)
                UNSAFE.putByte(memoryBlock.getAddress() + cnt++, b);

            offset += cnt;

            return name;
        }
    }

    @SneakyThrows
    public synchronized List<Serializable> get(String name) {
        final var list = new ArrayList<Serializable>();

        readObjects(blockStorage.getByName(name), list);

        return list;
    }

    @SneakyThrows
    private void readObjects(MemoryBlock memoryBlock, final List<Serializable> list) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            for (int i = 0; i < memoryBlock.getSize(); i++)
                baos.write(UNSAFE.getByte(memoryBlock.getAddress() + i));

            baos.flush();

            try (ByteArrayInputStream is = new ByteArrayInputStream(baos.toByteArray());
                 ObjectInputStream ois = new ObjectInputStream(is)) {
                list.add((Serializable) ois.readObject());
            }

            memoryBlock.getLinks().forEach(linkedBlock -> readObjects(linkedBlock, list));
        }
    }

    public synchronized void remove(String name) {
        var memoryBlock = blockStorage.getByName(name);

        blockStorage.remove(memoryBlock);
    }

    @Override
    public synchronized void link(String parent, String child) {
        var parentBlock = blockStorage.getByName(parent);
        var childBlock = blockStorage.getByName(child);
        parentBlock.getLinks().add(childBlock);

        getBlockStorage().getRootBlock().getLinks().remove(childBlock);
    }

    @Override
    public void unlink(String parent, String child) {
        var parentBlock = blockStorage.getByName(parent);

        var count = new AtomicInteger();

        parentBlock.setLinks(parentBlock.getLinks().stream()
                .filter(block -> !block.getName().equals(child) && count.incrementAndGet() < 1)
                .toList()
        );

        blockStorage.remove(parentBlock);
        blockStorage.insert(parentBlock);
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
        return obj.getClass().getSimpleName() + "_" + toHexString(offset);
    }
}
