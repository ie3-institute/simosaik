/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik;

import de.offis.mosaik.api.SimProcess;

/** Class to run SIMONA and MOSAIK in different threads */
public class RunSimosaik implements Runnable {

  /** Simulator that extends the MOSAIK API */
  private final MosaikSimulator mosaikSimulator;

  /** IP address for the connection to MOSAIK */
  private final String mosaikIP;

  public RunSimosaik(String mosaikIP, MosaikSimulator mosaikSimulator) {
    this.mosaikSimulator = mosaikSimulator;
    this.mosaikIP = mosaikIP;
  }

  @Override
  public void run() {
    try {
      SimProcess.startSimulation(new String[] {mosaikIP}, mosaikSimulator);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    System.out.println("Simulation ended!");
  }
}
