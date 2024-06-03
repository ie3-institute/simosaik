package edu.ie3.simosaik.data;

import edu.ie3.simona.api.data.ExtInputDataPackage;
import edu.ie3.simona.api.data.ExtInputDataValue;

import java.util.HashMap;
import java.util.Map;

public class SimosaikPrimaryDataWrapper implements ExtInputDataPackage {

    private final Map<String, ExtInputDataValue> dataMap;

    public SimosaikPrimaryDataWrapper(
            Map<String, SimosaikValue> dataMap
    ) {
        this.dataMap = new HashMap<>(dataMap);
    }

    public SimosaikPrimaryDataWrapper() {
        this(new HashMap<>());
    }

    public void addSimosaikValue(String assetId, SimosaikValue value) {
        dataMap.put(assetId, value);
    }

    @Override
    public Map<String, ExtInputDataValue> getSimonaInputMap() {
        return dataMap;
    }
}
