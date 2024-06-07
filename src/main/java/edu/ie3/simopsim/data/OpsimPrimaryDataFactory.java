package edu.ie3.simopsim.data;

import de.fhg.iwes.opsim.datamodel.generated.realtimedata.OpSimScheduleElement;
import de.fhg.iwes.opsim.datamodel.generated.realtimedata.OpSimScheduleMessage;
import de.fhg.iwes.opsim.datamodel.generated.realtimedata.SetPointValueType;
import edu.ie3.datamodel.models.StandardUnits;
import edu.ie3.datamodel.models.value.PValue;
import edu.ie3.datamodel.models.value.Value;
import edu.ie3.simona.api.data.ExtInputDataValue;
import edu.ie3.simona.api.data.primarydata.PrimaryDataFactory;
import edu.ie3.simona.api.exceptions.ConvertionException;
import tech.units.indriya.quantity.Quantities;

import java.util.Iterator;

public class OpsimPrimaryDataFactory implements PrimaryDataFactory {
    @Override
    public Value convert(ExtInputDataValue entity) throws ConvertionException {
        if (entity instanceof OpSimScheduleMessage ossm) {
            Iterator var6 = ossm.getScheduleElements().iterator();
            while(var6.hasNext()) {
                OpSimScheduleElement ose = (OpSimScheduleElement) var6.next();
                if (ose.getScheduledValueType() == SetPointValueType.ACTIVE_POWER) {
                    return new PValue(Quantities.getQuantity(ose.getScheduledValue(), StandardUnits.ACTIVE_POWER_IN));
                }
            }
            throw new ConvertionException("No ACTIVEPOWER was provided!");
        } else {
            throw new ConvertionException("The message format" + entity.getClass() + "is not supported!");
        }
    }
}
