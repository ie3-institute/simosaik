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

  private final ExtPrimaryDataConnection extPrimaryDataConnection;
  private final ExtEmDataConnection extEmDataConnection;
  private final ExtResultDataConnection extResultDataConnection;

  private final String mosaikIP;

  private MosaikSimulator mosaikSimulator; // extends Simulator in Mosaik

  private boolean startedMosasik = false;

  private final ExtEntityMapping mapping;

  public MosaikSimulation(ArgsParser.Arguments arguments) {
    super("MosaikSimulation", "MosaikSimulator");

    this.mosaikIP = arguments.mosaikIP();

    try {
      this.mapping = ExtEntityMappingSource.fromFile(arguments.mappingPath());
    } catch (SourceException e) {
      throw new RuntimeException(e);
    }

    this.extPrimaryDataConnection = buildPrimaryConnection(mapping);
    this.extEmDataConnection = buildEmConnection(mapping);
    this.extResultDataConnection = buildResultConnection(mapping);
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
      this.mosaikSimulator = new MosaikSimulator((int) deltaT);
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

      Optional<Long> nextTick = Optional.of(tick + deltaT);

      ExtInputDataContainer inputData = dataQueueExtCoSimulatorToSimonaApi.takeData();
      Map<String, Value> data = inputData.getSimonaInputMap();

      sendPrimaryDataToSimona(extPrimaryDataConnection, tick, data, nextTick);
      sendEmDataToSimona(extEmDataConnection, tick, data, nextTick);
      sendDataToExt(extResultDataConnection, tick, nextTick);

      return nextTick;
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
