/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.synchronisation;

import edu.ie3.simona.api.data.container.ExtInputContainer;
import edu.ie3.simona.api.data.container.ExtResultContainer;
import edu.ie3.simosaik.initialization.InitialisationData;
import java.util.Optional;

/**
 * Mosaik part of the {@link Synchronizer}. This interface contains all method, that are available
 * to mosaik for synchronizing with SIMONA.
 */
public sealed interface MosaikPart permits Synchronizer {

  // update methods

  void updateMosaikTime(long time) throws InterruptedException;

  void sendInitData(InitialisationData initialisationData) throws InterruptedException;

  boolean sendInputData(ExtInputContainer inputData);

  // getter methods

  Optional<ExtResultContainer> requestResults();

  long getNextTick();

  boolean outputNextTick();

  /** Returns {@code true}, if SIMONA is finished for the current tick. */
  boolean isFinished();

  // setter methods

  /**
   * Method for setting the no input flag in the {@link Synchronizer} to {@code true}. This flag
   * signals, that mosaik has until now not provided any input for the current tick.
   */
  void setNoInputFlag();

  void setMosaikStepSize(long stepSize);
}
