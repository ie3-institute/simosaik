/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik;

import edu.ie3.simona.api.ExtLinkInterface;
import edu.ie3.simona.api.data.SetupData;
import edu.ie3.simona.api.mapping.ExtEntityMapping;
import edu.ie3.simosaik.synchronization.Synchronizer;
import edu.ie3.simosaik.utils.SimosaikUtils;

import java.util.Optional;

public final class SimosaikExtLink implements ExtLinkInterface {

  private MosaikSimulation extSim;

  @Override
  public MosaikSimulation getExtSimulation() {
    return extSim;
  }

  @Override
  public void setup(SetupData data) {
    ArgsParser.Arguments arguments = ArgsParser.parse(data.mainArgs());

    String mosaikIP = arguments.mosaikIP();

    // initial mapping from grid container
    ExtEntityMapping mapping = new ExtEntityMapping(data.gridContainer());

    // for synchronising both simulations
    Synchronizer synchronizer = new Synchronizer();

    // creating and starting the simulator
    Runnable stopper = () -> Optional.ofNullable(extSim).ifPresent(sim -> sim.run = false);

    MosaikSimulator simulator = new MosaikSimulator(synchronizer, mapping, stopper);
    Thread.UncaughtExceptionHandler handler = (t, e) -> stopper.run();
    SimosaikUtils.startMosaikSimulator(simulator, mosaikIP, handler);

    // creating the external simulation
    extSim = new MosaikSimulation(synchronizer);
    extSim.setSetupData(data);
  }
}
