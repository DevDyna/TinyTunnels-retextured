package dev.thefern2.tinytunnels.teleport;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.MapCodec;

/** The player's way back out, one entry per room entered. Immutable; push and pop return a new stack. */
public record ReturnStack(List<ReturnPoint> points) {
    public static final ReturnStack EMPTY = new ReturnStack(List.of());
    public static final MapCodec<ReturnStack> MAP_CODEC = ReturnPoint.CODEC.listOf().fieldOf("points").xmap(ReturnStack::new, ReturnStack::points);

    public ReturnStack {
        points = List.copyOf(points);
    }

    public ReturnStack push(ReturnPoint point) {
        List<ReturnPoint> next = new ArrayList<>(points);
        next.add(point);
        return new ReturnStack(next);
    }

    public Optional<ReturnPoint> peek() {
        return points.isEmpty() ? Optional.empty() : Optional.of(points.getLast());
    }

    public ReturnStack pop() {
        return points.isEmpty() ? this : new ReturnStack(points.subList(0, points.size() - 1));
    }

    public int depth() {
        return points.size();
    }
}
