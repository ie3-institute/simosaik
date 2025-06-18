/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.synchronisation;

import edu.ie3.simona.api.data.container.ExtInputDataContainer;
import edu.ie3.simona.api.data.container.ExtResultContainer;
import edu.ie3.simosaik.initialization.InitialisationData;
import java.util.Optional;

/**
 * Mosaik part of the {@link Synchronizer}. This interface contains all method, that are available
 * to mosaik for synchronizing with SIMONA.
 */
public sealed interface MosaikPart permits Synchronizer {

  void sendInitData(InitialisationData initialisationData) throws InterruptedException;

  /**
   * Method for setting the no input flag in the {@link Synchronizer} to {@code true}. This flag
   * signals, that mosaik has until now not provided any input for the current tick.
   */
  void setNoInputFlag();

  boolean sendInputData(ExtInputDataContainer inputData);

  Optional<ExtResultContainer> requestResults();

  void setMosaikStepSize(long stepSize);

  void updateMosaikTime(long time) throws InterruptedException;

  Optional<Long> getNextSimonaTick();

  long getNextTick();

  boolean outputNextTick();

  boolean isFinished();
}
