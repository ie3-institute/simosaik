/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.flexibility;

import static edu.ie3.simona.api.simulation.mapping.DataType.EXT_EM_INPUT;
import static edu.ie3.simona.api.simulation.mapping.DataType.EXT_GRID_RESULT;
import static edu.ie3.simosaik.utils.FlexUtils.build;
import static edu.ie3.simosaik.utils.MosaikMessageParser.MosaikMessage;
import static edu.ie3.simosaik.utils.MosaikMessageParser.parse;
import static edu.ie3.simosaik.utils.SimosaikTranslation.*;

import edu.ie3.simona.api.data.ExtDataContainerQueue;
import edu.ie3.simona.api.data.datacontainer.ExtInputDataContainer;
import edu.ie3.simona.api.data.datacontainer.ExtResultContainer;
import edu.ie3.simona.api.simulation.mapping.ExtEntityMapping;
import edu.ie3.simosaik.MetaUtils;
import edu.ie3.simosaik.MetaUtils.ModelParams;
import edu.ie3.simosaik.MosaikSimulator;
import edu.ie3.simosaik.utils.FlexUtils;
import java.util.*;
import java.util.stream.Collectors;

// TODO: Refactor this class
public class FlexCommunicationSimulator extends MosaikSimulator {
  /** Agents who receive set points */
  protected Set<String> simonaEmAgents;

  /** Agents who send flex options for further calculations */
  protected Set<String> simonaResultOutputEntities;

  private final Set<MosaikMessage> cache = new HashSet<>();

  protected long time;

  public FlexCommunicationSimulator(int stepSize) {
    super("FlexCommunicationSimulator", stepSize);
  }

  @Override
  public Map<String, Object> init(String sid, Float timeResolution, Map<String, Object> simParams) {
    List<String> emUnits =
        List.of(
            FLEX_REQUEST,
            MOSAIK_ACTIVE_POWER,
            MOSAIK_REACTIVE_POWER,
            FLEX_OPTION_P_MIN,
            FLEX_OPTION_P_REF,
            FLEX_OPTION_P_MAX);

    return MetaUtils.createMetaWithPowerGrid(
        "hybrid", ModelParams.of(EM_AGENT_ENTITIES, emUnits, emUnits));
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
    if (this.time != time) {
      this.cache.clear();
    }

    this.time = time;
    logger.info("[" + this.time + "] Got inputs from MOSAIK!");
    long nextTick = time + this.stepSize;
    try {
      if (!inputs.isEmpty()) {
        logger.info(inputs.toString());

        List<MosaikMessage> mosaikMessages =
            parse(inputs).stream().filter(msg -> !cache.contains(msg)).collect(Collectors.toList());
        cache.addAll(mosaikMessages);

        logger.info("Parsed messages: " + mosaikMessages);

        ExtInputDataContainer container = build(time, nextTick, mosaikMessages);
        logger.info(container.flexOptionsString());


        // logger.info("Converted input for SIMONA! Now try to send it to SIMONA!");
        queueToSimona.queueData(container);
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

    logger.info(map.toString());

    ExtResultContainer results = queueToExt.takeAll();
    Map<String, Object> output = FlexUtils.createOutput(results, map);
    output.put("time", this.time);

    logger.info(output.toString());

    logger.info("[" + this.time + "] Converted results for MOSAIK! Now send it to MOSAIK!\n");
    return output;
  }

  public void setConnectionToSimonaApi(
      ExtEntityMapping mapping,
      ExtDataContainerQueue<ExtInputDataContainer> dataQueueExtCoSimulatorToSimonaApi,
      ExtDataContainerQueue<ExtResultContainer> dataQueueSimonaApiToExtCoSimulator) {
    logger.info("Set the mapping and the data queues between SIMONA and MOSAIK!");
    this.queueToExt = dataQueueSimonaApiToExtCoSimulator;
    this.queueToSimona = dataQueueExtCoSimulatorToSimonaApi;

    this.simonaEmAgents = mapping.getExtId2UuidMapping(EXT_EM_INPUT).keySet();
    this.simonaResultOutputEntities = mapping.getExtId2UuidMapping(EXT_GRID_RESULT).keySet();
  }
}
