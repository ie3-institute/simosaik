package edu.ie3.simosaik.data;

import java.util.HashMap;
import java.util.Map;

public record SimosaikPrimaryDataWrapper(
        Map<String, Map<String, Float>> dataMap
) {
    public SimosaikPrimaryDataWrapper() {
        this(new HashMap<>());
    }
}
