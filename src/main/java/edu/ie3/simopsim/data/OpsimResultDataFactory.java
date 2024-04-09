package edu.ie3.simopsim.data;

import edu.ie3.datamodel.models.result.ResultEntity;
import edu.ie3.simona.api.data.results.ResultDataFactory;
import edu.ie3.simona.api.exceptions.ConvertionException;

public class OpsimResultDataFactory implements ResultDataFactory {

    @Override
    public Object convert(ResultEntity resultEntity) throws ConvertionException {
        return resultEntity;
    }
}
