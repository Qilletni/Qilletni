package dev.qilletni.impl.lang.internal;

import dev.qilletni.api.lang.internal.BackgroundTaskExecutor;
import dev.qilletni.api.lang.stack.QilletniStackTrace;
import dev.qilletni.impl.lang.stack.QilletniStackTraceElementImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

public class BackgroundTaskExecutorImpl implements BackgroundTaskExecutor {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(BackgroundTaskExecutorImpl.class);

    private int previousConditionId = 0;

    /**
     * A check to avoid nested background task executions.
     */
    private boolean blockBackgroundTaskExecution = false;

    private final QilletniStackTrace qilletniStackTrace;
    private final Map<Integer, Condition<?>> backgroundTasks = new HashMap<>();
//    private final Map<Integer, ConditionInvocation> triggeredConditions = Collections.synchronizedMap(new HashMap<>());
    private final BlockingQueue<ConditionInvocation> triggeredConditions = new LinkedBlockingQueue<>();

    public BackgroundTaskExecutorImpl(QilletniStackTrace qilletniStackTrace) {
        this.qilletniStackTrace = qilletniStackTrace;
    }

    @Override
    public int runWhenCondition(Runnable callback) {
        backgroundTasks.put(++previousConditionId, new Condition<>(previousConditionId, callback));
        return previousConditionId;
    }

    @Override
    public <T> int runWhenCondition(Consumer<T> callback) {
        backgroundTasks.put(++previousConditionId, new Condition<>(previousConditionId, callback));
        return previousConditionId;
    }

    @Override
    public void triggerCondition(int conditionId) {
        try {
            triggeredConditions.put(new ConditionInvocation(conditionId));
        } catch (InterruptedException e) {
            LOGGER.error("Interrupted while adding condition to queue", e);
        }
    }

    @Override
    public void triggerCondition(int conditionId, Object param) {
        try {
            triggeredConditions.put(new ConditionInvocation(conditionId, param));
        } catch (InterruptedException e) {
            LOGGER.error("Interrupted while adding condition to queue", e);
        }
    }
    
    private void safelyCallCondition(ConditionInvocation conditionInvocation) {
        var conditionId = conditionInvocation.id();
        LOGGER.debug("Running background task for condition ID {}", conditionId);
        
        var task = backgroundTasks.get(conditionId);
        if (task != null) {
            if (conditionInvocation.hasParam() != task.consumesParam()) {
                // Skipping invocation, parameter mismatch
                LOGGER.debug("Skipping background task invocation for condition ID {} due to parameter mismatch", conditionId);
                return;
            }
            
            task.call(conditionInvocation);
        }
    }

    /**
     * Stops or allows triggered background tasks running at the next expression.
     *
     * @return If they were blocked before
     */
    public boolean blockBackgroundTasks(boolean block) {
        var previous = blockBackgroundTaskExecution;
        blockBackgroundTaskExecution = block;
        return previous;
    }

    @Override
    public void checkAndRunBackgroundTasks() {
        if (!blockBackgroundTaskExecution && !triggeredConditions.isEmpty()) {
            LOGGER.debug("Running {} background tasks", triggeredConditions.size());
            
            blockBackgroundTaskExecution = true;
            qilletniStackTrace.pushStackTraceElement(QilletniStackTraceElementImpl.createBackgroundTask());
            
            // Not really the best way to use a queue, this was adapted from a map. Change this later probably
            var conditions = List.copyOf(triggeredConditions);
            triggeredConditions.clear();
            
            conditions.forEach(this::safelyCallCondition);
            
            qilletniStackTrace.popStackTraceElement();
            blockBackgroundTaskExecution = false;
        }
    }

    @Override
    public void blockAndRunBackgroundTasks() {
        try {
            while (true) {
                var conditionInvocation = triggeredConditions.take();
                safelyCallCondition(conditionInvocation);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Interrupted while waiting for background tasks", e);
        }
    }

    private record ConditionInvocation(int id, Object param, boolean hasParam) {
        ConditionInvocation(int id) {
            this(id, null, false);
        }
        
        ConditionInvocation(int id, Object param) {
            this(id, param, true);
        }
    }
    
    private record Condition<T>(int id, Consumer<T> callback, boolean consumesParam) {
        Condition(int id, Runnable callback) {
            this(id, _ -> callback.run(), false);
        }
        
        Condition(int id, Consumer<T> callback) {
            this(id, callback, true);
        }
        
        void call(ConditionInvocation conditionInvocation) {
            if (consumesParam) {
                callback.accept((T) conditionInvocation.param());
            } else {
                callback.accept(null);
            }
        }
    }
}
