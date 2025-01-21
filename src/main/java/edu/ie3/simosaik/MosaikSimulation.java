/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik;

import edu.ie3.datamodel.exceptions.SourceException;
import edu.ie3.simona.api.data.ExtDataConnection;
import edu.ie3.simona.api.data.em.ExtEmDataConnection;
import edu.ie3.simona.api.data.primarydata.ExtPrimaryDataConnection;
import edu.ie3.simona.api.data.results.ExtResultDataConnection;
import edu.ie3.simona.api.simulation.ExtCoSimulation;
import edu.ie3.simona.api.simulation.mapping.ExtEntityMapping;
import edu.ie3.simona.api.simulation.mapping.ExtEntityMappingSource;
import edu.ie3.simosaik.config.SimosaikConfig;
import edu.ie3.simosaik.mosaik.MosaikSimulator;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    this.extPrimaryDataConnection = buildPrimaryConnection(mapping, log);
    this.extEmDataConnection = buildEmConnection(mapping, log);
    this.extResultDataConnection = buildResultConnection(mapping, log);
  }

  @Override
  public Set<ExtDataConnection> getDataConnections() {
    return toSet(extPrimaryDataConnection, extEmDataConnection, extResultDataConnection);
  }

  @Override
  protected Long initialize() {
    log.info(
        "+++++++++++++++++++++++++++ initialization of the external simulation +++++++++++++++++++++++++++");
    if (!startedMosasik) {
      startedMosasik = true;
      mosaikSimulator.setDataConnectionToAPI(
          dataQueueExtCoSimulatorToSimonaApi, dataQueueSimonaApiToExtCoSimulator);
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

      Optional<Long> maybeNextTickSimona =
          sendDataToSIMONA(Set.of(extPrimaryDataConnection, extEmDataConnection), log);

      Optional<Long> maybeNextTickExt = Optional.of(tick + stepSize);
      if (extResultDataConnection.isPresent()) {
        sendResultDataToExt(extResultDataConnection.get(), tick, maybeNextTickExt, log);
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
