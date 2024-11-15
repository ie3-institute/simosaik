package edu.ie3.simpleextsim.data;

import edu.ie3.datamodel.models.value.PValue;
import edu.ie3.datamodel.models.value.Value;
import edu.ie3.simona.api.data.ExtInputDataValue;
import edu.ie3.simona.api.data.primarydata.PrimaryDataFactory;
import edu.ie3.simona.api.exceptions.ConversionException;

public class SimplePrimaryDataFactory implements PrimaryDataFactory {

    @Override
    public Value convert(ExtInputDataValue entity) throws ConversionException {
        if (entity instanceof SimpleExtSimValue simpleEntity) {
            return simpleEntity.value();
        } else {
            throw new ConversionException("This factory can only convert PValue entities.");
        }
    }

}
