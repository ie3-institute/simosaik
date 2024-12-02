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
import edu.ie3.simona.api.simulation.mapping.ExtEntityMapping;
import edu.ie3.simona.api.simulation.mapping.ExtEntityMappingSource;
import edu.ie3.simosaik.config.ArgsParser;
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

  private final ExtPrimaryDataConnection extPrimaryDataConnection;
  private final ExtEmDataConnection extEmDataConnection;
  private final ExtResultDataConnection extResultDataConnection;

  private final String mosaikIP;

  private MosaikSimulator mosaikSimulator; // extends Simulator in Mosaik

  private final int stepSize;

  private boolean startedMosasik = false;

  private final ExtEntityMapping mapping;

  public MosaikSimulation(ArgsParser.Arguments arguments) {
    this(900, arguments);
  }

  public MosaikSimulation(int stepSize, ArgsParser.Arguments arguments) {
    super("MosaikSimulation", "MosaikSimulator");

    this.stepSize = stepSize;
    this.mosaikIP = arguments.mosaikIP();

    try {
      this.mapping = ExtEntityMappingSource.fromFile(arguments.mappingPath());
    } catch (SourceException e) {
      throw new RuntimeException(e);
    }

    this.extPrimaryDataConnection = buildPrimaryConnection(mapping, log);
    this.extEmDataConnection = buildEmConnection(mapping, log);
    this.extResultDataConnection = buildResultConnection(mapping, log);
  }

  @Override
  public Set<ExtDataConnection> getDataConnections() {
    return Stream.of(extPrimaryDataConnection, extEmDataConnection, extResultDataConnection)
        .collect(Collectors.toSet());
  }

  @Override
  protected Long initialize() {
    log.info(
        "+++++++++++++++++++++++++++ initialization of the external simulation +++++++++++++++++++++++++++");
    if (!startedMosasik) {
      startedMosasik = true;
      this.mosaikSimulator = new MosaikSimulator(stepSize);
      mosaikSimulator.setConnectionToSimonaApi(
          mapping, dataQueueExtCoSimulatorToSimonaApi, dataQueueSimonaApiToExtCoSimulator);
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
      sendPrimaryDataToSimona(extPrimaryDataConnection, tick, data, maybeNextTickSimona, log);
      sendEmDataToSimona(extEmDataConnection, tick, data, maybeNextTickSimona, log);

      Optional<Long> maybeNextTickExt = Optional.of(tick + stepSize);
      sendDataToExt(extResultDataConnection, tick, maybeNextTickExt, log);

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
