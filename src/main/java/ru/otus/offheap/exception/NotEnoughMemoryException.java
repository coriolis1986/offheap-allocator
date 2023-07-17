package ru.otus.offheap.exception;

public class NotEnoughMemoryException extends RuntimeException {

    public NotEnoughMemoryException(String message) {
        super(message);
    }
}
