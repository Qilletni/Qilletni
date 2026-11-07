package dev.qilletni.impl.debug;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Every file's top level and function call across all files, innermost first. Only touched on the interpreter thread.
 */
final class FrameTracker {

    private final Deque<Frame> frames = new ArrayDeque<>();

    void push(Frame frame) {
        frames.push(frame);
    }

    void pop() {
        frames.pop();
    }

    /**
     * @return The innermost frame, or {@code null} if nothing is running
     */
    Frame top() {
        return frames.peek();
    }

    int depth() {
        return frames.size();
    }

    /**
     * @return The frames, innermost first
     */
    List<Frame> snapshot() {
        return List.copyOf(frames);
    }
}
