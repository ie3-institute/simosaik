package edu.ie3.simosaik.data;

import edu.ie3.datamodel.models.StandardUnits;
import edu.ie3.datamodel.models.value.PValue;
import edu.ie3.datamodel.models.value.Value;
import edu.ie3.simona.api.data.primarydata.PrimaryDataFactory;
import edu.ie3.simona.api.exceptions.ConvertionException;
import tech.units.indriya.quantity.Quantities;

public class MosaikPrimaryDataFactory implements PrimaryDataFactory {

    @Override
    public Value convert(Object entity) throws ConvertionException {
        if (entity.getClass() == Double.class) {
            return new PValue(Quantities.getQuantity((Double) entity*1000, StandardUnits.ACTIVE_POWER_IN));
        } else {
            throw new ConvertionException("This factory can only convert PValue entities.");
        }
    }

}
