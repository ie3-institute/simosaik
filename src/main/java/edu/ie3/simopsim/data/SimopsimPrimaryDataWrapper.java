package edu.ie3.simopsim.data;

import de.fhg.iwes.opsim.datamodel.generated.realtimedata.OpSimScheduleMessage;
import edu.ie3.datamodel.models.value.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record SimopsimPrimaryDataWrapper(
        Map<String, OpSimScheduleMessage> ossm
) {
    public SimopsimPrimaryDataWrapper() {
        this(new HashMap<>());
    }
}
