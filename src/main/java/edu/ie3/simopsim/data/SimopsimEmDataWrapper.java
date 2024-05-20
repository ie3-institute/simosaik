package edu.ie3.simopsim.data;

import de.fhg.iwes.opsim.datamodel.generated.realtimedata.OpSimScheduleMessage;

import java.util.HashMap;
import java.util.Map;

public record SimopsimEmDataWrapper(
        Map<String, OpSimScheduleMessage> ossm
) {
    public SimopsimEmDataWrapper() {
        this(new HashMap<>());
    }
}
