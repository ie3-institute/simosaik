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

public interface MosaikPart {

  void sendInitData(InitialisationData initialisationData) throws InterruptedException;

  void setNoInputFlag();

  boolean sendInputData(ExtInputDataContainer inputData);

  Optional<ExtResultContainer> requestResults();

  void setMosaikStepSize(long stepSize);

  void updateMosaikTime(long time) throws InterruptedException;

  long getCurrentSimonaTick();

  Optional<Long> getNextSimonaTick();

  long getNextTick();

  boolean outputNextTick();

  boolean isFinished();
}
