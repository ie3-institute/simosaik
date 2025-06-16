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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Synchronizer implements SIMONAPart, MosaikPart {

  private final Logger log = LoggerFactory.getLogger(Synchronizer.class);
  
  // mosaik fields
  private final AtomicBoolean mosaikBlocked = new AtomicBoolean(false);
  private final AtomicLong mosaikTick = new AtomicLong(0);
  private long mosaikStepSize = 0L;
  private long nextRegularMosaikTick = 0L;
  private long nextMosaikTick = 0L;

  // SIMONA fields
  private final AtomicBoolean simonaBlocked = new AtomicBoolean(false);
  private final AtomicLong simonaTick = new AtomicLong(0);
  private final AtomicReference<Optional<Long>> simonaNextTick = new AtomicReference<>(Optional.empty());

  // general fields
  private final InitializationQueue initDataQueue = new InitializationQueue();

  public Synchronizer() {}

  @Override
  public <R extends InitialisationData> R getInitialisationData(Class<R> clazz)
      throws InterruptedException {
    return initDataQueue.take(clazz);
  }

  @Override
  public void updateTickSIMONA(long tick) throws InterruptedException {
    long mosaikTime = mosaikTick.get();
    
    if (mosaikTime < tick) {
      final ReentrantLock simonaLock = new  ReentrantLock();
      simonaLock.lockInterruptibly();
      
      Condition waitForMosaik = simonaLock.newCondition();
      
      try {
        // set new SIMONA tick
        simonaTick.set(tick);
        
        // wait for mosaik
        log.info("Mosaik is behind SIMONA. We need to wait.");
        simonaBlocked.set(true);
        waitForMosaik.await();
        simonaBlocked.set(false);
      } finally {
        simonaLock.unlock();
      }
    }
  }

  @Override
  public void updateNextTickSIMONA(long tick) {
    updateNextTickSIMONA(Optional.of(tick));
  }

  @Override
  public void updateNextTickSIMONA(Optional<Long> tick) {
    simonaNextTick.set(tick);
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
    log.info("Mosaik step size is: {}", stepSize);
    mosaikStepSize = stepSize;
  }

  @Override
  public void updateMosaikTime(long time) {
    log.info("Mosaik provided time: {}", time);

    if (time < simonaTick.get()) {
      log.info("Mosaik is behind SIMONA.");
    } else {
      
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

      log.info("Mosaik next time is '{}', next regular time is '{}'.", nextMosaikTick, nextRegularMosaikTick);
    }
  }

  @Override
  public long getCurrentSimonaTick() {
    long tick = simonaTick.get();
    log.info("Current simonaTick is: {}", tick);
    return tick;
  }

  @Override
  public Optional<Long> getNextSimonaTick() {
    return simonaNextTick.get();
  }

  @Override
  public long getNextTick() {
    Optional<Long> maybeNextTick = getNextSimonaTick();
    return maybeNextTick.orElse(nextMosaikTick);
  }

  @Override
  public boolean sendEmptyData() {
    boolean sendEmptyData = simonaTick.get() != mosaikTick.get();
    
    log.info("Sending empty data = {}.", sendEmptyData);
    return sendEmptyData;
  }
}
