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
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public final class MosaikSimulation extends ExtCoSimulation {

  private final ExtPrimaryDataConnection extPrimaryDataConnection;
  private final ExtEmDataConnection extEmDataConnection;
  private final ExtResultDataConnection extResultDataConnection;

  private final String mosaikIP;
  private final SimosaikConfig.Simosaik.Receive receive;
  private final boolean sendResults;

  private MosaikSimulator mosaikSimulator; // extends Simulator in Mosaik

  private boolean startedMosasik = false;

  private final ExtEntityMapping mapping;

  public MosaikSimulation(String[] mainArgs) {
    super("MosaikSimulation");
    ArgsParser.Arguments arguments = ArgsParser.parse(mainArgs);

    this.mosaikIP = arguments.mosaikIP();
    SimosaikConfig config = arguments.simosaikConfig();

    this.receive = config.simosaik.receive;
    this.sendResults = config.simosaik.sendResults;

    this.mapping = ExtEntityMappingCsvSource.createExtEntityMapping(arguments.mappingPath());

    this.extPrimaryDataConnection = primaryDataConnection(config);
    this.extEmDataConnection = emDataConnection(config);
    this.extResultDataConnection = resultDataConnection(config);
  }

  @Override
  public List<ExtDataConnection> getDataConnections() {
    return Stream.of(extPrimaryDataConnection, extEmDataConnection, extResultDataConnection)
        .filter(Objects::nonNull)
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

      if (!receive.primary) {
        log.warn(
            "No external primary data connection present! Therefore no primary data is send to SIMONA!");
      }

      if (receive.em) {
        log.warn("An external em data connection is present! This is not supported currently!");
      }

      if (!sendResults) {
        log.warn(
            "No external result data connection present! Therefore no result data is send to MOSAIK!");
      }
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

    if (extPrimaryDataConnection != null) {
      sendPrimaryDataToSimona(extPrimaryDataConnection, tick);
    }

    if (extEmDataConnection != null) {
      sendEmDataToSimona(extEmDataConnection, tick, nextTick);
    }

    if (extResultDataConnection != null) {
      sendResultsToExtCoSimulator(extResultDataConnection, tick, nextTick);
    }

    return Optional.of(nextTick);
  }

  private ExtPrimaryDataConnection primaryDataConnection(SimosaikConfig config) {
    return config.simosaik.receive.primary
        ? new ExtPrimaryDataConnection(
            mapping.getExtId2UuidMapping(ExtEntityEntry.EXT_PRIMARY_INPUT))
        : null;
  }

  private ExtEmDataConnection emDataConnection(SimosaikConfig config) {
    return config.simosaik.receive.em
        ? new ExtEmDataConnection(mapping.getExtId2UuidMapping(ExtEntityEntry.EXT_EM_INPUT))
        : null;
  }

  private ExtResultDataConnection resultDataConnection(SimosaikConfig config) {
    return config.simosaik.sendResults
        ? new ExtResultDataConnection(
            mapping.getExtUuid2IdMapping(ExtEntityEntry.EXT_RESULT_PARTICIPANT),
            mapping.getExtUuid2IdMapping(ExtEntityEntry.EXT_RESULT_GRID))
        : null;
  }
}
