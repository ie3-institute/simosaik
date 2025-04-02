/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik;

import edu.ie3.datamodel.models.value.Value;
import edu.ie3.simona.api.data.ExtDataConnection;
import edu.ie3.simona.api.data.em.EmMode;
import edu.ie3.simona.api.data.em.ExtEmDataConnection;
import edu.ie3.simona.api.data.primarydata.ExtPrimaryDataConnection;
import edu.ie3.simona.api.data.results.ExtResultDataConnection;
import edu.ie3.simona.api.simulation.ExtCoSimulation;
import edu.ie3.simona.api.simulation.mapping.DataType;
import edu.ie3.simona.api.simulation.mapping.ExtEntityMapping;
import edu.ie3.simosaik.utils.SimosaikUtils;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple external mosaik simulation. This external simulation is capable to provide SIMONA with
 * primary and em data. Also, this simulation can send result data back to mosaik.
 */
public class MosaikSimulation extends ExtCoSimulation {

  protected static final Logger log = LoggerFactory.getLogger(MosaikSimulation.class);

  private final String mosaikIP;
  protected final int stepSize;

  protected final MosaikSimulator mosaikSimulator; // extends Simulator in Mosaik

  // connections
  private ExtPrimaryDataConnection extPrimaryDataConnection;
  private ExtEmDataConnection extEmDataConnection;
  private ExtResultDataConnection extResultDataConnection;

  public MosaikSimulation(String mosaikIP, MosaikSimulator simulator) {
    this("MosaikSimulation", mosaikIP, simulator);
  }

  public MosaikSimulation(String name, String mosaikIP, MosaikSimulator simulator) {
    super(name, simulator.getSimName());

    this.mosaikSimulator = simulator;
    mosaikSimulator.setConnectionToSimonaApi(queueToSimona, queueToExt);
    SimosaikUtils.startMosaikSimulation(mosaikSimulator, mosaikIP);

    this.stepSize = simulator.stepSize;
    this.mosaikIP = mosaikIP;

    try {
      ExtEntityMapping entityMapping = simulator.controlledQueue.take();

      // primary data connection
      Map<UUID, Class<Value>> primaryInput = SimosaikUtils.buildAssetsToValueClasses(entityMapping);

      this.extPrimaryDataConnection =
          !primaryInput.isEmpty() ? buildPrimaryConnection(primaryInput, log) : null;

      // em data connection
      Optional<Map.Entry<EmMode, List<UUID>>> mode = SimosaikUtils.findEmMode(entityMapping);

      if (mode.isPresent()) {
        Map.Entry<EmMode, List<UUID>> entry = mode.get();
        this.extEmDataConnection = buildEmConnection(entry.getValue(), entry.getKey(), log);
      } else {
        this.extEmDataConnection = null;
      }

      // result data connection
      Map<DataType, List<UUID>> resultInput = SimosaikUtils.buildResultMapping(entityMapping);
      this.extResultDataConnection =
          !resultInput.isEmpty() ? buildResultConnection(resultInput, log) : null;

    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Set<ExtDataConnection> getDataConnections() {
    return Stream.of(extPrimaryDataConnection, extEmDataConnection, extResultDataConnection)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  @Override
  protected final Long initialize() {
    log.info(
        "+++++++++++++++++++++++++++ initialization of the external simulation +++++++++++++++++++++++++++");
    log.info(
        "+++++++++++++++++++++++++++ initialization of the external simulation completed +++++++++++++++++++++++++++");
    return 0L;
  }

  @Override
  protected final Optional<Long> doActivity(long tick) {
    log.info(
        "+++++++++++++++++++++++++++ Activities in External simulation: Tick {} has been triggered. +++++++++++++++++++++++++++",
        tick);
    try {
      Thread.sleep(500);

      long nextTick = tick + stepSize;
      return activity(tick, nextTick);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  protected Optional<Long> activity(long tick, long nextTick) throws InterruptedException {
    Optional<Long> maybeNextTick = Optional.of(nextTick);

    if (extPrimaryDataConnection != null) {
      // sending primary data to SIMONA
      sendPrimaryDataToSimona(extPrimaryDataConnection, tick, maybeNextTick, log);
    }

    if (extEmDataConnection != null) {
      // using em connection
      switch (extEmDataConnection.mode) {
        case SET_POINT -> sendEmSetPointsToSimona(extEmDataConnection, tick, maybeNextTick, log);
        case EM_COMMUNICATION ->
            useFlexCommunication(extEmDataConnection, tick, maybeNextTick, log);
        default ->
            throw new IllegalStateException(
                "The mode '" + extEmDataConnection.mode + "' is currently not supported!");
      }
    }

    if (extResultDataConnection != null) {
      // sending results to mosaik
      sendResultToExt(extResultDataConnection, tick, maybeNextTick, log);
    }

    return maybeNextTick;
  }
}
