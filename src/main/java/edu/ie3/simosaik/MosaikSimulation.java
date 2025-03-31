/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik;

import edu.ie3.simona.api.simulation.ExtCoSimulation;
import edu.ie3.simosaik.utils.SimosaikUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Simple external mosaik simulation. This external simulation is capable to provide SIMONA with
 * primary and em data. Also, this simulation can send result data back to mosaik.
 */
public abstract class MosaikSimulation extends ExtCoSimulation {

  protected static final Logger log = LoggerFactory.getLogger(MosaikSimulation.class);

  private final String mosaikIP;
  protected final MosaikSimulator mosaikSimulator; // extends Simulator in Mosaik

  protected final int stepSize;

  public MosaikSimulation(String mosaikIP, MosaikSimulator simulator) {
    this("MosaikSimulation", mosaikIP, simulator);
  }

  public MosaikSimulation(
      String name, String mosaikIP, MosaikSimulator simulator) {
    super(name, simulator.getSimName());

    this.mosaikSimulator = simulator;
    mosaikSimulator.setConnectionToSimonaApi(queueToSimona, queueToExt);
    SimosaikUtils.startMosaikSimulation(mosaikSimulator, mosaikIP);

    this.stepSize = simulator.stepSize;
    this.mosaikIP = mosaikIP;
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

  protected abstract Optional<Long> activity(long tick, long nextTick) throws InterruptedException;
}
