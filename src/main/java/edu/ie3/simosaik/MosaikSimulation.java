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
import edu.ie3.simona.api.simulation.mapping.ExtEntityMappingCsvSource;
import edu.ie3.simosaik.config.ArgsParser;
import edu.ie3.simosaik.config.SimosaikConfig;
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

  private final Optional<ExtPrimaryDataConnection> extPrimaryDataConnection;
  private final Optional<ExtEmDataConnection> extEmDataConnection;
  private final Optional<ExtResultDataConnection> extResultDataConnection;

  private final String mosaikIP;

  private MosaikSimulator mosaikSimulator; // extends Simulator in Mosaik

  private boolean startedMosasik = false;

  private final ExtEntityMapping mapping;

  public MosaikSimulation(ArgsParser.Arguments arguments) {
    super("MosaikSimulation", "MosaikSimulator");

    this.mosaikIP = arguments.mosaikIP();
    SimosaikConfig config = arguments.simosaikConfig();

    try {
      this.mapping = ExtEntityMappingCsvSource.createExtEntityMapping(arguments.mappingPath());
    } catch (SourceException e) {
      throw new RuntimeException(e);
    }

    this.extPrimaryDataConnection =
        Optional.ofNullable(
            config.simosaik.receive.primary
                ? new ExtPrimaryDataConnection(
                    mapping.getExtId2UuidMapping(DataType.EXT_PRIMARY_INPUT))
                : null);

    this.extEmDataConnection =
        Optional.ofNullable(
            config.simosaik.receive.em
                ? new ExtEmDataConnection(mapping.getExtId2UuidMapping(DataType.EXT_EM_INPUT))
                : null);

    this.extResultDataConnection =
        Optional.ofNullable(
            config.simosaik.sendResults
                ? new ExtResultDataConnection(
                    mapping.getExtUuid2IdMapping(DataType.EXT_RESULT_PARTICIPANT),
                    mapping.getExtUuid2IdMapping(DataType.EXT_RESULT_GRID))
                : null);
  }

  @Override
  public Set<ExtDataConnection> getDataConnections() {
    return Stream.of(extPrimaryDataConnection, extEmDataConnection, extResultDataConnection)
        .flatMap(Optional::stream)
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

      extPrimaryDataConnection.ifPresent(con -> sendPrimaryDataToSimona(con, tick, data, nextTick));
      extEmDataConnection.ifPresent(con -> sendEmDataToSimona(con, tick, data, nextTick));

      if (extResultDataConnection.isPresent()) {
        sendDataToExt(extResultDataConnection.get(), tick, nextTick);
      }

      return nextTick;
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
