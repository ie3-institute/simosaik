/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simpleextsim;

import static edu.ie3.simpleextsim.grid.SimpleExtSimulationGridData.*;

import edu.ie3.datamodel.models.result.ModelResultEntity;
import edu.ie3.datamodel.models.result.NodeResult;
import edu.ie3.datamodel.models.value.Value;
import edu.ie3.simona.api.data.ExtDataConnection;
import edu.ie3.simona.api.data.primarydata.ExtPrimaryDataConnection;
import edu.ie3.simona.api.data.results.ExtResultDataConnection;
import edu.ie3.simona.api.simulation.ExtCoSimulation;
import java.util.*;

/**
 * Simple example for an external simulation, that calculates set points for two em agents, and gets
 * power for two pv plants from SIMONA.
 */
public class SimpleExtSimulationWithPowerFlow extends ExtCoSimulation {

  private final ExtPrimaryDataConnection extPrimaryData;
  private final ExtResultDataConnection extResultData;

  public SimpleExtSimulationWithPowerFlow() {
    super(SimpleExtSimulationWithPowerFlow.class.getName());
    this.extPrimaryData =
        new ExtPrimaryDataConnection(
            Map.of(
                LOAD_1, LOAD_1_UUID,
                LOAD_2, LOAD_2_UUID));
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

    Map<UUID, Value> extSimData = new HashMap<>();

    long phase = (tick / 2000) % 4;

    long nextTick = tick + deltaT;

    extSimData.put(LOAD_MODEL_1.getUuid(), LOAD_MODEL_1.getPower(phase));

    extSimData.put(LOAD_MODEL_2.getUuid(), LOAD_MODEL_2.getPower(phase));

    // send primary data for load1 and load2 to SIMONA
    extPrimaryData.providePrimaryData(tick, extSimData, Optional.of(nextTick));

    log.info(
        "[{}] Provide Primary Data to SIMONA for {} ({}) with {} and {} ({}) with {}.",
        tick,
        LOAD_MODEL_1.getId(),
        LOAD_MODEL_1.getUuid(),
        LOAD_MODEL_1.getPower(phase),
        LOAD_MODEL_2.getId(),
        LOAD_MODEL_2.getUuid(),
        LOAD_MODEL_2.getPower(phase));

    log.debug("[{}] Request Results from SIMONA!", tick);

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
