package edu.ie3.simosaik.data;

import edu.ie3.datamodel.models.result.NodeResult;
import edu.ie3.datamodel.models.result.ResultEntity;
import edu.ie3.datamodel.models.result.system.SystemParticipantResult;
import tech.units.indriya.ComparableQuantity;
import tech.units.indriya.quantity.Quantities;

import javax.measure.quantity.Dimensionless;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

import static edu.ie3.util.quantities.PowerSystemUnits.PU;

public record SimosaikResultWrapper(
        Long tick,
        ZonedDateTime simulationTime,
        Map<String, ResultEntity> resultsFromSimona
) {
    public SimosaikResultWrapper() {
        this(
                -1L,
                null,
                new HashMap<>()
        );
    }
    public double getVoltageDeviation(String assetId) {
        if (resultsFromSimona().get(assetId) instanceof NodeResult nodeResult) {
            ComparableQuantity<Dimensionless> vMagDev = Quantities.getQuantity(0, PU);
            vMagDev = Quantities.getQuantity(0, PU)
                    .add(nodeResult.getvMag()
                            .subtract(Quantities.getQuantity(1.0, PU)));
            return vMagDev.getValue().doubleValue();
        } else {
            throw new RuntimeException("No NodeResult!");
        }
    }

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
