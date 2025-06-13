/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.synchronisation;

import edu.ie3.simosaik.initialization.InitialisationData;
import java.util.Optional;

public interface SIMONAPart {

  <R extends InitialisationData> R getInitialisationData(Class<R> clazz)
      throws InterruptedException;

  void updateTickSIMONA(long tick);

  void updateNextTickSIMONA(long tick) throws InterruptedException;

  void updateNextTickSIMONA(Optional<Long> tick) throws InterruptedException;

  long getCurrentMosaikTick();
}
