package edu.ie3.simpleextsim.data;

import edu.ie3.datamodel.models.value.Value;
import edu.ie3.simona.api.data.ExtInputDataValue;

public record SimpleExtSimValue(
        Value value
) implements ExtInputDataValue {}
