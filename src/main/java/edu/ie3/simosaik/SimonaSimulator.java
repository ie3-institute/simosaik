package edu.ie3.simosaik;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.offis.mosaik.api.SimProcess;
import de.offis.mosaik.api.Simulator;
import edu.ie3.datamodel.io.naming.timeseries.ColumnScheme;
import edu.ie3.datamodel.models.value.Value;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

public class SimonaSimulator extends Simulator {
    private Logger logger = SimProcess.logger;

    private MosaikSimulation mosaikSimulation;

    private Map<UUID, ColumnScheme> uuidToColumnScheme;

    private ObjectMapper mapper = new ObjectMapper();
    private static final JSONObject meta = (JSONObject) JSONValue.parse(("{"
            + "    'api_version': " + Simulator.API_VERSION + ","
            + "    'models': {"
            + "        'SimonaPowerGrid': {"
            + "            'public': true,"
            + "            'params': ['simona_config'],"
            + "            'attrs': []"
            + "        }"
            + "    }"
            + "}").replace("'", "\""));



    public SimonaSimulator(
            MosaikSimulation mosaikSimulation
    ) {
        super("SimonaPowerGrid");
        this.mosaikSimulation = mosaikSimulation;
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
        Map<String, Object> entity = new HashMap<>();
        entity.put("eid", model);
        entity.put("type", model);
        entities.add(entity);
        return entities;
    }

    @Override
    public long step(
            long time,
            Map<String, Object> inputs,
            long maxAdvance
    ) throws Exception {
        /*
        Map<UUID, Value> inputsConverted = createEntityMap(inputs);                // UUID -> String, Object -> Value
        mosaikSimulation.extPrimaryData.putPrimaryDataInQueue(time, inputsConverted);
        long nextTick = mosaikSimulation.extPrimaryData.receiveFinishMessageFromSimona();
        return nextTick;
         */
        return 0;
    }

    @Override
    public Map<String, Object> getData(
            Map<String, List<String>> map
    ) throws Exception {
        // Schicke Results an mosaik
        return null;
    }


    // -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

    private<V extends Value> V createEntity(Object agentInputValue, Class<V> valueClass) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(agentInputValue.toString(), valueClass);
    }

    private Map<UUID, Value> createEntityMap(Map<String, Object> inputMap) throws IOException {
        Map<UUID, Value> convertedInputMap = new HashMap<>();
        inputMap.forEach(
                (uuid, inputValue) -> {
                    try {
                        UUID agentUUID = UUID.fromString(uuid);
                        convertedInputMap.put(agentUUID, createEntity(inputValue, uuidToColumnScheme.get(agentUUID).getValueClass()));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
        );
        return convertedInputMap;
    }

    /*
    private Optional<TimeBasedValue<V>> createEntity(Map<String, String> fieldToValues) {
        fieldToValues.remove("timeSeries");
        return this.createTimeBasedValue(fieldToValues).getData();
    }

    public <V extends Value> Try<V, FactoryException> createPrimaryDataValue(Map<String, String> fieldToValues) {
        PrimaryDataValueData<V> factoryData = new PrimaryDataValueData<>(fieldToValues, )
    }

    public <V extends Value> Class<V> getValueClass(UUID agentUUID) {
        return (Class<V>) uuidToColumnScheme.get(agentUUID).getValueClass();
    }
     */
}
