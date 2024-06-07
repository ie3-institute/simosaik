package edu.ie3.simosaik;

import de.offis.mosaik.api.SimProcess;
import de.offis.mosaik.api.Simulator;
import edu.ie3.simona.api.data.DataQueueExtSimulationExtSimulator;
import edu.ie3.simona.api.data.ExtInputDataPackage;
import edu.ie3.simona.api.data.results.ExtResultPackage;
import edu.ie3.simona.api.simulation.mapping.ExtEntityMapping;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.util.*;
import java.util.logging.Logger;

import static edu.ie3.simona.api.simulation.mapping.ExtEntityEntry.EXT_INPUT;
import static edu.ie3.simona.api.simulation.mapping.ExtEntityEntry.EXT_RESULT_GRID;
import static edu.ie3.simosaik.SimosaikTranslation.*;

public class SimonaSimulator extends Simulator {
    private final int stepSize = 900;
    private final Logger logger = SimProcess.logger;

    private static final String SIMONA_POWER_GRID_ENVIRONMENT = "SimonaPowerGridEnvironment";
    private static final String PRIMARY_INPUT_ENTITIES = "PrimaryInputEntities";
    private static final String RESULT_OUTPUT_ENTITIES = "ResultOutputEntities";

    private static final JSONObject meta = (JSONObject) JSONValue.parse(("{"
            + "    'api_version': '" + Simulator.API_VERSION + "',"
            + "    'type': 'time-based',"
            + "    'models': {"
            + "        '" + SIMONA_POWER_GRID_ENVIRONMENT + "': {"
            + "            'public': true,"
            + "            'params': ['simona_config'],"
            + "            'attrs': []"
            + "        },"
            + "        '" + PRIMARY_INPUT_ENTITIES + "': {"
            + "            'public': true,"
            + "            'params': [],"
            + "            'attrs': ['" + MOSAIK_ACTIVE_POWER + "', '" + MOSAIK_REACTIVE_POWER + "', '" + MOSAIK_VOLTAGE_DEVIATION + "']"
            + "        },"
            + "        '" + RESULT_OUTPUT_ENTITIES + "': {"
            + "            'public': true,"
            + "            'params': [],"
            + "            'attrs': ['" + MOSAIK_ACTIVE_POWER + "', '" + MOSAIK_REACTIVE_POWER + "', '" + MOSAIK_VOLTAGE_DEVIATION + "']"
            + "        }"
            + "    }"
            + "}").replace("'", "\""));

    public final DataQueueExtSimulationExtSimulator<ExtInputDataPackage> dataQueueMosaikToSimona;
    public final DataQueueExtSimulationExtSimulator<ExtResultPackage> dataQueueSimonaToMosaik;

    private final String[] simonaPrimaryEntities;
    private final String[] simonaResultEntities;

    private final ExtEntityMapping mapping;

    public SimonaSimulator(ExtEntityMapping mapping) {
        super("SimonaPowerGrid");
        this.dataQueueMosaikToSimona = new DataQueueExtSimulationExtSimulator<>();
        this.dataQueueSimonaToMosaik = new DataQueueExtSimulationExtSimulator<>();
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
        List<Map<String, Object>> entities = new ArrayList<>();
        if (Objects.equals(model, SIMONA_POWER_GRID_ENVIRONMENT)) {
            if (num != 1) {
                throw new IllegalArgumentException("Requested number (" + num + ") of " + SIMONA_POWER_GRID_ENVIRONMENT + " entities is not possible.");
            }
            Map<String, Object> entity = new HashMap<>();
            entity.put("eid", model);
            entity.put("type", model);
            entities.add(entity);
            return entities;
        } else if (Objects.equals(model, PRIMARY_INPUT_ENTITIES)) {
            if (num != simonaPrimaryEntities.length) {
                throw new IllegalArgumentException("Requested number (" + num + ") of " + PRIMARY_INPUT_ENTITIES + " entities is not possible.");
            }
            for (int i = 0; i < num; i++) {
                Map<String, Object> entity = new HashMap<>();
                entity.put("eid", simonaPrimaryEntities[i]);
                entity.put("type", model);
                entities.add(entity);
            }
            return entities;
        } else if (Objects.equals(model, RESULT_OUTPUT_ENTITIES)) {
            if (num != simonaResultEntities.length) {
                throw new IllegalArgumentException("Requested number (" + num + ") of " + RESULT_OUTPUT_ENTITIES + " entities is not possible.");
            }
            for (int i = 0; i < num; i++) {
                Map<String, Object> entity = new HashMap<>();
                entity.put("eid", simonaResultEntities[i]);
                entity.put("type", model);
                entities.add(entity);
            }
            return entities;
        } else {
            throw new IllegalArgumentException("The model " + model + " is not supported by SimonaSimulator.");
        }
    }

    @Override
    public long step(
            long time,
            Map<String, Object> inputs,
            long maxAdvance
    ) throws Exception {
        try {
            logger.info("Got inputs from MOSAIK for tick = " + time);
            ExtInputDataPackage primaryDataForSimona = SimosaikUtils.createSimosaikPrimaryDataWrapper(
                    inputs
            );
            logger.info("Converted input for SIMONA! Now try to send it to SIMONA!");
            dataQueueMosaikToSimona.queueData(primaryDataForSimona);
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
        ExtResultPackage results = dataQueueSimonaToMosaik.takeData();
        logger.info("Got results from SIMONA for MOSAIK!");
        Map<String, Object> data = SimosaikUtils.createSimosaikOutputMap(
                map,
                results
        );
        logger.info("Converted results for MOSAIK! Now send it to MOSAIK!");
        return data;
    }
}
