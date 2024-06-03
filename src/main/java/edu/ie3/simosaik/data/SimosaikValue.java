package edu.ie3.simosaik.data;

import edu.ie3.simona.api.data.ExtInputDataValue;

import java.util.Map;

public class SimosaikValue implements ExtInputDataValue {
    private final Map<String, Float> mosaikMap;

    public SimosaikValue(
            Map<String, Float> mosaikMap
    ) {
        this.mosaikMap = mosaikMap;
    }

    public Map<String, Float> getMosaikMap() {
        return mosaikMap;
    }
}
