/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.simosaikElectrolyzer;

import edu.ie3.ArgsParser;
import edu.ie3.datamodel.models.input.system.LoadInput;
import edu.ie3.datamodel.models.result.ModelResultEntity;
import edu.ie3.simona.api.data.ExtDataConnection;
import edu.ie3.simona.api.data.ExtInputDataContainer;
import edu.ie3.simona.api.data.primarydata.ExtPrimaryDataConnection;
import edu.ie3.simona.api.data.results.ExtResultContainer;
import edu.ie3.simona.api.data.results.ExtResultDataConnection;
import edu.ie3.simona.api.simulation.ExtSimulation;
import edu.ie3.simona.api.simulation.mapping.ExtEntityEntry;
import edu.ie3.simona.api.simulation.mapping.ExtEntityMapping;
import edu.ie3.simona.api.simulation.mapping.ExtEntityMappingCsvSource;
import edu.ie3.simosaik.SimosaikUtils;
import edu.ie3.simosaik.data.MosaikPrimaryDataFactory;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MosaikElectrolyzerSimulation extends ExtSimulation {

  private final Logger log = LoggerFactory.getLogger("MosaikSimulation");
  private final ExtPrimaryDataConnection extPrimaryData;
  private final ExtResultDataConnection extResultData;
  private final long deltaT = 900L;
  private final String mosaikIP;

  private SimonaElectrolyzerSimulator simonaElectrolyzerSimulator; // extends Simulator in Mosaik

  private boolean startedMosasik = false;

  private final ExtEntityMapping mapping;

  public MosaikElectrolyzerSimulation(String[] mainArgs) {
    ArgsParser.Arguments arguments = ArgsParser.parse(mainArgs);

    this.mapping = ExtEntityMappingCsvSource.createExtEntityMapping(arguments.mappingPath());

    this.extPrimaryData =
        new ExtPrimaryDataConnection(
            new MosaikPrimaryDataFactory(),
            mapping.getExtId2UuidMapping(ExtEntityEntry.EXT_INPUT),
            List.of(LoadInput.class));
    this.extResultData =
        new ExtResultDataConnection(
            mapping.getExtUuid2IdMapping(ExtEntityEntry.EXT_RESULT_PARTICIPANT),
            mapping.getExtUuid2IdMapping(ExtEntityEntry.EXT_RESULT_GRID));

    this.mosaikIP = arguments.mosaikIP();
  }

  @Override
  public List<ExtDataConnection> getDataConnections() {
    return List.of(extPrimaryData, extResultData);
  }

  @Override
  protected Long initialize() {
    log.info(
        "+++++++++++++++++++++++++++ initialization of the external simulation +++++++++++++++++++++++++++");
    if (!startedMosasik) {
      startedMosasik = true;
      this.simonaElectrolyzerSimulator = new SimonaElectrolyzerSimulator();
      simonaElectrolyzerSimulator.setMapping(mapping);
      SimosaikUtils.startMosaikSimulation(simonaElectrolyzerSimulator, mosaikIP);
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
    try {
      ExtInputDataContainer rawPrimaryData =
          simonaElectrolyzerSimulator.dataQueueMosaikToSimona.takeData();
      log.debug("Received Primary Data from Mosaik = " + rawPrimaryData);

      extPrimaryData.providePrimaryData(
          tick,
          extPrimaryData.createExtPrimaryDataMap(rawPrimaryData),
          rawPrimaryData.getMaybeNextTick());
      log.info("Provided Primary Data to SIMONA");
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    long nextTick = tick + deltaT;
    try {
      log.info("Request Results from SIMONA!");
      Map<String, ModelResultEntity> resultsToBeSend = extResultData.requestResults(tick);
      log.info("Received results from SIMONA! Now convert them and send them to Mosaik!");

      simonaElectrolyzerSimulator.dataQueueSimonaToMosaik.queueData(
          new ExtResultContainer(tick, resultsToBeSend));
      log.info(
          "***** External simulation for tick "
              + tick
              + " completed. Next simulation tick = "
              + nextTick
              + " *****");
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    return Optional.of(nextTick);
  }
}
