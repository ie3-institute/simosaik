package edu.ie3.simopsim.data;

import edu.ie3.datamodel.models.result.ResultEntity;
import edu.ie3.datamodel.models.result.system.SystemParticipantResult;

import java.time.ZonedDateTime;
import java.util.Map;

public record SimopsimResultWrapper(
        ZonedDateTime simulationTime,
        Map<String, ResultEntity> resultsFromSimona
) {
    public double getActivePower(String assetId) {
        if (resultsFromSimona().get(assetId) instanceof SystemParticipantResult systemParticipantResult) {
            return systemParticipantResult.getP().getValue().doubleValue();
        } else {
            throw new RuntimeException("No ActivePower!");
        }
    }

    public double getReactivePower(String assetId) {
        if (resultsFromSimona().get(assetId) instanceof SystemParticipantResult systemParticipantResult) {
            return systemParticipantResult.getQ().getValue().doubleValue();
        } else {
            throw new RuntimeException("No ReactivePower!");
        }
    }
}
