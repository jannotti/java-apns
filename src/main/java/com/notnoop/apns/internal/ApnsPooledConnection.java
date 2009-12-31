package com.notnoop.apns.internal;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import com.notnoop.apns.ApnsNotification;

public class ApnsPooledConnection implements IApnsConnection {
    private static final Logger logger = LoggerFactory.getLogger(ApnsPooledConnection.class);

    private final IApnsConnection prototype;
    private final int max;

    private final ExecutorService executors;
    private final ConcurrentLinkedQueue<IApnsConnection> prototypes;

    public ApnsPooledConnection(IApnsConnection prototype, int max) {
        this.prototype = prototype;
        this.max = max;

        executors = Executors.newFixedThreadPool(max);
        this.prototypes = new ConcurrentLinkedQueue<IApnsConnection>();
    }

    private final ThreadLocal<IApnsConnection> uniquePrototype =
        new ThreadLocal<IApnsConnection>() {
        protected IApnsConnection initialValue() {
            IApnsConnection newCopy = prototype.copy();
            prototypes.add(newCopy);
            return newCopy;
        }
    };

    public void sendMessage(final ApnsNotification m) {
        executors.execute(new Runnable() {
            public void run() {
                uniquePrototype.get().sendMessage(m);
            }
        });
    }

    public IApnsConnection copy() {
        return new ApnsPooledConnection(prototype, max);
    }

    public void close() {
        executors.shutdown();
        try {
            executors.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.warn("pool termination interrupted", e);
        }
        for (IApnsConnection conn : prototypes) {
            Utilities.close(conn);
        }
        Utilities.close(prototype);
    }
}