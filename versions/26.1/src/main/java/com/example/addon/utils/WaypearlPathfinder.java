package com.example.addon.utils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.Function;
import java.util.function.Predicate;

public final class WaypearlPathfinder {
    private WaypearlPathfinder() {}

    public static <T> List<T> find(T start, Predicate<T> goal, Function<T, Iterable<T>> neighbours, int limit) {
        Queue<T> queue = new ArrayDeque<>();
        Map<T, T> parents = new HashMap<>();
        queue.add(start);
        parents.put(start, null);
        int visited = 0;
        while (!queue.isEmpty() && visited++ < limit) {
            T current = queue.remove();
            if (goal.test(current)) {
                List<T> path = new ArrayList<>();
                for (T node = current; node != null; node = parents.get(node)) path.add(node);
                Collections.reverse(path);
                return path;
            }
            for (T next : neighbours.apply(current)) {
                if (parents.containsKey(next)) continue;
                parents.put(next, current);
                queue.add(next);
            }
        }
        return null;
    }
}
