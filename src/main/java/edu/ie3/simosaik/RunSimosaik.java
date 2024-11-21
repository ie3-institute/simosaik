/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik;

import de.offis.mosaik.api.SimProcess;
import edu.ie3.simosaik.mosaik.MosaikSimulator;

/** Class to run SIMONA and MOSAIK in different threads */
public class RunSimosaik implements Runnable {

  /** Simulator that extends the MOSAIK API */
  private final MosaikSimulator simonaSimulator;

  /** IP address for the connection to MOSAIK */
  private final String mosaikIP;

  public RunSimosaik(String mosaikIP, MosaikSimulator simonaSimulator) {
    this.simonaSimulator = simonaSimulator;
    this.mosaikIP = mosaikIP;
  }

  @Override
  public void run() {
    try {
      SimProcess.startSimulation(new String[] {mosaikIP}, simonaSimulator);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
