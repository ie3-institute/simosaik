/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simpleextsim;

import static edu.ie3.simpleextsim.grid.SimpleExtSimulationGridData.*;

import edu.ie3.datamodel.models.input.EmInput;
import edu.ie3.datamodel.models.result.ModelResultEntity;
import edu.ie3.datamodel.models.result.NodeResult;
import edu.ie3.simona.api.data.ExtDataConnection;
import edu.ie3.simona.api.data.ExtInputDataContainer;
import edu.ie3.simona.api.data.ExtInputDataValue;
import edu.ie3.simona.api.data.primarydata.ExtPrimaryDataConnection;
import edu.ie3.simona.api.data.results.ExtResultDataConnection;
import edu.ie3.simona.api.simulation.ExtSimulation;
import edu.ie3.simpleextsim.data.SimpleExtSimValue;
import edu.ie3.simpleextsim.data.SimplePrimaryDataFactory;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple example for an external simulation, that calculates set points for two em agents, and gets
 * power for two pv plants from SIMONA.
 */
public class SimpleExtSimulationWithPowerFlow extends ExtSimulation {

  private final Logger log = LoggerFactory.getLogger("SimpleExtSimulationWithPowerFlow");

  private final ExtPrimaryDataConnection extPrimaryData;
  private final ExtResultDataConnection extResultData;

  private final long deltaT = 900L;

  public SimpleExtSimulationWithPowerFlow() {
    this.extPrimaryData =
        new ExtPrimaryDataConnection(
            new SimplePrimaryDataFactory(),
            Map.of(
                LOAD_1, LOAD_1_UUID,
                LOAD_2, LOAD_2_UUID),
            List.of(EmInput.class));
    this.extResultData =
        new ExtResultDataConnection(
            Collections.emptyMap(),
            Map.of(
                NODE_1_UUID, NODE_1,
                NODE_2_UUID, NODE_2));
  }

  @Override
  public List<ExtDataConnection> getDataConnections() {
    return List.of(extPrimaryData, extResultData);
  }

  @Override
  protected Long initialize() {
    log.info(
        "+++ Main args handed over to external simulation: {} +++", Arrays.toString(getMainArgs()));
    return 0L;
  }

  @Override
  protected Optional<Long> doActivity(long tick) {
    log.info(
        "+++++++++++++++++++++++++++ Activities in External simulation: Tick {} has been triggered. +++++++++++++++++++++++++++",
        tick);

    Map<String, ExtInputDataValue> extSimData = new HashMap<>();

    long phase = (tick / 2000) % 4;

    long nextTick = tick + deltaT;

    extSimData.put(LOAD_MODEL_1.getId(), new SimpleExtSimValue(LOAD_MODEL_1.getPower(phase)));

    extSimData.put(LOAD_MODEL_2.getId(), new SimpleExtSimValue(LOAD_MODEL_2.getPower(phase)));

    ExtInputDataContainer extInputDataPackage =
        new ExtInputDataContainer(tick, extSimData, Optional.of(nextTick));

    // send primary data for load1 and load2 to SIMONA

    extPrimaryData.providePrimaryData(
        tick,
        extPrimaryData.createExtPrimaryDataMap(extInputDataPackage),
        extInputDataPackage.getMaybeNextTick());

    log.info(
        "["
            + tick
            + "] Provide Primary Data to SIMONA for "
            + LOAD_MODEL_1.getId()
            + " ("
            + LOAD_MODEL_1.getUuid()
            + ") with "
            + LOAD_MODEL_1.getPower(phase)
            + " and "
            + LOAD_MODEL_2.getId()
            + " ("
            + LOAD_MODEL_2.getUuid()
            + ") with "
            + LOAD_MODEL_2.getPower(phase)
            + ".");

    log.debug("[" + tick + "] Request Results from SIMONA!");

    try {
      Map<String, ModelResultEntity> resultsFromSimona = extResultData.requestResults(tick);

      log.info("[" + tick + "] Received results from SIMONA for " + resultsFromSimona.keySet());

      resultsFromSimona.forEach(
          (id, result) -> {
            if (result instanceof NodeResult nodeResult) {
              if (NODE_1.equals(id)) {
                log.info(
                    "Tick "
                        + tick
                        + ": SIMONA calculated the power of "
                        + NODE_1
                        + " with "
                        + nodeResult);
                // log.info("[" + tick + "] SIMONA calculated the power of " + PV_1 + " with p = " +
                // nodeResult.getvMag());
              } else if (NODE_2.equals(id)) {
                log.info(
                    "Tick "
                        + tick
                        + ": SIMONA calculated the power of "
                        + NODE_2
                        + " with "
                        + nodeResult);
                // log.info("[" + tick + "] SIMONA calculated the power of " + PV_2 + " with p = " +
                // spResult.getP());
              } else {
                log.error(
                    "Received a result from SIMONA for uuid {}, but I don't expect this entity!",
                    id);
              }
            } else {
              log.error("Received wrong results from SIMONA!");
            }
          });
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    log.info(
        "***** External simulation for tick "
            + tick
            + " completed. Next simulation tick = "
            + nextTick
            + " *****");
    return Optional.of(nextTick);
  }
}
