/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik;

import edu.ie3.datamodel.exceptions.SourceException;
import edu.ie3.simona.api.simulation.ExtCoSimulation;
import edu.ie3.simona.api.simulation.mapping.ExtEntityMapping;
import edu.ie3.simona.api.simulation.mapping.ExtEntityMappingSource;
import edu.ie3.simosaik.utils.SimosaikUtils;
import java.nio.file.Path;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple external mosaik simulation. This external simulation is capable to provide SIMONA with
 * primary and em data. Also, this simulation can send result data back to mosaik.
 */
public abstract class MosaikSimulation extends ExtCoSimulation {

  protected static final Logger log = LoggerFactory.getLogger(MosaikSimulation.class);

  private final String mosaikIP;
  protected final MosaikSimulator mosaikSimulator; // extends Simulator in Mosaik

  protected final int stepSize;
  private boolean startedMosasik = false;

  protected final ExtEntityMapping mapping;

  public MosaikSimulation(String mosaikIP, Path mappingPath, MosaikSimulator simulator) {
    this("MosaikSimulation", mosaikIP, mappingPath, simulator);
  }

  public MosaikSimulation(
      String name, String mosaikIP, Path mappingPath, MosaikSimulator simulator) {
    super(name, simulator.getSimName());

    this.mosaikSimulator = simulator;
    this.stepSize = simulator.stepSize;
    this.mosaikIP = mosaikIP;

    try {
      this.mapping = ExtEntityMappingSource.fromFile(mappingPath);
    } catch (SourceException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected final Long initialize() {
    log.info(
        "+++++++++++++++++++++++++++ initialization of the external simulation +++++++++++++++++++++++++++");
    if (!startedMosasik) {
      startedMosasik = true;
      mosaikSimulator.setConnectionToSimonaApi(mapping, queueToSimona, queueToExt);
      SimosaikUtils.startMosaikSimulation(mosaikSimulator, mosaikIP);
    }
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

  protected abstract Optional<Long> activity(long tick, long nextTick) throws InterruptedException;
}
