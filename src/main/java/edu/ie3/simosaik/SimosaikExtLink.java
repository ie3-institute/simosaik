/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik;

import edu.ie3.simona.api.ExtLinkInterface;
import edu.ie3.simona.api.data.ExtSimAdapterData;
import edu.ie3.simona.api.mapping.ExtEntityMapping;
import edu.ie3.simosaik.synchronization.Synchronizer;
import edu.ie3.simosaik.utils.SimosaikUtils;
import java.util.function.Supplier;

public final class SimosaikExtLink implements ExtLinkInterface {

  private MosaikSimulation extSim;

  @Override
  public MosaikSimulation getExtSimulation() {
    return extSim;
  }

  @Override
  public void setup(ExtSimAdapterData data) {
    ArgsParser.Arguments arguments = ArgsParser.parse(data.getMainArgs());

    String mosaikIP = arguments.mosaikIP();

    // initial mapping from grid container
    ExtEntityMapping mapping = new ExtEntityMapping(data.getGrid());

    // for synchronising both simulations
    Synchronizer synchronizer = new Synchronizer();

    // creating and starting the simulator
    MosaikSimulator simulator = new MosaikSimulator(synchronizer, mapping);
    Thread.UncaughtExceptionHandler handler = (t, e) -> extSim.run = true;
    Supplier<Boolean> mosaikStateSupplier =
        SimosaikUtils.startMosaikSimulator(simulator, mosaikIP, handler);

    // creating the external simulation
    extSim = new MosaikSimulation(synchronizer, mosaikStateSupplier);
    extSim.setAdapterData(data);
  }
}
