/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simpleextsim;

import static edu.ie3.simpleextsim.grid.SimpleExtSimulationGridData.*;

import edu.ie3.datamodel.models.result.ModelResultEntity;
import edu.ie3.datamodel.models.result.system.EmResult;
import edu.ie3.datamodel.models.result.system.PvResult;
import edu.ie3.simona.api.data.ExtDataConnection;
import edu.ie3.simona.api.data.ExtInputDataContainer;
import edu.ie3.simona.api.data.ExtInputDataValue;
import edu.ie3.simona.api.data.em.ExtEmDataConnection;
import edu.ie3.simona.api.data.results.ExtResultDataConnection;
import edu.ie3.simona.api.simulation.ExtSimulation;
import edu.ie3.simpleextsim.data.SimpleEmDataFactory;
import edu.ie3.simpleextsim.data.SimpleExtSimValue;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple example for an external simulation, that calculates set points for two em agents, and gets
 * power for two pv plants from SIMONA.
 */
public class SimpleExtSimulationWithEm extends ExtSimulation {

  private final Logger log = LoggerFactory.getLogger(SimpleExtSimulationWithEm.class);

  private final ExtEmDataConnection extEmData;
  private final ExtResultDataConnection extResultData;

  private final long deltaT = 900L;

  public SimpleExtSimulationWithEm() {
    this.extEmData =
        new ExtEmDataConnection(
            new SimpleEmDataFactory(),
            Map.of(
                EM_3, EM_3_UUID,
                EM_4, EM_4_UUID));
    this.extResultData =
        new ExtResultDataConnection(
            Map.of(
                PV_1_UUID, PV_1,
                PV_2_UUID, PV_2),
            Collections.emptyMap());
  }

  @Override
  public List<ExtDataConnection> getDataConnections() {
    return List.of(extEmData, extResultData);
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

    extSimData.put(
        EM_CONTROLLER_3.getId(), new SimpleExtSimValue(EM_CONTROLLER_3.getSetPoint(phase)));

    extSimData.put(
        EM_CONTROLLER_4.getId(), new SimpleExtSimValue(EM_CONTROLLER_4.getSetPoint(phase)));

    ExtInputDataContainer extInputDataPackage =
        new ExtInputDataContainer(tick, extSimData, Optional.of(nextTick));

    extEmData.provideEmData(
        tick, extEmData.createExtEmDataMap(extInputDataPackage), Optional.of(nextTick));

    log.info(
        "["
            + tick
            + "] Provide EmData to SIMONA for "
            + EM_CONTROLLER_3.getId()
            + " ("
            + EM_CONTROLLER_3.getUuid()
            + ") with "
            + EM_CONTROLLER_3.getSetPoint(phase)
            + " and "
            + EM_CONTROLLER_4.getId()
            + " ("
            + EM_CONTROLLER_4.getUuid()
            + ") with "
            + EM_CONTROLLER_4.getSetPoint(phase)
            + ".");

    log.debug("[" + tick + "] Request Results from SIMONA!");

    try {
      Map<String, ModelResultEntity> resultsFromSimona = extResultData.requestResults(tick);

      log.info("[" + tick + "] Received results from SIMONA for " + resultsFromSimona.keySet());

      resultsFromSimona.forEach(
          (id, result) -> {
            if (result instanceof PvResult spResult) {
              if (PV_1.equals(id)) {
                log.debug(
                    "Tick "
                        + tick
                        + ": SIMONA calculated the power of pv1 ("
                        + PV_1
                        + ") with "
                        + spResult);
                log.info(
                    "["
                        + tick
                        + "] SIMONA calculated the power of "
                        + PV_1
                        + " with p = "
                        + spResult.getP());
              } else if (PV_2.equals(id)) {
                log.debug(
                    "Tick "
                        + tick
                        + ": SIMONA calculated the power of pv2 ("
                        + PV_2
                        + ") with "
                        + spResult);
                log.info(
                    "["
                        + tick
                        + "] SIMONA calculated the power of "
                        + PV_2
                        + " with p = "
                        + spResult.getP());
              } else {
                log.error(
                    "Received a result from SIMONA for uuid {}, but I don't expect this entity!",
                    id);
              }
            } else if (result instanceof EmResult emResult) {
              if (EM_3.equals(id)) {
                log.debug(
                    "Tick "
                        + tick
                        + ": SIMONA calculated the power of em3 ("
                        + EM_3
                        + ") with "
                        + emResult);
                log.info(
                    "["
                        + tick
                        + "] SIMONA calculated the power of "
                        + EM_3
                        + " with p = "
                        + emResult.getP());
              } else if (EM_4.equals(id)) {
                log.debug(
                    "Tick "
                        + tick
                        + ": SIMONA calculated the power of em4 ("
                        + EM_4
                        + ") with "
                        + emResult);
                log.info(
                    "["
                        + tick
                        + "] SIMONA calculated the power of "
                        + EM_4
                        + " with p = "
                        + emResult.getP());
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
