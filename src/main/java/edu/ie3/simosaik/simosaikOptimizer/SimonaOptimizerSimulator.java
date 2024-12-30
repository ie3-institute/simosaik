package edu.ie3.simosaik.simosaikOptimizer;

import de.offis.mosaik.api.Simulator;
import edu.ie3.datamodel.models.result.ModelResultEntity;
import edu.ie3.datamodel.models.result.system.FlexOptionsResult;
import edu.ie3.datamodel.models.result.system.StorageResult;
import edu.ie3.simona.api.data.DataQueueExtSimulationExtSimulator;
import edu.ie3.simona.api.data.ExtInputDataContainer;
import edu.ie3.simona.api.data.results.ExtResultContainer;
import edu.ie3.simona.api.simulation.mapping.ExtEntityMapping;
import edu.ie3.simosaik.SimonaSimulator;
import edu.ie3.simosaik.SimosaikUtils;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.util.*;

import static edu.ie3.simona.api.simulation.mapping.ExtEntityEntry.*;
import static edu.ie3.simosaik.SimosaikTranslation.*;

public class SimonaOptimizerSimulator extends SimonaSimulator {
    private final int stepSize = 900;

    private long time;

    private static final String SIMONA_POWER_GRID_ENVIRONMENT = "SimonaPowerGridEnvironment";
    private static final String EM_AGENT_ENTITIES = "EmAgentEntities";
    private static final String RESULT_OUTPUT_ENTITIES = "ResultOutputEntities";
    private static final String STORAGE_ENTITIES = "StorageEntities";
    private static final String FLEX_OPTION_ENTITIES = "FlexOptionEntities";

    private static final JSONObject meta = (JSONObject) JSONValue.parse(("{"
            + "    'api_version': '" + Simulator.API_VERSION + "',"
            + "    'type': 'hybrid',"
            + "    'models': {"
            + "        '" + SIMONA_POWER_GRID_ENVIRONMENT + "': {"
            + "            'public': true,"
            + "            'params': ['simona_config'],"
            + "            'attrs': []"
            + "        },"
            + "        '" + EM_AGENT_ENTITIES + "': {"
            + "            'public': true,"
            + "            'params': [],"
            + "            'attrs': ['" + MOSAIK_ACTIVE_POWER + "', '" + MOSAIK_REACTIVE_POWER + "', '" + FLEX_OPTION_P_MIN + "', '" + FLEX_OPTION_P_REF + "', '" + FLEX_OPTION_P_MAX + "']"
            + "            'trigger': ['" + MOSAIK_ACTIVE_POWER + "', '" + MOSAIK_REACTIVE_POWER + "']"
            + "        }"
            //+ "        '" + FLEX_OPTION_ENTITIES + "': {"
            //+ "            'public': true,"
            //+ "            'params': [],"
            //+ "            'attrs': ['" + FLEX_OPTION_P_MIN + "', '" + FLEX_OPTION_P_REF + "', '" + FLEX_OPTION_P_MAX + "']"
            //+ "        }"
            + "        '" + RESULT_OUTPUT_ENTITIES + "': {"
            + "            'public': true,"
            + "            'params': [],"
            + "            'attrs': ['" + MOSAIK_VOLTAGE_PU + "']"
            + "        }"
            + "    }"
            + "}").replace("'", "\""));

    public DataQueueExtSimulationExtSimulator<ExtInputDataContainer> dataQueueMosaikToSimona;
    public DataQueueExtSimulationExtSimulator<ExtResultContainer> dataQueueSimonaToMosaik;

    private String[] simonaEmAgents;            // Agents who receive set points
    private String[] simonaFlexOptionEntities;  // Agents who send flex options for further calculations
    private String[] simonaResultOutputEntities;  // Agents who send flex options for further calculations

    private ExtEntityMapping mapping;

    private int counter;

    private Map<String, Object> resultCache;

    public SimonaOptimizerSimulator() {
        super("SimonaPowerGrid");
    }

    @Override
    public Map<String, Object> init(
            String sid,
            Float timeResolution,
            Map<String, Object> simParams
    ) throws Exception {
        this.counter = 0;
        this.resultCache = Collections.emptyMap();
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
        } else if (Objects.equals(model, EM_AGENT_ENTITIES)) {
            if (num != simonaEmAgents.length) {
                throw new IllegalArgumentException("Requested number (" + num + ") of " + EM_AGENT_ENTITIES + " entities is not possible.");
            }
            for (int i = 0; i < num; i++) {
                Map<String, Object> entity = new HashMap<>();
                entity.put("eid", simonaEmAgents[i]);
                entity.put("type", model);
                entities.add(entity);
            }
            return entities;
        } else if (Objects.equals(model, RESULT_OUTPUT_ENTITIES)) {
            if (num != simonaResultOutputEntities.length) {
                throw new IllegalArgumentException("Requested number (" + num + ") of " + RESULT_OUTPUT_ENTITIES + " entities is not possible.");
            }
            for (int i = 0; i < num; i++) {
                Map<String, Object> entity = new HashMap<>();
                entity.put("eid", simonaResultOutputEntities[i]);
                entity.put("type", model);
                entities.add(entity);
            }
            return entities;
        } else if (Objects.equals(model, FLEX_OPTION_ENTITIES)) {
            if (num != simonaFlexOptionEntities.length) {
                throw new IllegalArgumentException("Requested number (" + num + ") of " + FLEX_OPTION_ENTITIES + " entities is not possible.");
            }
            for (int i = 0; i < num; i++) {
                Map<String, Object> entity = new HashMap<>();
                entity.put("eid", simonaFlexOptionEntities[i]);
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
        if (time != this.time) {
            this.counter = 0;
        }
        this.counter = this.counter + 1;

        this.time = time;
        logger.info("[" + this.time + "] Got inputs from MOSAIK!");
        long nextTick = time + this.stepSize;
        try {
            if (!inputs.isEmpty()) {
                ExtInputDataContainer extInputDataContainer = SimosaikUtils.createExtInputDataContainer(
                        time,
                        inputs,
                        nextTick
                );

                // Wenn Container leer ist, dann sende einen leeren Container, um Simona auf Setpoints vorzubereiten.

                //logger.info("Converted input for SIMONA! Now try to send it to SIMONA!");
                dataQueueMosaikToSimona.queueData(extInputDataContainer);
                logger.info("[" + this.time + "] Sent converted input to SIMONA!");
            } else {
                logger.info("[" + this.time + "] Got an empty input, so we wait for valid data!");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return nextTick;
    }

    @Override
    public Map<String, Object> getData(
            Map<String, List<String>> map
    ) throws Exception {
        //logger.info("[" + this.time + "-" + this.counter + "] Got a request from MOSAIK to provide data!");
        //logger.info("[" + this.time + "-" + this.counter + "] Got a request from MOSAIK to provide data! \nOutputs = " + map);
        logger.info("[" + this.time + "] Got a request from MOSAIK to provide data!");
        if (this.counter == 1 || this.counter == 2) {
            ExtResultContainer results = dataQueueSimonaToMosaik.takeData();
            //logger.info("[" + this.time + "-" + this.counter + "] Got results from SIMONA for MOSAIK! \n" + results.getResultsAsString());
            Map<String, Object> data;
            if (this.counter == 2 && this.time == 0) {
                logger.info("[" + this.time + "] Got a final request from MOSAIK to provide data for tick 0!");
                data = createSimosaikOutputMapFromRequestedAttributes(
                        map,
                        results
                );
            } else {
                data = createSimosaikOutputMap(
                        map,
                        results
                );
            }
            data.put("time", this.time);
            this.resultCache = data;
        }
        //logger.info("[" + this.time + "-" + this.counter + "] Converted results for MOSAIK! Now send it to MOSAIK!\n Results = " + this.resultCache + "\n\n");
        logger.info("[" + this.time + "] Converted results for MOSAIK! Now send it to MOSAIK!\n");
        return this.resultCache;
    }

    public void setConnectionToSimonaApi(
            ExtEntityMapping mapping,
            DataQueueExtSimulationExtSimulator<ExtInputDataContainer> dataQueueExtCoSimulatorToSimonaApi,
            DataQueueExtSimulationExtSimulator<ExtResultContainer> dataQueueSimonaApiToExtCoSimulator
    ) {
        logger.info("Set the mapping and the data queues between SIMONA and MOSAIK!");
        this.mapping = mapping;
        this.simonaEmAgents = this.mapping.getExtId2UuidMapping(EXT_INPUT).keySet().toArray(new String[0]);
        this.simonaFlexOptionEntities = this.mapping.getExtId2UuidMapping(EXT_RESULT_FLEX_OPTIONS).keySet().toArray(new String[0]);
        this.simonaResultOutputEntities = this.mapping.getExtId2UuidMapping(EXT_RESULT_GRID).keySet().toArray(new String[0]);
        this.dataQueueSimonaToMosaik = dataQueueSimonaApiToExtCoSimulator;
        this.dataQueueMosaikToSimona = dataQueueExtCoSimulatorToSimonaApi;
    }

    private double getSoc(
            ExtResultContainer results,
            String id
    ) {
        Map<String, ModelResultEntity> resultMap = results.getResults();
        if (resultMap.get(id) instanceof StorageResult storageResult) {
            return storageResult.getSoc().getValue().doubleValue();
        } else {
            throw new IllegalArgumentException(
                    "SOC is only available for StorageResult's!");
        }
    }


    private double[] getFlexOptions(ExtResultContainer results, String id) {
        Map<String, ModelResultEntity> resultMap = results.getResults();
        if (resultMap.get(id) instanceof FlexOptionsResult flexOptionsResult) {
            double[] flexOptions = {
                    flexOptionsResult.getpMin().getValue().doubleValue(),
                    flexOptionsResult.getpRef().getValue().doubleValue(),
                    flexOptionsResult.getpMax().getValue().doubleValue()
            };
            return flexOptions;
        } else {
            throw new IllegalArgumentException(
                    "FlexOptions is only available for FlexOptionsResult's!");
        }
    }

    public Map<String, Object> createSimosaikOutputMap(
            Map<String, List<String>> mosaikRequestedAttributes,
            ExtResultContainer simonaResults
    ) {
        Map<String, Object> outputMap = new HashMap<>();
        simonaResults.getResults().forEach(
                (id, result) -> {
                    List<String> attrs = mosaikRequestedAttributes.get(id);
                    HashMap<String, Object> values = new HashMap<>();
                    for (String attr : attrs) {
                        if (attr.equals(MOSAIK_ACTIVE_POWER_IN) || attr.equals(MOSAIK_REACTIVE_POWER_IN)) {
                            SimosaikUtils.addResult(
                                    simonaResults,
                                    id,
                                    attr,
                                    values
                            );
                        } else if(
                                attr.equals(FLEX_OPTION_P_MIN) ||
                                        attr.equals(FLEX_OPTION_P_REF) ||
                                        attr.equals(FLEX_OPTION_P_MAX)
                        ) {
                            addFlexOptions(
                                    simonaResults,
                                    id,
                                    attr,
                                    values
                            );
                        } else if(
                                attr.equals(MOSAIK_VOLTAGE_DEVIATION) || attr.equals(MOSAIK_VOLTAGE_PU)  // Grid assets
                        ) {
                            SimosaikUtils.addResult(
                                    simonaResults,
                                    id,
                                    attr,
                                    values
                            );
                        } else {
                            logger.info("id = " + id + " requested attr = " + attr);
                        }
                    }
                    outputMap.put(id, values);
                }
        );
        return outputMap;
    }


    public Map<String, Object> createSimosaikOutputMapFromRequestedAttributes(
            Map<String, List<String>> mosaikRequestedAttributes,
            ExtResultContainer simonaResults
    ) {
        Map<String, Object> outputMap = new HashMap<>();
        mosaikRequestedAttributes.forEach(
                (id, attrs) -> {
                    HashMap<String, Object> values = new HashMap<>();
                    for (String attr : attrs) {
                        if (attr.equals(MOSAIK_ACTIVE_POWER_IN) || attr.equals(MOSAIK_REACTIVE_POWER_IN)) {
                            SimosaikUtils.addResult(
                                    simonaResults,
                                    id,
                                    attr,
                                    values
                            );
                        } else if(
                                attr.equals(FLEX_OPTION_P_MIN) ||
                                        attr.equals(FLEX_OPTION_P_REF) ||
                                        attr.equals(FLEX_OPTION_P_MAX)
                        ) {
                            addFlexOptions(
                                    simonaResults,
                                    id,
                                    attr,
                                    values
                            );
                        } else if(
                                attr.equals(MOSAIK_VOLTAGE_DEVIATION) || attr.equals(MOSAIK_VOLTAGE_PU)   // Grid assets
                        ) {
                            SimosaikUtils.addResult(
                                    simonaResults,
                                    id,
                                    attr,
                                    values
                            );
                        } else {
                            logger.info("id = " + id + " requested attr = " + attr);
                        }
                    }
                    outputMap.put(id, values);
                }
        );
        return outputMap;
    }


    private void addFlexOptions(ExtResultContainer results, String id, String attr, Map<String, Object> outputMap) {
        if (attr.equals(FLEX_OPTION_P_MIN)) {
            outputMap.put(attr, getFlexOptions(results, id)[0]);
        }
        if (attr.equals(FLEX_OPTION_P_REF)) {
            outputMap.put(attr, getFlexOptions(results, id)[1]);
        }
        if (attr.equals(FLEX_OPTION_P_MAX)) {
            outputMap.put(attr, getFlexOptions(results, id)[2]);
        }
    }
}
