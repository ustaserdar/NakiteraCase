package com.nakitera.brokerage.service;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class ConcurrentOperationExecutor {

    private static final int MAX_ATTEMPTS = 3;

    public <T> T execute(Supplier<T> action) {
        OptimisticLockingFailureException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return action.get();
            } catch (OptimisticLockingFailureException ex) {
                last = ex;
            }
        }
        throw last;
    }
}
