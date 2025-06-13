/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.synchronisation;

import edu.ie3.simosaik.initialization.InitialisationData;
import edu.ie3.simosaik.initialization.InitializationQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

public class Synchronizer implements SIMONAPart, MosaikPart {

  private final Logger log = LoggerFactory.getLogger(Synchronizer.class);
  
  // mosaik fields
  private final AtomicLong mosaikTick = new AtomicLong(0);
  private long mosaikStepSize = 0L;
  private long nextRegularMosaikTick = 0L;
  private long nextMosaikTick = 0L;
  private boolean sendEmptyData = false;

  // SIMONA fields
  private final AtomicLong simonaTick = new AtomicLong(-1);
  private final LinkedBlockingQueue<Optional<Long>> simonaNextTick = new LinkedBlockingQueue<>(1);

  // general fields
  private final InitializationQueue initDataQueue = new InitializationQueue();

  public Synchronizer() {}

  @Override
  public <R extends InitialisationData> R getInitialisationData(Class<R> clazz)
      throws InterruptedException {
    return initDataQueue.take(clazz);
  }

  @Override
  public void updateTickSIMONA(long tick) {
    simonaTick.set(tick);
  }

  @Override
  public void updateNextTickSIMONA(long tick) throws InterruptedException {
    updateNextTickSIMONA(Optional.of(tick));
  }

  @Override
  public void updateNextTickSIMONA(Optional<Long> tick) throws InterruptedException {
    simonaNextTick.clear();
    simonaNextTick.put(tick);
  }

  @Override
  public long getCurrentMosaikTick() {
    return mosaikTick.get();
  }

  @Override
  public void sendInitData(InitialisationData initialisationData) throws InterruptedException {
    initDataQueue.put(initialisationData);
  }

  @Override
  public void setMosaikStepSize(long stepSize) {
    mosaikStepSize = stepSize;
  }

  @Override
  public void updateMosaikTime(long time) {
    if (time != simonaTick.get()) {
      sendEmptyData = true;
      
      // no update
      return;
    }
    
    if (time == nextRegularMosaikTick) {
      // the received time is the next regular tick, that we expected

      // calculate the next regular tick
      nextMosaikTick = time + mosaikStepSize;

      // set the next regular tick
      nextRegularMosaikTick = nextMosaikTick;

    } else if (time < nextRegularMosaikTick) {
      // the received time is between the last and the next regular tick
      nextMosaikTick = nextRegularMosaikTick;
    } else {
      // we received data for a time after the expected tick
      // this should be an error
      throw new IllegalStateException(
          "We received no data for the expected tick. Expected data for tick '"
              + nextRegularMosaikTick
              + "', but got data for tick '"
              + time
              + "'.");
    }

    mosaikTick.set(time);
  }

  @Override
  public long getCurrentSimonaTick() {
    return simonaTick.get();
  }

  @Override
  public Optional<Long> getNextSimonaTick() throws InterruptedException {
    return simonaNextTick.take();
  }

  @Override
  public long getNextTick() throws InterruptedException {
    Optional<Long> maybeNextTick = getNextSimonaTick();

    return maybeNextTick.orElse(nextMosaikTick);
  }

  @Override
  public boolean sendEmptyData() {
    return sendEmptyData;
  }
}
