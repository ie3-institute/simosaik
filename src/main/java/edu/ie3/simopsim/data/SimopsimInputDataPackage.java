package edu.ie3.simopsim.data;

import edu.ie3.simona.api.data.ExtInputDataPackage;
import edu.ie3.simona.api.data.ExtInputDataValue;

import java.util.HashMap;
import java.util.Map;

public class SimopsimInputDataPackage implements ExtInputDataPackage {

    private final Map<String, ExtInputDataValue> dataMap;

    public SimopsimInputDataPackage(
        Map<String, SimopsimValue> dataMap
    ) {
        this.dataMap = new HashMap<>(dataMap);
    }
    public SimopsimInputDataPackage() {
        this(new HashMap<>());
    }

    @Override
    public Map<String, ExtInputDataValue> getSimonaInputMap() {
        return dataMap;
    }
}
