/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.synchronisation;

import edu.ie3.simona.api.data.ExtDataContainerQueue;
import edu.ie3.simona.api.data.container.ExtInputDataContainer;
import edu.ie3.simona.api.data.container.ExtResultContainer;
import edu.ie3.simosaik.initialization.InitialisationData;
import java.util.Optional;

public interface SIMONAPart {

  <R extends InitialisationData> R getInitialisationData(Class<R> clazz)
      throws InterruptedException;

  void setDataQueues(ExtDataContainerQueue<ExtInputDataContainer> queueToSimona, ExtDataContainerQueue<ExtResultContainer> queueToExt);
  
  void updateTickSIMONA(long tick) throws InterruptedException;

  void updateNextTickSIMONA(long tick);

  void updateNextTickSIMONA(Optional<Long> tick);

  long getCurrentMosaikTick();
}
