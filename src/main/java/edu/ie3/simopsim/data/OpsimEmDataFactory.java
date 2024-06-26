package edu.ie3.simopsim.data;

import de.fhg.iwes.opsim.datamodel.generated.realtimedata.OpSimMessage;
import de.fhg.iwes.opsim.datamodel.generated.realtimedata.OpSimScheduleElement;
import de.fhg.iwes.opsim.datamodel.generated.realtimedata.OpSimScheduleMessage;
import de.fhg.iwes.opsim.datamodel.generated.realtimedata.SetPointValueType;
import edu.ie3.datamodel.models.StandardUnits;
import edu.ie3.datamodel.models.value.PValue;
import edu.ie3.simona.api.data.ExtInputDataValue;
import edu.ie3.simona.api.data.em.EmDataFactory;
import edu.ie3.simona.api.exceptions.ConvertionException;
import tech.units.indriya.quantity.Quantities;

public class OpsimEmDataFactory implements EmDataFactory {
    @Override
    public PValue convert(ExtInputDataValue entity) throws ConvertionException {
        if (entity instanceof SimopsimValue simopsimValue) {
            OpSimMessage osm = simopsimValue.opSimMessage();
            if (osm instanceof OpSimScheduleMessage ossm) {
                for (OpSimScheduleElement ose : ossm.getScheduleElements()) {
                    if (ose.getScheduledValueType() == SetPointValueType.ACTIVE_POWER) {
                        return new PValue(Quantities.getQuantity(ose.getScheduledValue(), StandardUnits.ACTIVE_POWER_IN));
                    }
                }
                throw new ConvertionException("Only ACTIVEPOWER set points allowed for external controlled EM agents!");
            } else {
                throw new ConvertionException("The OpSimMessage" + osm.getClass() + " is not supported!");
            }
        } else {
            throw new ConvertionException("Only SimopsimValue supported! " + entity.getClass() + " is not supported!");
        }
    }
}
