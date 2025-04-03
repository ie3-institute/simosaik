/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.flexibility;

import static edu.ie3.simona.api.data.mapping.DataType.*;
import static edu.ie3.simosaik.utils.SimosaikTranslation.*;

import edu.ie3.datamodel.models.result.ResultEntity;
import edu.ie3.datamodel.models.result.system.FlexOptionsResult;
import edu.ie3.datamodel.models.result.system.StorageResult;
import edu.ie3.simona.api.data.ExtDataContainerQueue;
import edu.ie3.simona.api.data.container.ExtInputDataContainer;
import edu.ie3.simona.api.data.container.ExtResultContainer;
import edu.ie3.simona.api.data.results.model.DesaggFlexOptionsResult;
import edu.ie3.simosaik.MosaikSimulator;
import edu.ie3.simosaik.utils.SimosaikUtils;
import java.util.*;

// TODO: Refactor this class
public class FlexOptionOptimizerSimulator extends MosaikSimulator {
  private Set<String> simonaEmAgents; // Agents who receive set points
  private Set<String>
      simonaFlexOptionEntities; // Agents who send flex options for further calculations
  private Set<String>
      simonaResultOutputEntities; // Agents who send flex options for further calculations

  private final boolean useFlexOptionEntitiesInsteadOfEmAgents;

  private long time;
  private int counter;
  private Map<String, Object> resultCache;

  public FlexOptionOptimizerSimulator(
      int stepSize, boolean useFlexOptionEntitiesInsteadOfEmAgents) {
    super("SimonaPowerGrid", stepSize);
    this.useFlexOptionEntitiesInsteadOfEmAgents = useFlexOptionEntitiesInsteadOfEmAgents;
  }

  /*
  @Override
  public Map<String, Object> init(String sid, Float timeResolution, Map<String, Object> simParams) {
    this.counter = 0;
    this.resultCache = Collections.emptyMap();

    if (useFlexOptionEntitiesInsteadOfEmAgents) {
      return flexOptionEntitiesMeta();
    } else {
      return emEntitiesMeta();
    }
  }

  private Map<String, Object> emEntitiesMeta() {
    List<String> emUnits =
        List.of(
            MOSAIK_ACTIVE_POWER,
            MOSAIK_REACTIVE_POWER,
            FLEX_OPTION_P_MIN,
            FLEX_OPTION_P_REF,
            FLEX_OPTION_P_MAX);

    return MetaUtils.createMeta(
        "hybrid",
        Model.of(
            , emUnits, List.of(MOSAIK_ACTIVE_POWER, MOSAIK_REACTIVE_POWER)),
        Model.of(RESULT_OUTPUT_ENTITIES, MOSAIK_VOLTAGE_PU));
  }

  private Map<String, Object> flexOptionEntitiesMeta() {
    List<String> emUnits =
        List.of(
            MOSAIK_ACTIVE_POWER,
            MOSAIK_REACTIVE_POWER,
            FLEX_OPTION_MAP_P_MIN,
            FLEX_OPTION_MAP_P_REF,
            FLEX_OPTION_MAP_P_MAX);

    return MetaUtils.createMeta(
        "hybrid",
        ModelParams.of(FLEX_OPTION_ENTITIES, emUnits),
        ModelParams.of(RESULT_OUTPUT_ENTITIES, MOSAIK_VOLTAGE_PU));
  }

  @Override
  public List<Map<String, Object>> create(int num, String model, Map<String, Object> modelParams) {
    List<Map<String, Object>> entities = new ArrayList<>();
    if (Objects.equals(model, SIMONA_POWER_GRID_ENVIRONMENT)) {
      if (num != 1) {
        throw new IllegalArgumentException(
            "Requested number ("
                + num
                + ") of "
                + SIMONA_POWER_GRID_ENVIRONMENT
                + " entities is not possible.");
      }
      Map<String, Object> entity = new HashMap<>();
      entity.put("eid", model);
      entity.put("type", model);

      List<Map<String, Object>> childEntities = new ArrayList<>();

      if (useFlexOptionEntitiesInsteadOfEmAgents) {

        // FLEX_OPTION_ENTITIES
        childEntities.addAll(buildMap(FLEX_OPTION_ENTITIES, simonaFlexOptionEntities));
      } else {

        // EM_AGENT_ENTITIES
        childEntities.addAll(buildMap(EM_AGENT_ENTITIES, simonaEmAgents));
      }

      // RESULT_OUTPUT_ENTITIES
      childEntities.addAll(buildMap(RESULT_OUTPUT_ENTITIES, simonaResultOutputEntities));

      entity.put("children", childEntities);
      entities.add(entity);
      return entities;
    } else {
      throw new IllegalArgumentException(
          "The model " + model + " is not supported by SimonaSimulator.");
    }
  }
   */

  @Override
  public long step(long time, Map<String, Object> inputs, long maxAdvance) {
    if (time != this.time) {
      this.counter = 0;
    }
    this.counter = this.counter + 1;

    this.time = time;
    logger.info("[" + this.time + "] Got inputs from MOSAIK!");
    long nextTick = time + this.stepSize;
    try {
      if (!inputs.isEmpty()) {
        // ExtInputDataContainer extInputDataContainer =
        // SimosaikUtils.createExtInputDataContainer(time, inputs, nextTick);
        // logger.info("Converted input for SIMONA! Now try to send it to SIMONA!");
        queueToSimona.queueData(new ExtInputDataContainer(time));
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
  public Map<String, Object> getData(Map<String, List<String>> map) throws Exception {
    logger.info("[" + this.time + "] Got a request from MOSAIK to provide data!");

    if (this.counter == 1 || this.counter == 2) {
      ExtResultContainer results = queueToExt.takeAll();
      Map<String, Object> data;

      if (this.counter == 2 && this.time == 0) {
        logger.info(
            "[" + this.time + "] Got a final request from MOSAIK to provide data for tick 0!");
        data = createSimosaikOutputMapFromRequestedAttributes(map, results);
      } else {
        data = createSimosaikOutputMap(map, results);
      }

      data.put("time", this.time);
      this.resultCache = data;
    }

    logger.info("[" + this.time + "] Converted results for MOSAIK! Now send it to MOSAIK!\n");
    return this.resultCache;
  }

  public void setConnectionToSimonaApi(
      ExtDataContainerQueue<ExtInputDataContainer> dataQueueExtCoSimulatorToSimonaApi,
      ExtDataContainerQueue<ExtResultContainer> dataQueueSimonaApiToExtCoSimulator) {
    logger.info("Set the mapping and the data queues between SIMONA and MOSAIK!");
    this.queueToExt = dataQueueSimonaApiToExtCoSimulator;
    this.queueToSimona = dataQueueExtCoSimulatorToSimonaApi;

    this.simonaEmAgents = mapping.getExtId2UuidMapping(EXT_EM_INPUT).keySet();
    this.simonaFlexOptionEntities = mapping.getExtId2UuidMapping(EXT_FLEX_OPTIONS_RESULT).keySet();
    this.simonaResultOutputEntities = mapping.getExtId2UuidMapping(EXT_GRID_RESULT).keySet();
  }

  private double getSoc(ExtResultContainer results, UUID id) {
    Map<UUID, ResultEntity> resultMap = results.getResults();
    if (resultMap.get(id) instanceof StorageResult storageResult) {
      return storageResult.getSoc().getValue().doubleValue();
    } else {
      throw new IllegalArgumentException("SOC is only available for StorageResult's!");
    }
  }

  private double[] getFlexOptions(ExtResultContainer results, UUID id) {
    Map<UUID, ResultEntity> resultMap = results.getResults();
    if (resultMap.get(id) instanceof FlexOptionsResult flexOptionsResult) {
      return getFlexMinRefMaxFlexOptions(flexOptionsResult);
    } else {
      throw new IllegalArgumentException("FlexOptions is only available for FlexOptionsResult's!");
    }
  }

  private DetailedFlexOptions getConnectedFlexOptions(ExtResultContainer results, UUID id) {
    Map<UUID, ResultEntity> resultMap = results.getResults();
    if (resultMap.get(id) instanceof FlexOptionsResult flexOptionsResult) {
      return new DetailedFlexOptions(flexOptionsResult);
    } else {
      throw new IllegalArgumentException("FlexOptions is only available for FlexOptionsResult's!");
    }
  }

  private double[] getFlexMinRefMaxFlexOptions(FlexOptionsResult flexOptionsResult) {
    return new double[] {
      flexOptionsResult.getpMin().getValue().doubleValue(),
      flexOptionsResult.getpRef().getValue().doubleValue(),
      flexOptionsResult.getpMax().getValue().doubleValue()
    };
  }

  public Map<String, Object> createSimosaikOutputMap(
      Map<String, List<String>> mosaikRequestedAttributes, ExtResultContainer simonaResults) {
    Map<UUID, String> c = mapping.getExtUuid2IdMapping(EXT_EM_INPUT);

    Map<String, Object> outputMap = new HashMap<>();
    simonaResults
        .getResults()
        .forEach(
            (id, result) -> {
              List<String> attrs = mosaikRequestedAttributes.get(c.get(id));
              HashMap<String, Object> values = new HashMap<>();
              for (String attr : attrs) {
                switch (attr) {
                  case MOSAIK_ACTIVE_POWER_IN, MOSAIK_REACTIVE_POWER_IN ->
                      SimosaikUtils.addResult(simonaResults, id, attr, values);
                  case FLEX_OPTION_P_MIN,
                          FLEX_OPTION_P_REF,
                          FLEX_OPTION_P_MAX,
                          FLEX_OPTION_MAP_P_MIN,
                          FLEX_OPTION_MAP_P_REF,
                          FLEX_OPTION_MAP_P_MAX ->
                      addFlexOptions(simonaResults, id, attr, values);
                  case MOSAIK_VOLTAGE_DEVIATION_PU, MOSAIK_VOLTAGE_PU ->
                      // Grid assets
                      SimosaikUtils.addResult(simonaResults, id, attr, values);
                  default -> logger.info("id = " + id + " requested attr = " + attr);
                }
              }
              outputMap.put(c.get(id), values);
            });
    return outputMap;
  }

  public Map<String, Object> createSimosaikOutputMapFromRequestedAttributes(
      Map<String, List<String>> mosaikRequestedAttributes, ExtResultContainer simonaResults) {
    Map<String, UUID> c = mapping.getExtId2UuidMapping(EXT_EM_INPUT);

    Map<String, Object> outputMap = new HashMap<>();
    mosaikRequestedAttributes.forEach(
        (id, attrs) -> {
          HashMap<String, Object> values = new HashMap<>();
          for (String attr : attrs) {
            switch (attr) {
              case MOSAIK_ACTIVE_POWER_IN, MOSAIK_REACTIVE_POWER_IN ->
                  SimosaikUtils.addResult(simonaResults, c.get(id), attr, values);
              case FLEX_OPTION_P_MIN,
                      FLEX_OPTION_P_REF,
                      FLEX_OPTION_P_MAX,
                      FLEX_OPTION_MAP_P_MIN,
                      FLEX_OPTION_MAP_P_REF,
                      FLEX_OPTION_MAP_P_MAX ->
                  addFlexOptions(simonaResults, c.get(id), attr, values);
              case MOSAIK_VOLTAGE_DEVIATION_PU, MOSAIK_VOLTAGE_PU ->
                  // Grid assets
                  SimosaikUtils.addResult(simonaResults, c.get(id), attr, values);
              default -> logger.info("id = " + id + " requested attr = " + attr);
            }
          }
          outputMap.put(id, values);
        });
    return outputMap;
  }

  private void addFlexOptions(
      ExtResultContainer results, UUID id, String attr, Map<String, Object> outputMap) {
    if (attr.equals(FLEX_OPTION_P_MIN)) {
      outputMap.put(attr, getFlexOptions(results, id)[0]);
    }
    if (attr.equals(FLEX_OPTION_P_REF)) {
      outputMap.put(attr, getFlexOptions(results, id)[1]);
    }
    if (attr.equals(FLEX_OPTION_P_MAX)) {
      outputMap.put(attr, getFlexOptions(results, id)[2]);
    }
    if (attr.equals(FLEX_OPTION_MAP_P_MAX)) {
      outputMap.put(attr, getConnectedFlexOptions(results, id).getMinFlexOptions());
    }
    if (attr.equals(FLEX_OPTION_MAP_P_REF)) {
      outputMap.put(attr, getConnectedFlexOptions(results, id).getRefFlexOptions());
    }
    if (attr.equals(FLEX_OPTION_MAP_P_MIN)) {
      outputMap.put(attr, getConnectedFlexOptions(results, id).getMaxFlexOptions());
    }
  }

  private class DetailedFlexOptions {
    private final Map<String, Double> minFlexOptions;
    private final Map<String, Double> refFlexOptions;
    private final Map<String, Double> maxFlexOptions;

    public DetailedFlexOptions(FlexOptionsResult flexOptionsResult) {
      Map<String, Double> connectedPmin = new HashMap<>();
      Map<String, Double> connectedPref = new HashMap<>();
      Map<String, Double> connectedPmax = new HashMap<>();
      double[] flexOptionArray = getFlexMinRefMaxFlexOptions(flexOptionsResult);
      connectedPmin.put("EM", flexOptionArray[0]);
      connectedPref.put("EM", flexOptionArray[1]);
      connectedPmax.put("EM", flexOptionArray[2]);

      if (flexOptionsResult instanceof DesaggFlexOptionsResult desaggFlexOptionsResult) {
        Map<String, FlexOptionsResult> connectedFlexOptions =
            desaggFlexOptionsResult.getConnectedFlexOptionResults();
        for (String key : connectedFlexOptions.keySet()) {
          flexOptionArray = getFlexMinRefMaxFlexOptions(connectedFlexOptions.get(key));
          connectedPmin.put(key, flexOptionArray[0]);
          connectedPref.put(key, flexOptionArray[1]);
          connectedPmax.put(key, flexOptionArray[2]);
        }
      }

      this.minFlexOptions = connectedPmin;
      this.refFlexOptions = connectedPref;
      this.maxFlexOptions = connectedPmax;
    }

    public Map<String, Double> getMinFlexOptions() {
      return this.minFlexOptions;
    }

    public Map<String, Double> getRefFlexOptions() {
      return this.refFlexOptions;
    }

    public Map<String, Double> getMaxFlexOptions() {
      return this.maxFlexOptions;
    }
  }
}
