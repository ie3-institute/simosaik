/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.synchronisation;

import edu.ie3.simona.api.data.ExtDataContainerQueue;
import edu.ie3.simona.api.data.container.ExtInputContainer;
import edu.ie3.simona.api.data.container.ExtResultContainer;
import edu.ie3.simosaik.initialization.InitialisationData;
import java.util.Optional;

/**
 * SIMONA part of the {@link Synchronizer}. This interface contains all method, that are available
 * to SIMONA for synchronizing with mosaik.
 */
public sealed interface SIMONAPart permits Synchronizer {

  // update methods
  /**
   * Method for updating the SIMONA tick, that is used by the {@link Synchronizer}.
   *
   * @param tick new tick
   * @throws InterruptedException if there is an interruption
   */
  void updateTickSIMONA(long tick) throws InterruptedException;

  /**
   * Method for updating the next tick, SIMONA expects data.
   *
   * @param maybeNextTick an option for the next SIMONA tick
   */
  void updateNextTickSIMONA(Optional<Long> maybeNextTick);

  // getter methods

  /**
   * Retrieves {@link InitialisationData}, that was provided by mosaik.
   *
   * @param clazz class of data, that is requested
   * @return the initialisation data
   * @param <R> type of initialisation data
   * @throws InterruptedException if there is an interrupted while retrieving the data
   */
  <R extends InitialisationData> R getInitialisationData(Class<R> clazz)
      throws InterruptedException;

  /** Returns {@code true}, if SIMONA is finished for the current tick. */
  boolean isFinished();

  /** Returns {@code true}, if there are inputs from mosaik available. */
  boolean expectInput();

  // setter methods

  /**
   * Sets the data queues, that are provided by the {@link
   * edu.ie3.simona.api.simulation.ExtCoSimulation}.
   *
   * @param queueToSimona queue for input data
   * @param queueToExt queue for output data
   */
  void setDataQueues(
      ExtDataContainerQueue<ExtInputContainer> queueToSimona,
      ExtDataContainerQueue<ExtResultContainer> queueToExt);

  /**
   * Method for setting the finish flag in the {@link Synchronizer} to {@code true}. This flag
   * signals, that SIMONA is finished for the current tick.
   */
  void setFinishedFlag();
}
