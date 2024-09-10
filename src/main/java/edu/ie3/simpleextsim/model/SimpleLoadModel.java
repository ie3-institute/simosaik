package edu.ie3.simpleextsim.model;

import edu.ie3.datamodel.models.timeseries.individual.IndividualTimeSeries;
import edu.ie3.datamodel.models.value.PValue;

import java.util.Map;
import java.util.UUID;

public class SimpleLoadModel {

    private final UUID uuid;
    private final String id;
    private final Map<Long, PValue> timeSeries;

    public SimpleLoadModel(
            UUID uuid,
            String id,
            Map<Long, PValue> timeSeries
    ) {
        this.uuid = uuid;
        this.id = id;
        this.timeSeries = timeSeries;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getId() {
        return id;
    }

    public PValue getPower(Long tick) {
        return timeSeries.get(tick);
    }


}
