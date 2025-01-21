/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik;

import edu.ie3.datamodel.exceptions.SourceException;
import edu.ie3.datamodel.models.value.Value;
import edu.ie3.simona.api.data.ExtDataConnection;
import edu.ie3.simona.api.data.ExtInputDataContainer;
import edu.ie3.simona.api.data.em.ExtEmDataConnection;
import edu.ie3.simona.api.data.primarydata.ExtPrimaryDataConnection;
import edu.ie3.simona.api.data.results.ExtResultDataConnection;
import edu.ie3.simona.api.simulation.ExtCoSimulation;
import edu.ie3.simona.api.simulation.mapping.DataType;
import edu.ie3.simona.api.simulation.mapping.ExtEntityMapping;
import edu.ie3.simona.api.simulation.mapping.ExtEntityMappingSource;
import edu.ie3.simosaik.config.SimosaikConfig;
import edu.ie3.simosaik.mosaik.MosaikSimulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Simple external mosaik simulation. This external simulation is capable to provide SIMONA with
 * primary and em data. Also, this simulation can send result data back to mosaik.
 */
public final class MosaikSimulation extends ExtCoSimulation {

  private static final Logger log = LoggerFactory.getLogger(MosaikSimulation.class);

  private final Optional<ExtPrimaryDataConnection> extPrimaryDataConnection;
  private final Optional<ExtEmDataConnection> extEmDataConnection;
  private final Optional<ExtResultDataConnection> extResultDataConnection;

  private final String mosaikIP;
  private final MosaikSimulator mosaikSimulator; // extends Simulator in Mosaik

  private final int stepSize;
  private boolean startedMosasik = false;

  private final ExtEntityMapping mapping;

  public MosaikSimulation(String mosaikIP, SimosaikConfig config, MosaikSimulator simulator) {
    super("MosaikSimulation", "MosaikSimulator");

    this.mosaikSimulator = simulator;
    this.stepSize = simulator.stepSize;
    this.mosaikIP = mosaikIP;

    try {
      this.mapping = ExtEntityMappingSource.fromFile(config.mappingPath);
    } catch (SourceException e) {
      throw new RuntimeException(e);
    }

    // set up connections
    if (!mapping.getExtId2UuidMapping(DataType.EXT_PRIMARY_INPUT).isEmpty()) {
      this.extPrimaryDataConnection = Optional.of(buildPrimaryConnection(mapping, log));
    } else {
      this.extPrimaryDataConnection = Optional.empty();
    }

    if (!mapping.getExtId2UuidMapping(DataType.EXT_EM_INPUT).isEmpty()) {
      this.extEmDataConnection = Optional.of(buildEmConnection(mapping, log));
    } else {
      this.extEmDataConnection = Optional.empty();
    }

    if (!mapping.getExtId2UuidMapping(DataType.EXT_GRID_RESULT).isEmpty() || !mapping.getExtId2UuidMapping(DataType.EXT_PARTICIPANT_RESULT).isEmpty()) {
      this.extResultDataConnection = Optional.of(buildResultConnection(mapping, log));
    } else {
      this.extResultDataConnection = Optional.empty();
    }
  }

  @Override
  public Set<ExtDataConnection> getDataConnections() {
    return Stream.of(extPrimaryDataConnection, extEmDataConnection, extResultDataConnection)
            .filter(Optional::isPresent)
            .map(Optional::get)
        .collect(Collectors.toSet());
  }

  @Override
  protected Long initialize() {
    log.info(
        "+++++++++++++++++++++++++++ initialization of the external simulation +++++++++++++++++++++++++++");
    if (!startedMosasik) {
      startedMosasik = true;
      mosaikSimulator.setDataConnectionToAPI(dataQueueExtCoSimulatorToSimonaApi, dataQueueSimonaApiToExtCoSimulator);
      mosaikSimulator.setMapping(mapping);
      SimosaikUtils.startMosaikSimulation(mosaikSimulator, mosaikIP);
    }
    log.info(
        "+++++++++++++++++++++++++++ initialization of the external simulation completed +++++++++++++++++++++++++++");
    return 0L;
  }

  @Override
  protected Optional<Long> doActivity(long tick) {
    log.info(
        "+++++++++++++++++++++++++++ Activities in External simulation: Tick {} has been triggered. +++++++++++++++++++++++++++",
        tick);
    try {
      Thread.sleep(500);

      ExtInputDataContainer inputData = dataQueueExtCoSimulatorToSimonaApi.takeData();
      Map<String, Value> data = inputData.getSimonaInputMap();

      Optional<Long> maybeNextTickSimona = inputData.getMaybeNextTick();
      extPrimaryDataConnection.ifPresent(con -> sendPrimaryDataToSimona(con, tick, data, maybeNextTickSimona, log));
      extEmDataConnection.ifPresent(con -> sendEmDataToSimona(con, tick, data, maybeNextTickSimona, log));


      Optional<Long> maybeNextTickExt = Optional.of(tick + stepSize);
      if (extResultDataConnection.isPresent()) {
        sendDataToExt(extResultDataConnection.get(), tick, maybeNextTickExt, log);
      }

      if (maybeNextTickSimona.isPresent() && maybeNextTickSimona.get() < maybeNextTickExt.get()) {
        return maybeNextTickSimona;
      } else {
        return maybeNextTickExt;
      }
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
