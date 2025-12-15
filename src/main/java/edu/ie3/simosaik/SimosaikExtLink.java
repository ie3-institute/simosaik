/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik;

import de.offis.mosaik.api.SimProcess;
import edu.ie3.simona.api.ExtLinkInterface;
import edu.ie3.simona.api.data.SetupData;
import edu.ie3.simona.api.mapping.ExtEntityMapping;
import edu.ie3.simosaik.synchronization.Synchronizer;
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
    startMosaikSimulator(simulator, mosaikIP, handler);

    // creating the external simulation
    extSim = new MosaikSimulation(synchronizer);
    extSim.setSetupData(data);
  }

  /**
   * Starts MOSAIK connection
   *
   * @param mosaikSimulator Simulator that extends the MOSAIK API
   * @param mosaikIP IP address for the connection with MOSAIK
   */
  public static void startMosaikSimulator(
      MosaikSimulator mosaikSimulator, String mosaikIP, Thread.UncaughtExceptionHandler handler) {

    // mosaik simulator thread
    Thread thread =
        new Thread("Simosaik") {
          @Override
          public void run() {
            try {
              SimProcess.startSimulation(new String[] {mosaikIP}, mosaikSimulator);
            } catch (Exception e) {
              handler.uncaughtException(this, e);
            }
          }
        };

    thread.start();
  }
}
