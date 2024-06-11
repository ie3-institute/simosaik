package edu.ie3.simopsim.data;

import de.fhg.iwes.opsim.datamodel.generated.realtimedata.OpSimMessage;
import edu.ie3.simona.api.data.ExtInputDataValue;

/**
 * Interface class that contains an OPSIM message
 */
public record SimopsimValue(OpSimMessage opSimMessage) implements ExtInputDataValue {}