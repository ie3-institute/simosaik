package edu.ie3.simosaik;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.offis.mosaik.api.SimProcess;
import de.offis.mosaik.api.Simulator;
import edu.ie3.datamodel.io.naming.timeseries.ColumnScheme;
import edu.ie3.datamodel.models.StandardUnits;
import edu.ie3.datamodel.models.result.NodeResult;
import edu.ie3.datamodel.models.value.Value;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

import edu.ie3.simopsim.data.SimopsimPrimaryDataWrapper;
import edu.ie3.simopsim.data.SimopsimResultWrapper;
import edu.ie3.simosaik.data.SimosaikPrimaryDataWrapper;
import edu.ie3.simosaik.data.SimosaikResultWrapper;
import edu.ie3.util.quantities.PowerSystemUnits;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import tech.units.indriya.ComparableQuantity;
import tech.units.indriya.quantity.Quantities;

import javax.measure.Quantity;
import javax.measure.quantity.Angle;
import javax.measure.quantity.Dimensionless;

public class SimonaSimulator extends Simulator {
    private int stepSize = 900;
    private Logger logger = SimProcess.logger;

    private static final JSONObject meta = (JSONObject) JSONValue.parse(("{"
            + "    'api_version': " + Simulator.API_VERSION + ","
            + "    'models': {"
            + "        'SimonaPowerGridEnvironment': {"
            + "            'public': true,"
            + "            'params': ['simona_config'],"
            + "            'attrs': []"
            + "        },"
            + "        'PrimaryInputEntities': {"
            + "            'public': true,"
            + "            'params': [],"
            + "            'attrs': ['P[MW]', 'Q[MVAr]', 'deltaU[kV]']"
            + "        },"
            + "        'ResultOutputEntities': {"
            + "            'public': true,"
            + "            'params': [],"
            + "            'attrs': ['P[MW]', 'Q[MVAr]', 'deltaU[kV]']"
            + "        }"
            + "    }"
            + "}").replace("'", "\""));

    public final LinkedBlockingQueue<SimosaikPrimaryDataWrapper> receiveTriggerQueueForPrimaryData = new LinkedBlockingQueue();
    public final LinkedBlockingQueue<SimosaikResultWrapper> receiveTriggerQueueForResults = new LinkedBlockingQueue();

    private final String[] simonaPrimaryEntities = {"Load_HS_2", "Load_HS_3"};
    private final String[] simonaResultEntities = {"Node_HS_2", "Node_HS_3"};

    public SimonaSimulator() {
        super("SimonaPowerGrid");
    }


    @Override
    public Map<String, Object> init(
            String sid,
            Float timeResolution,
            Map<String, Object> simParams
    ) throws Exception {
        return meta;
    }

    @Override
    public List<Map<String, Object>> create(
            int num,
            String model,
            Map<String, Object> modelParams
    ) throws Exception {
        //logger.info(this + "Create SimonaSimulator! Model = " + model);
        List<Map<String, Object>> entities = new ArrayList();
        if (Objects.equals(model, "SimonaPowerGridEnvironment")) {
            if (num > 1) {
                throw new RuntimeException("");
            }
            logger.info("Create SimonaPowerGridEnvironment!");
            Map<String, Object> entity = new HashMap<>();
            entity.put("eid", model);
            entity.put("type", model);
            entities.add(entity);
            return entities;
        } else if (Objects.equals(model, "PrimaryInputEntities")) {
            if (num > simonaPrimaryEntities.length) {
                throw new RuntimeException("");
            }
            logger.info("Create PrimaryInputEntities!");
            for (int i = 0; i < num; i++) {
                Map<String, Object> entity = new HashMap<>();
                entity.put("eid", simonaPrimaryEntities[i]);
                entity.put("type", model);
                entities.add(entity);
            }
            //logger.info("PrimaryInputEntities = " + entities);
            return entities;
        } else if (Objects.equals(model, "ResultOutputEntities")) {
            if (num > simonaResultEntities.length) {
                throw new RuntimeException("");
            }
            for (int i = 0; i < num; i++) {
                Map<String, Object> entity = new HashMap<>();
                entity.put("eid", simonaResultEntities[i]);
                entity.put("type", model);
                entities.add(entity);
            }
            return entities;
        } else {
            throw new RuntimeException("");
        }
    }

    @Override
    public long step(
            long time,
            Map<String, Object> inputs,
            long maxAdvance
    ) throws Exception {
        logger.info("Got inputs from MOSAIK for tick = " + time);
        SimosaikPrimaryDataWrapper primaryDataForSimona = new SimosaikPrimaryDataWrapper();

        inputs.forEach(
                (assetId, inputValue) -> {
                    //logger.info("assetId = " + assetId + ", inputValue = " + inputValue);
                    Map<String, Float> valueMap = new HashMap<>();
                    Map<String, Object> attrs = (Map<String, Object>) inputValue;
                    //go through attrs of the entity
                    for (Map.Entry<String, Object> attr : attrs.entrySet()) {
                        //check if there is a new delta
                        String attrName = attr.getKey();
                        if (attrName.equals("P[MW]")) {
                            //sum up deltas from different sources
                            Object[] values = ((Map<String, Object>) attr.getValue()).values().toArray();
                            float value = 0;
                            for (int i = 0; i < values.length; i++) {
                                value += ((Number) values[i]).floatValue();
                            }
                            valueMap.put("P[MW]", value);
                            //logger.info("Entity P[MW] = " + value);
                        }
                        if (attrName.equals("Q[MVAr]")) {
                            //sum up deltas from different sources
                            Object[] values = ((Map<String, Object>) attr.getValue()).values().toArray();
                            float value = 0;
                            for (int i = 0; i < values.length; i++) {
                                value += ((Number) values[i]).floatValue();
                            }
                            valueMap.put("Q[MVAr]", value);
                            //logger.info("Entity Q[MVAr] = " + value);
                        }
                    }
                    primaryDataForSimona.dataMap().put(assetId, valueMap);
                }
        );
        //logger.info("Created primaryDataForSimona"+ primaryDataForSimona);
        logger.info("Converted input for SIMONA! Now try to send it to SIMONA!");
        try {
            receiveTriggerQueueForPrimaryData.put(primaryDataForSimona);
            logger.info("Sent converted input for tick " + time + " to SIMONA!");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return time + this.stepSize;
    }

    @Override
    public Map<String, Object> getData(
            Map<String, List<String>> map
    ) throws Exception {
        logger.info("Got a request from MOSAIK to provide data!");
        SimosaikResultWrapper results = receiveTriggerQueueForResults.take();
        logger.info("Got results from SIMONA for MOSAIK!");

        Map<String, Object> data = new HashMap<>();

        map.forEach(
                (id, attrs) -> {
                    HashMap<String, Object> values = new HashMap<>();
                    for (String attr : attrs) {
                        if (attr.equals("deltaU[kV]")) {
                            if (results.tick() == 0L) {
                                values.put(attr, 0d);
                            } else {
                                values.put(attr, results.getVoltageDeviation(id));
                            }
                        }
                        if (attr.equals("P[MW]")) {
                            values.put(attr, results.getActivePower(id));
                        }
                        if (attr.equals("Q[MVAr]")) {
                            values.put(attr, results.getReactivePower(id));
                        }
                    }
                    data.put(id, values);
                }
        );
        logger.info("Converted results for MOSAIK! Now send it to MOSAIK!");
        return data;
    }


    // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

    public void queueResultsFromSimona(SimosaikResultWrapper data) throws InterruptedException {
        this.receiveTriggerQueueForResults.put(data);
    }
}
