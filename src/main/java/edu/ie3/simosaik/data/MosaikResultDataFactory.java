package edu.ie3.simosaik.data;

import edu.ie3.datamodel.models.result.ResultEntity;
import edu.ie3.simona.api.data.results.ResultDataFactory;
import edu.ie3.simona.api.exceptions.ConvertionException;

public class MosaikResultDataFactory implements ResultDataFactory {
    @Override
    public Object convert(ResultEntity entity) throws ConvertionException {
        return entity;
    }
}
