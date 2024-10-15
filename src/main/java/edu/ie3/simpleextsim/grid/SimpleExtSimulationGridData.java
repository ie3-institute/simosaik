package edu.ie3.simpleextsim.grid;

import edu.ie3.datamodel.models.StandardUnits;
import edu.ie3.datamodel.models.value.PValue;
import edu.ie3.simpleextsim.model.SimpleFlexibilityController;
import edu.ie3.simpleextsim.model.SimpleLoadModel;
import tech.units.indriya.quantity.Quantities;

import java.util.HashMap;
import java.util.UUID;

public class SimpleExtSimulationGridData {

    public final static String EM_3 = "EM_3";
    public final static String EM_4 = "EM_4";

    public final static String PV_1 = "PV_1";
    public final static String PV_2 = "PV_2";

    public final static String LOAD_1 = "LOAD_NS_Node_1";
    public final static String LOAD_2 = "LOAD_NS_Node_2";

    public final static String NODE_1 = "Node_1";
    public final static String NODE_2 = "Node_2";

    public final static UUID EM_3_UUID = UUID.fromString("fd1a8de9-722a-4304-8799-e1e976d9979c");
    public final static UUID EM_4_UUID = UUID.fromString("ff0b995a-86ff-4f4d-987e-e475a64f2180");

    public final static UUID LOAD_1_UUID = UUID.fromString("4dca3b1d-5d24-444a-b4df-f4fa23b9ef1b");
    public final static UUID LOAD_2_UUID = UUID.fromString("9c5991bc-24df-496b-b4ce-5ec27657454c");

    public final static UUID PV_1_UUID = UUID.fromString("a1eb7fc1-3bee-4b65-a387-ef3046644bf0");
    public final static UUID PV_2_UUID = UUID.fromString("9d7cd8e2-d859-4f4f-9c01-abba06ef2e2c");

    public final static UUID NODE_1_UUID = UUID.fromString("00d03670-7833-47ee-ad52-04d18d1c64fd");
    public final static UUID NODE_2_UUID = UUID.fromString("dfae9806-9b44-4995-ba27-d66d8e4a43e0");

    private SimpleExtSimulationGridData() {}


    public final static SimpleFlexibilityController EM_CONTROLLER_3 = new SimpleFlexibilityController(
            EM_3_UUID,
            EM_3,
            createTimeSeries1()
    );

    public final static SimpleFlexibilityController EM_CONTROLLER_4 = new SimpleFlexibilityController(
            EM_4_UUID,
            EM_4,
            createTimeSeries2()
    );

    public final static SimpleLoadModel LOAD_MODEL_1 = new SimpleLoadModel(
            LOAD_1_UUID,
            LOAD_1,
            createTimeSeries3()
    );

    public final static SimpleLoadModel LOAD_MODEL_2 = new SimpleLoadModel(
            LOAD_2_UUID,
            LOAD_2,
            createTimeSeries3()
    );

    private static HashMap<Long, PValue> createTimeSeries1() {
        HashMap<Long, PValue> ts = new HashMap<>();
        ts.put(0L, new PValue(Quantities.getQuantity(-0.5, StandardUnits.ACTIVE_POWER_IN)));
        ts.put(1L, new PValue(Quantities.getQuantity(-0.25, StandardUnits.ACTIVE_POWER_IN)));
        ts.put(2L, new PValue(Quantities.getQuantity(0.25, StandardUnits.ACTIVE_POWER_IN)));
        ts.put(3L, new PValue(Quantities.getQuantity(0.5, StandardUnits.ACTIVE_POWER_IN)));
        return ts;
    }

    private static HashMap<Long, PValue> createTimeSeries2() {
        HashMap<Long, PValue> ts = new HashMap<>();
        ts.put(0L, new PValue(Quantities.getQuantity(0.5, StandardUnits.ACTIVE_POWER_IN)));
        ts.put(1L, new PValue(Quantities.getQuantity(0.25, StandardUnits.ACTIVE_POWER_IN)));
        ts.put(2L, new PValue(Quantities.getQuantity(-0.25, StandardUnits.ACTIVE_POWER_IN)));
        ts.put(3L, new PValue(Quantities.getQuantity(-0.5, StandardUnits.ACTIVE_POWER_IN)));
        return ts;
    }


    private static HashMap<Long, PValue> createTimeSeries3() {
        HashMap<Long, PValue> ts = new HashMap<>();
        ts.put(0L, new PValue(Quantities.getQuantity(0.75, StandardUnits.ACTIVE_POWER_IN)));
        ts.put(1L, new PValue(Quantities.getQuantity(0.67, StandardUnits.ACTIVE_POWER_IN)));
        ts.put(2L, new PValue(Quantities.getQuantity(0.55, StandardUnits.ACTIVE_POWER_IN)));
        ts.put(3L, new PValue(Quantities.getQuantity(0.63, StandardUnits.ACTIVE_POWER_IN)));
        return ts;
    }
}