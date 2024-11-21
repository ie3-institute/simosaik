/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik;

import edu.ie3.simona.api.data.ExtDataConnection;
import edu.ie3.simona.api.data.em.ExtEmDataConnection;
import edu.ie3.simona.api.data.primarydata.ExtPrimaryDataConnection;
import edu.ie3.simona.api.data.results.ExtResultDataConnection;
import edu.ie3.simona.api.simulation.ExtCoSimulation;
import edu.ie3.simona.api.simulation.mapping.ExtEntityEntry;
import edu.ie3.simona.api.simulation.mapping.ExtEntityMapping;
import edu.ie3.simona.api.simulation.mapping.ExtEntityMappingCsvSource;
import edu.ie3.simosaik.config.ArgsParser;
import edu.ie3.simosaik.config.SimosaikConfig;
import edu.ie3.simosaik.mosaik.MosaikSimulator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class MosaikSimulation extends ExtCoSimulation {

  private final Optional<ExtPrimaryDataConnection> extPrimaryDataConnection;
  private final Optional<ExtEmDataConnection> extEmDataConnection;
  private final Optional<ExtResultDataConnection> extResultDataConnection;

  private final String mosaikIP;
  private final SimosaikConfig config;

  private MosaikSimulator mosaikSimulator; // extends Simulator in Mosaik

  private boolean startedMosasik = false;

  private final ExtEntityMapping mapping;

  public MosaikSimulation(String[] mainArgs) {
    super("MosaikSimulation");
    ArgsParser.Arguments arguments = ArgsParser.parse(mainArgs);

    this.mosaikIP = arguments.mosaikIP();
    this.config = arguments.simosaikConfig();

    this.mapping = ExtEntityMappingCsvSource.createExtEntityMapping(arguments.mappingPath());

    this.extPrimaryDataConnection = primaryDataConnection(config);
    this.extEmDataConnection = emDataConnection(config);
    this.extResultDataConnection = resultDataConnection(config);
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<ExtDataConnection> getDataConnections() {
    return (List<ExtDataConnection>)
        Stream.of(extPrimaryDataConnection, extEmDataConnection, extResultDataConnection)
            .flatMap(Optional::stream)
            .toList();
  }

  @Override
  protected Long initialize() {
    log.info(
        "+++++++++++++++++++++++++++ initialization of the external simulation +++++++++++++++++++++++++++");
    if (!startedMosasik) {
      startedMosasik = true;
      this.mosaikSimulator = new MosaikSimulator();
      mosaikSimulator.setMapping(mapping);
      mosaikSimulator.setQueues(
          dataQueueExtCoSimulatorToSimonaApi, dataQueueSimonaApiToExtCoSimulator);
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
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    long nextTick = tick + deltaT;

    extPrimaryDataConnection.ifPresentOrElse(
        connection -> sendPrimaryDataToSimona(connection, tick),
        () -> log.info("No external primary data connection present!"));

    extEmDataConnection.ifPresent(connection -> sendEmDataToSimona(connection, tick, nextTick));

    extResultDataConnection.ifPresentOrElse(
        connection -> sendResultsToExtCoSimulator(connection, tick, nextTick),
        () -> log.info("No external result data connection present!"));

    return Optional.of(nextTick);
  }

  private Optional<ExtPrimaryDataConnection> primaryDataConnection(SimosaikConfig config) {
    return config.simosaik.receive.primary
        ? Optional.of(
            new ExtPrimaryDataConnection(
                mapping.getExtId2UuidMapping(ExtEntityEntry.EXT_PRIMARY_INPUT)))
        : Optional.empty();
  }

  private Optional<ExtEmDataConnection> emDataConnection(SimosaikConfig config) {
    return config.simosaik.receive.em
        ? Optional.of(
            new ExtEmDataConnection(mapping.getExtId2UuidMapping(ExtEntityEntry.EXT_EM_INPUT)))
        : Optional.empty();
  }

  private Optional<ExtResultDataConnection> resultDataConnection(SimosaikConfig config) {
    return config.simosaik.sendResults
        ? Optional.of(
            new ExtResultDataConnection(
                mapping.getExtUuid2IdMapping(ExtEntityEntry.EXT_RESULT_PARTICIPANT),
                mapping.getExtUuid2IdMapping(ExtEntityEntry.EXT_RESULT_GRID)))
        : Optional.empty();
  }
}
