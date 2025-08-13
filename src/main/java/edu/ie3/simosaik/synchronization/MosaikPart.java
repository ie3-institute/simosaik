/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.synchronization;

import edu.ie3.simona.api.data.container.ExtInputContainer;
import edu.ie3.simona.api.data.container.ExtResultContainer;
import edu.ie3.simosaik.initialization.InitializationData;
import java.util.Optional;

/**
 * Mosaik part of the {@link Synchronizer}. This interface contains all method, that are available
 * to mosaik for synchronizing with SIMONA.
 */
public sealed interface MosaikPart permits Synchronizer {

  // update methods

  /**
   * Method for updating the mosaik time, that is used by the {@link Synchronizer}.
   *
   * @param time new time
   * @return the scaled mosaik time
   * @throws InterruptedException if there is an interruption
   */
  long updateMosaikTime(long time) throws InterruptedException;

  /**
   * Method for sending {@link InitializationData} to SIMONA.
   *
   * @param initialisationData that should be sent
   * @throws InterruptedException if there is an interruption while sending the data
   */
  void sendInitData(InitializationData initialisationData) throws InterruptedException;

  /**
   * Method for sending input data to SIMONA.
   *
   * @param inputData that should be sent
   * @return {@code true} if the data was sent correctly, else {@code false} is returned
   */
  boolean sendInputData(ExtInputContainer inputData);

  // getter methods

  /** Returns an option for result data from SIMONA. */
  Optional<ExtResultContainer> requestResults();

  /**
   * Returns the next tick, for which mosaik should be executed. This method may return a tick
   * between regular mosaik ticks.
   */
  long getNextTick();

  /** Returns {@code true}, if data should be sent to mosaik, else {@code false} is returned. */
  boolean outputNextTick();

  /** Returns {@code true}, if SIMONA is finished for the current tick. */
  boolean isFinished();

  // setter methods

  /**
   * Method for setting the no input flag in the {@link Synchronizer} to {@code true}. This flag
   * signals, that mosaik has until now not provided any input for the current tick.
   */
  void setNoInputFlag();

  /**
   * Method for setting the no output fleg in the {@link Synchronizer} to {@code true}. This flag
   * signals, that no further output is sent to mosaik.
   */
  void setNoOutputFlag();

  /**
   * Method for setting the mosaik step size.
   *
   * @param stepSize that should be used
   */
  void setMosaikStepSize(long stepSize);

  /**
   * Method for setting the mosaik time scaling.
   *
   * @param timeScaling that is used
   */
  void setMosaikTimeScaling(double timeScaling);
}
