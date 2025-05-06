/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.initialization;

import java.util.concurrent.LinkedBlockingQueue;

public class InitializationQueue {

  private final LinkedBlockingQueue<InitialisationData> initializationQueue =
      new LinkedBlockingQueue<>();

  public void put(InitialisationData initialisationData) throws InterruptedException {
    initializationQueue.put(initialisationData);
  }

  public <R> R take(Class<R> clazz) throws InterruptedException {
    InitialisationData initialisationData = initializationQueue.take();

    if (clazz.isAssignableFrom(initialisationData.getClass())) {
      return clazz.cast(initialisationData);
    } else {
      throw new IllegalStateException(
          "Received unexpected initialisation data: " + initialisationData);
    }
  }
}
