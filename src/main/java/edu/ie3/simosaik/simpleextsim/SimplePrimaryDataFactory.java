package edu.ie3.simosaik.simpleextsim;

import edu.ie3.datamodel.models.value.PValue;
import edu.ie3.datamodel.models.value.Value;
import edu.ie3.simona.api.data.primarydata.PrimaryDataFactory;
import edu.ie3.simona.api.exceptions.ConvertionException;

public class SimplePrimaryDataFactory implements PrimaryDataFactory {

    @Override
    public Value convert(Object entity) throws ConvertionException {
        if (entity.getClass() == PValue.class) {
            return (PValue) entity;
        } else {
            throw new ConvertionException("This factory can only convert PValue entities.");
        }
    }

}
