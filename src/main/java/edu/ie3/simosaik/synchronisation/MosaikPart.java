/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.synchronisation;

import edu.ie3.simosaik.initialization.InitialisationData;
import java.util.Optional;

public interface MosaikPart {

  void sendInitData(InitialisationData initialisationData) throws InterruptedException;

  void setMosaikStepSize(long stepSize);

  void updateMosaikTime(long time);

  long getCurrentSimonaTick();

  Optional<Long> getNextSimonaTick() throws InterruptedException;

  long getNextTick() throws InterruptedException;

  boolean sendEmptyData();
}
