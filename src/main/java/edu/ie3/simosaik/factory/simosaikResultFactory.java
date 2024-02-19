package edu.ie3.simosaik.factory;

import edu.ie3.datamodel.models.result.NodeResult;
import edu.ie3.datamodel.models.result.ResultEntity;
import edu.ie3.datamodel.models.result.connector.ConnectorResult;
import edu.ie3.datamodel.models.result.system.ElectricalEnergyStorageResult;
import edu.ie3.datamodel.models.result.system.SystemParticipantResult;
import edu.ie3.datamodel.models.result.system.SystemParticipantWithHeatResult;
import edu.ie3.datamodel.models.result.thermal.ThermalUnitResult;
import edu.ie3.simona.api.data.results.ResultDataFactory;

public class simosaikResultFactory implements ResultDataFactory {

    @Override
    public Object convertResultToString(ResultEntity entity) throws Exception {
        String resultObject;
        if (entity instanceof SystemParticipantWithHeatResult systemParticipantWithHeatResult) {
            resultObject =
                    "{\"p\":\""
                            + systemParticipantWithHeatResult.getP()
                            + ",\"q\":\""
                            + systemParticipantWithHeatResult.getQ()
                            + ",\"qDot\":\""
                            + systemParticipantWithHeatResult.getqDot()
                            + "\"}";
        } else if (entity instanceof ElectricalEnergyStorageResult electricalEnergyStorageResult) {
            resultObject =
                    "{\"p\":\""
                            + electricalEnergyStorageResult.getP()
                            + ",\"q\":\""
                            + electricalEnergyStorageResult.getQ()
                            + ",\"soc\":\""
                            + electricalEnergyStorageResult.getSoc()
                            + "\"}";
        } else if (entity instanceof ConnectorResult connectorResult) {
            resultObject =
                    "{\"iAMag\":\""
                            + connectorResult.getiAMag()
                            + ",\"iAAng\":\""
                            + connectorResult.getiAAng()
                            + ",\"iBMag\":\""
                            + connectorResult.getiBMag()
                            + ",\"iBAng\":\""
                            + connectorResult.getiBAng()
                            + "\"}";
        } else if (entity instanceof NodeResult nodeResult) {
            resultObject =
                    "{\"vMag\":\"" + nodeResult.getvMag() + ",\"vAng\":\"" + nodeResult.getvAng() + "\"}";
        } else if (entity instanceof ThermalUnitResult thermalUnitResult) {
            resultObject = "{\"qDot\":\"" + thermalUnitResult.getqDot() + "\"}";
        } else if (entity instanceof SystemParticipantResult systemParticipantResult) {
            resultObject =
                    "{\"p\":\""
                            + systemParticipantResult.getP()
                            + ",\"q\":\""
                            + systemParticipantResult.getQ()
                            + "\"}";
        } else {
            resultObject = "{}";
        }
        return resultObject;
    }
}
