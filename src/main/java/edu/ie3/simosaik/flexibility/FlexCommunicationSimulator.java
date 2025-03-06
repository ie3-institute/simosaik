/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.flexibility;

import static edu.ie3.simona.api.simulation.mapping.DataType.EXT_EM_INPUT;
import static edu.ie3.simona.api.simulation.mapping.DataType.EXT_GRID_RESULT;
import static edu.ie3.simosaik.SimosaikTranslation.*;

import edu.ie3.datamodel.models.result.ResultEntity;
import edu.ie3.datamodel.models.result.system.FlexOptionsResult;
import edu.ie3.simona.api.data.DataQueueExtSimulationExtSimulator;
import edu.ie3.simona.api.data.ExtInputDataContainer;
import edu.ie3.simona.api.data.results.ExtResultContainer;
import edu.ie3.simona.api.simulation.mapping.ExtEntityMapping;
import edu.ie3.simosaik.MetaUtils;
import edu.ie3.simosaik.MosaikSimulator;
import edu.ie3.simosaik.SimosaikUtils;
import java.util.*;

// TODO: Refactor this class
public class FlexCommunicationSimulator extends MosaikSimulator {
  /** Agents who receive set points */
  protected Set<String> simonaEmAgents;

  /** Agents who send flex options for further calculations */
  protected Set<String> simonaResultOutputEntities;

  protected long time;
  protected int counter;
  protected Map<String, Object> resultCache;

  public FlexCommunicationSimulator(int stepSize) {
    super("FlexCommunicationSimulator", stepSize);
  }

  @Override
  public Map<String, Object> init(String sid, Float timeResolution, Map<String, Object> simParams) {
    this.counter = 0;
    this.resultCache = Collections.emptyMap();

    List<String> emUnits =
        List.of(
            MOSAIK_ACTIVE_POWER,
            MOSAIK_REACTIVE_POWER,
            FLEX_OPTION_P_MIN,
            FLEX_OPTION_P_REF,
            FLEX_OPTION_P_MAX);

    return MetaUtils.createMeta(
        "hybrid",
        ModelParams.of(EM_AGENT_ENTITIES, emUnits),
        ModelParams.of(RESULT_OUTPUT_ENTITIES, emUnits));
  }

  @Override
  public final List<Map<String, Object>> create(
      int num, String model, Map<String, Object> modelParams) {
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

      // EM_AGENT_ENTITIES
      childEntities.addAll(buildMap(EM_AGENT_ENTITIES, simonaEmAgents));

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
        ExtInputDataContainer extInputDataContainer =
            SimosaikUtils.createExtInputDataContainer(time, inputs, nextTick);
        // logger.info("Converted input for SIMONA! Now try to send it to SIMONA!");
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
  public Map<String, Object> getData(Map<String, List<String>> map) throws Exception {
    logger.info("[" + this.time + "] Got a request from MOSAIK to provide data!");

    if (this.counter == 1 || this.counter == 2) {
      ExtResultContainer results = dataQueueSimonaToMosaik.takeData();
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
      ExtEntityMapping mapping,
      DataQueueExtSimulationExtSimulator<ExtInputDataContainer> dataQueueExtCoSimulatorToSimonaApi,
      DataQueueExtSimulationExtSimulator<ExtResultContainer> dataQueueSimonaApiToExtCoSimulator) {
    logger.info("Set the mapping and the data queues between SIMONA and MOSAIK!");
    this.dataQueueSimonaToMosaik = dataQueueSimonaApiToExtCoSimulator;
    this.dataQueueMosaikToSimona = dataQueueExtCoSimulatorToSimonaApi;

    this.simonaEmAgents = mapping.getExtId2UuidMapping(EXT_EM_INPUT).keySet();
    this.simonaResultOutputEntities = mapping.getExtId2UuidMapping(EXT_GRID_RESULT).keySet();
  }

  protected double[] getFlexOptions(ExtResultContainer results, String id) {
    Map<String, ResultEntity> resultMap = results.getResults();
    if (resultMap.get(id) instanceof FlexOptionsResult flexOptionsResult) {
      return getFlexMinRefMaxFlexOptions(flexOptionsResult);
    } else {
      throw new IllegalArgumentException("FlexOptions is only available for FlexOptionsResult's!");
    }
  }

  protected double[] getFlexMinRefMaxFlexOptions(FlexOptionsResult flexOptionsResult) {
    return new double[] {
      flexOptionsResult.getpMin().getValue().doubleValue(),
      flexOptionsResult.getpRef().getValue().doubleValue(),
      flexOptionsResult.getpMax().getValue().doubleValue()
    };
  }

  public Map<String, Object> createSimosaikOutputMap(
      Map<String, List<String>> mosaikRequestedAttributes, ExtResultContainer simonaResults) {
    Map<String, Object> outputMap = new HashMap<>();
    simonaResults
        .getResults()
        .forEach(
            (id, result) -> {
              List<String> attrs = mosaikRequestedAttributes.get(id);
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
              outputMap.put(id, values);
            });
    return outputMap;
  }

  public Map<String, Object> createSimosaikOutputMapFromRequestedAttributes(
      Map<String, List<String>> mosaikRequestedAttributes, ExtResultContainer simonaResults) {
    Map<String, Object> outputMap = new HashMap<>();
    mosaikRequestedAttributes.forEach(
        (id, attrs) -> {
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
          outputMap.put(id, values);
        });
    return outputMap;
  }

  protected void addFlexOptions(
      ExtResultContainer results, String id, String attr, Map<String, Object> outputMap) {
    switch (attr) {
      case FLEX_OPTION_P_MIN -> outputMap.put(attr, getFlexOptions(results, id)[0]);
      case FLEX_OPTION_P_REF -> outputMap.put(attr, getFlexOptions(results, id)[1]);
      case FLEX_OPTION_P_MAX -> outputMap.put(attr, getFlexOptions(results, id)[2]);
    }
  }
}
