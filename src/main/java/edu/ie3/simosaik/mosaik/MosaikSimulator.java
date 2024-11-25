/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.mosaik;

import static edu.ie3.simona.api.simulation.mapping.DataType.EXT_PRIMARY_INPUT;
import static edu.ie3.simona.api.simulation.mapping.DataType.EXT_RESULT_GRID;

import de.offis.mosaik.api.SimProcess;
import de.offis.mosaik.api.Simulator;
import edu.ie3.simona.api.data.DataQueueExtSimulationExtSimulator;
import edu.ie3.simona.api.data.ExtInputDataContainer;
import edu.ie3.simona.api.data.results.ExtResultContainer;
import edu.ie3.simona.api.simulation.mapping.ExtEntityMapping;
import edu.ie3.simosaik.SimosaikUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** The mosaik simulator that exchanges information with mosaik. */
public class MosaikSimulator extends Simulator implements Meta {
  protected final Logger logger = SimProcess.logger;

  private final int stepSize;
  private final InputWrapperFunction wrapperFunction;
  private final OutputBuilderFunction outputBuilder;

  private DataQueueExtSimulationExtSimulator<ExtInputDataContainer> dataQueueMosaikToSimona;
  private DataQueueExtSimulationExtSimulator<ExtResultContainer> dataQueueSimonaToMosaik;

  protected String[] simonaPrimaryEntities;
  protected String[] simonaResultEntities;

  public MosaikSimulator(int stepSize) {
    this(
        stepSize,
        SimosaikUtils::createExtInputDataContainer,
        SimosaikUtils::createSimosaikOutputMap);
  }

  public MosaikSimulator(
      int stepSize, InputWrapperFunction wrapperFunction, OutputBuilderFunction outputBuilder) {
    super("SimonaPowerGrid");

    this.stepSize = stepSize;
    this.wrapperFunction = wrapperFunction;
    this.outputBuilder = outputBuilder;
  }

  @Override
  public Map<String, Object> init(String sid, Float timeResolution, Map<String, Object> simParams)
      throws Exception {
    return getMeta();
  }

  @Override
  public List<Map<String, Object>> create(int num, String model, Map<String, Object> modelParams)
      throws Exception {
    return switch (model) {
      case SIMONA_POWER_GRID_ENVIRONMENT -> {
        if (num != 1) {
          throwException(num, 1, model);
        }
        yield buildMap(new String[] {model}, model);
      }
      case PRIMARY_INPUT_ENTITIES -> {
        int allowed = simonaPrimaryEntities.length;

        if (num != allowed) {
          throwException(num, allowed, model);
        }
        yield buildMap(simonaPrimaryEntities, model);
      }
      case RESULT_OUTPUT_ENTITIES -> {
        int allowed = simonaResultEntities.length;

        if (num != allowed) {
          throwException(num, allowed, model);
        }
        yield buildMap(simonaResultEntities, model);
      }
      default ->
          throw new IllegalArgumentException(
              "The model " + model + " is not supported by SimonaSimulator.");
    };
  }

  @Override
  public final long step(long time, Map<String, Object> inputs, long maxAdvance) throws Exception {
    long nextTick = time + this.stepSize;
    try {
      logger.info("Got inputs from MOSAIK for tick = " + time);
      ExtInputDataContainer extDataForSimona = wrapperFunction.apply(time, inputs, nextTick);
      logger.info("Converted input for SIMONA! Now try to send it to SIMONA!");
      dataQueueMosaikToSimona.queueData(extDataForSimona);
      logger.info("Sent converted input for tick " + time + " to SIMONA!");
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    return nextTick;
  }

  @Override
  public final Map<String, Object> getData(Map<String, List<String>> map) throws Exception {
    logger.info("Got a request from MOSAIK to provide data!");
    ExtResultContainer results = dataQueueSimonaToMosaik.takeData();
    logger.info("Got results from SIMONA for MOSAIK!");
    Map<String, Object> data = outputBuilder.apply(map, results);
    logger.info("Converted results for MOSAIK! Now send it to MOSAIK!");
    return data;
  }

  public void setConnectionToSimonaApi(
      ExtEntityMapping mapping,
      DataQueueExtSimulationExtSimulator<ExtInputDataContainer> dataQueueExtCoSimulatorToSimonaApi,
      DataQueueExtSimulationExtSimulator<ExtResultContainer> dataQueueSimonaApiToExtCoSimulator) {
    this.simonaPrimaryEntities =
        mapping.getExtId2UuidMapping(EXT_PRIMARY_INPUT).keySet().toArray(new String[0]);
    this.simonaResultEntities =
        mapping.getExtId2UuidMapping(EXT_RESULT_GRID).keySet().toArray(new String[0]);
    this.dataQueueSimonaToMosaik = dataQueueSimonaApiToExtCoSimulator;
    this.dataQueueMosaikToSimona = dataQueueExtCoSimulatorToSimonaApi;
  }

  protected List<Map<String, Object>> buildMap(String[] simonaEntities, String model) {
    List<Map<String, Object>> entities = new ArrayList<>();

    for (String simonaEntity : simonaEntities) {
      Map<String, Object> entity = new HashMap<>();
      entity.put("eid", simonaEntity);
      entity.put("type", model);
      entities.add(entity);
    }

    return entities;
  }

  protected void throwException(int num, int allowed, String type) {
    throw new IllegalArgumentException(
        "Requested number ("
            + num
            + ") of "
            + type
            + " entities is not possible. Allowed: "
            + allowed);
  }
}
