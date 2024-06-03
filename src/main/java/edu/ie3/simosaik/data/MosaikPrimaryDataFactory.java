package edu.ie3.simosaik.data;

import edu.ie3.datamodel.models.StandardUnits;
import edu.ie3.datamodel.models.value.PValue;
import edu.ie3.datamodel.models.value.SValue;
import edu.ie3.datamodel.models.value.Value;
import edu.ie3.simona.api.data.primarydata.PrimaryDataFactory;
import edu.ie3.simona.api.exceptions.ConvertionException;
import tech.units.indriya.quantity.Quantities;

import static edu.ie3.simosaik.SimosaikTranslation.MOSAIK_ACTIVE_POWER;
import static edu.ie3.simosaik.SimosaikTranslation.MOSAIK_REACTIVE_POWER;

public class MosaikPrimaryDataFactory implements PrimaryDataFactory {

    @Override
    public Value convert(Object entity) throws ConvertionException {
        if (entity instanceof SimosaikValue valueMap) {
            if (valueMap.getMosaikMap().containsKey(MOSAIK_ACTIVE_POWER) && valueMap.getMosaikMap().containsKey(MOSAIK_REACTIVE_POWER)) {
                return new SValue(
                        Quantities.getQuantity(valueMap.getMosaikMap().get(MOSAIK_ACTIVE_POWER) * 1000, StandardUnits.ACTIVE_POWER_IN),
                        Quantities.getQuantity(valueMap.getMosaikMap().get(MOSAIK_REACTIVE_POWER) * 1000, StandardUnits.ACTIVE_POWER_IN)
                );
            } else if (valueMap.getMosaikMap().containsKey(MOSAIK_ACTIVE_POWER) && !valueMap.getMosaikMap().containsKey(MOSAIK_REACTIVE_POWER)) {
                return new PValue(
                        Quantities.getQuantity(valueMap.getMosaikMap().get(MOSAIK_ACTIVE_POWER) * 1000, StandardUnits.ACTIVE_POWER_IN)
                        );
            } else {
                throw new ConvertionException("This factory can only convert PValue or SValue.");
            }
        } else {
            throw new ConvertionException("This factory can only convert Mosaik entities.");
        }
    }
}
