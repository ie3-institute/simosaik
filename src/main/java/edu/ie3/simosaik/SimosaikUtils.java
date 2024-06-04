package edu.ie3.simosaik;

import edu.ie3.simona.api.data.results.ExtResultPackage;
import edu.ie3.simosaik.data.SimosaikInputDataPackage;
import edu.ie3.simosaik.data.SimosaikValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static edu.ie3.simosaik.SimosaikTranslation.*;

public class SimosaikUtils {
    private SimosaikUtils() {}


    public static SimosaikInputDataPackage createSimosaikPrimaryDataWrapper(
            Map<String, Object> mosaikInput
    ) {
        SimosaikInputDataPackage simosaikPrimaryDataWrapper = new SimosaikInputDataPackage();
        mosaikInput.forEach(
                (assetId, inputValue) -> {
                    simosaikPrimaryDataWrapper.addSimosaikValue(
                            assetId,
                            getSimosaikValue(inputValue)
                    );
                }
        );
        return simosaikPrimaryDataWrapper;
    }

    private static SimosaikValue getSimosaikValue(Object inputValue) {
        Map<String, Float> convertedInputValueMap = new HashMap<>();
        Map<String, Object> attrs = (Map<String, Object>) inputValue;
        for (Map.Entry<String, Object> attr : attrs.entrySet()) {
            Object[] values = ((Map<String, Object>) attr.getValue()).values().toArray();
            float value = 0;
            for (int i = 0; i < values.length; i++) {
                value += ((Number) values[i]).floatValue();
            }
            convertedInputValueMap.put(
                    attr.getKey(),
                    value
            );
        }
        return new SimosaikValue(convertedInputValueMap);
    }

    public static Map<String, Object> createSimosaikOutputMap(
            Map<String, List<String>> mosaikRequestedAttributes,
            ExtResultPackage simonaResults
    ) {
        Map<String, Object> outputMap = new HashMap<>();
        mosaikRequestedAttributes.forEach(
                (id, attrs) -> {
                    HashMap<String, Object> values = new HashMap<>();
                    for (String attr : attrs) {
                        addResult(
                                simonaResults,
                                id,
                                attr,
                                values
                        );
                    }
                    outputMap.put(id, values);
                }
        );
        return outputMap;
    }

    private static void addResult(ExtResultPackage results, String id, String attr, Map<String, Object> outputMap) {
        if (attr.equals(MOSAIK_VOLTAGE_DEVIATION)) {
            if (results.getTick() == 0L) {
                outputMap.put(attr, 0d);
            } else {
                outputMap.put(attr, results.getVoltageDeviation(id));
            }
        }
        if (attr.equals(MOSAIK_ACTIVE_POWER)) {
            outputMap.put(attr, results.getActivePower(id));
        }
        if (attr.equals(MOSAIK_REACTIVE_POWER)) {
            outputMap.put(attr, results.getReactivePower(id));
        }
    }
}
