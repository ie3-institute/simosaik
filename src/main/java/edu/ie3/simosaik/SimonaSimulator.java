package edu.ie3.simosaik;

import de.offis.mosaik.api.SimProcess;
import de.offis.mosaik.api.Simulator;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

import edu.ie3.simona.api.data.ExtInputDataPackage;
import edu.ie3.simona.api.data.results.ExtResultPackage;
import edu.ie3.simona.api.simulation.mapping.ExtEntityMapping;
import edu.ie3.simosaik.data.SimosaikPrimaryDataWrapper;
import edu.ie3.simosaik.data.SimosaikValue;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import static edu.ie3.simona.api.simulation.mapping.ExtEntityEntry.EXT_INPUT;
import static edu.ie3.simona.api.simulation.mapping.ExtEntityEntry.EXT_RESULT_GRID;
import static edu.ie3.simosaik.SimosaikTranslation.*;

public class SimonaSimulator extends Simulator {
    private int stepSize = 900;
    private Logger logger = SimProcess.logger;

    private static final JSONObject meta = (JSONObject) JSONValue.parse(("{"
            + "    'api_version': '" + Simulator.API_VERSION + "',"
            + "    'type': 'time-based',"
            + "    'models': {"
            + "        'SimonaPowerGridEnvironment': {"
            + "            'public': true,"
            + "            'params': ['simona_config'],"
            + "            'attrs': []"
            + "        },"
            + "        'PrimaryInputEntities': {"
            + "            'public': true,"
            + "            'params': [],"
            + "            'attrs': ['" + MOSAIK_ACTIVE_POWER + "', '" + MOSAIK_REACTIVE_POWER + "', '" + MOSAIK_VOLTAGE_DEVIATION + "']"
            + "        },"
            + "        'ResultOutputEntities': {"
            + "            'public': true,"
            + "            'params': [],"
            + "            'attrs': ['" + MOSAIK_ACTIVE_POWER + "', '" + MOSAIK_REACTIVE_POWER + "', '" + MOSAIK_VOLTAGE_DEVIATION + "']"
            + "        }"
            + "    }"
            + "}").replace("'", "\""));

    public final LinkedBlockingQueue<ExtInputDataPackage> receiveTriggerQueueForInputData = new LinkedBlockingQueue();
    public final LinkedBlockingQueue<ExtResultPackage> receiveTriggerQueueForResults = new LinkedBlockingQueue();

    private final String[] simonaPrimaryEntities;
    private final String[] simonaResultEntities;

    private final ExtEntityMapping mapping;

    private final SimosaikUtils simosaikUtils;

    public SimonaSimulator(ExtEntityMapping mapping) {
        super("SimonaPowerGrid");
        this.simosaikUtils = new SimosaikUtils();
        this.mapping = mapping;
        this.simonaPrimaryEntities = this.mapping.getExtIdUuidMapping(EXT_INPUT).keySet().toArray(new String[0]);
        this.simonaResultEntities = this.mapping.getExtIdUuidMapping(EXT_RESULT_GRID).keySet().toArray(new String[0]);
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
        SimosaikPrimaryDataWrapper primaryDataForSimona = simosaikUtils.createSimosaikPrimaryDataWrapper(
                inputs
        );
        logger.info("Converted input for SIMONA! Now try to send it to SIMONA!");
        try {
            queueDataFromMosaik(primaryDataForSimona);
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
        ExtResultPackage results = receiveTriggerQueueForResults.take();
        logger.info("Got results from SIMONA for MOSAIK!");
        Map<String, Object> data = simosaikUtils.createSimosaikOutputMap(
                map,
                results
        );
        logger.info("Converted results for MOSAIK! Now send it to MOSAIK!");
        return data;
    }


    // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

    public void queueResultsFromSimona(ExtResultPackage data) throws InterruptedException {
        this.receiveTriggerQueueForResults.put(data);
    }

    public void queueDataFromMosaik(SimosaikPrimaryDataWrapper data) throws InterruptedException {
        this.receiveTriggerQueueForInputData.put(data);
    }
}
