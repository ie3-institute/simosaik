package edu.ie3.simosaik.simpleextsim;

import edu.ie3.datamodel.models.result.ResultEntity;
import edu.ie3.simona.api.data.results.ResultDataFactory;
import edu.ie3.simona.api.exceptions.ConvertionException;

public class SimpleResultDataFactory implements ResultDataFactory {
    @Override
    public Object convert(ResultEntity entity) throws ConvertionException {
        return entity;
    }
}
