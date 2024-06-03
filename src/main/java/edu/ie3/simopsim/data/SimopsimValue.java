package edu.ie3.simopsim.data;

import de.fhg.iwes.opsim.datamodel.generated.realtimedata.OpSimMessage;
import edu.ie3.simona.api.data.ExtInputDataValue;

public class SimopsimValue implements ExtInputDataValue {

    private final OpSimMessage opSimMessage;

    public SimopsimValue(
            OpSimMessage opSimMessage
    ) {
        this.opSimMessage = opSimMessage;
    }

    public OpSimMessage getOpSimMessage() {
        return opSimMessage;
    }
}