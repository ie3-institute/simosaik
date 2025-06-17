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
import edu.ie3.simosaik.initialization.InitializationQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Synchronizer implements SIMONAPart, MosaikPart {

  private final Logger log = LoggerFactory.getLogger(Synchronizer.class);
  
  // mosaik fields
  private final ReentrantLock mosaikLock = new ReentrantLock();
  private final Condition waitForSIMONA = mosaikLock.newCondition();
  private final AtomicLong mosaikTick = new AtomicLong(-1);
  private long mosaikStepSize = 0L;
  private long nextRegularMosaikTick = 0L;
  private long nextMosaikTick = 0L;

  // SIMONA fields
  private final ReentrantLock simonaLock = new ReentrantLock();
  private final Condition waitForMosaik = simonaLock.newCondition();
  
  private final AtomicLong simonaTick = new AtomicLong(-1);
  private final AtomicReference<Optional<Long>> simonaNextTick = new AtomicReference<>(Optional.empty());

  // general fields
  private final InitializationQueue initDataQueue = new InitializationQueue();
  private ExtDataContainerQueue<ExtInputDataContainer> queueToSimona;
  private ExtDataContainerQueue<ExtResultContainer> queueToExt;

  private boolean goToNextTick;

  public Synchronizer() {}

  @Override
  public <R extends InitialisationData> R getInitialisationData(Class<R> clazz)
      throws InterruptedException {
    return initDataQueue.take(clazz);
  }

  @Override
  public void setDataQueues(ExtDataContainerQueue<ExtInputDataContainer> queueToSimona, ExtDataContainerQueue<ExtResultContainer> queueToExt) {
    this.queueToSimona = queueToSimona;
    this.queueToExt = queueToExt;
  }

  @Override
  public void updateTickSIMONA(long tick) throws InterruptedException {
    long mosaikTime = mosaikTick.get();
    
    // set new SIMONA tick
    simonaTick.set(tick);
    
    if (mosaikTime < tick) {
      // wait for mosaik
      log.info("Mosaik is behind SIMONA. SIMONA will wait.");
      
      mosaikLock.lockInterruptibly();
      waitForMosaik.await();
    } else if (mosaikTime == tick) {
      // signal, because mosaik might wait for SIMONA
      waitForSIMONA.signal();
    }

    mosaikLock.unlock();
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
  public boolean sendInputData(ExtInputDataContainer inputData) {
    try {
      queueToSimona.queueData(inputData);
      
      return true;
    } catch (InterruptedException e) {
      // could not queue the input data
      return false;
    }
  }

  @Override
  public Optional<ExtResultContainer> requestResults() {
    Optional<ExtResultContainer> container;
    
    try {
      if (goToNextTick) {
        container = Optional.empty();
      } else {
        container = queueToExt.poll(10, TimeUnit.SECONDS);
      }
      
    } catch (InterruptedException e) {
      container = Optional.empty();
    }

    return container;
  }

  @Override
  public void setMosaikStepSize(long stepSize) {
    log.info("Mosaik step size is: {}", stepSize);
    mosaikStepSize = stepSize;
  }

  @Override
  public void updateMosaikTime(long time) throws InterruptedException {
    log.info("Mosaik provided time: {}", time);

    mosaikTick.set(time);
    
    long simonaTime = simonaTick.get();

    if (time < simonaTime) {
      // mosaik is behing
      log.info("Mosaik is behind SIMONA.");

      // we don't need to update the time
      goToNextTick = true;
      return;
    } else if (time > simonaTime) {
      log.info("SIMONA is behind MOSAIK. Mosaik will wait.");

      // wait for SIMONA
      simonaLock.lockInterruptibly();
      waitForSIMONA.await();

      goToNextTick = false;
    } else {
      // signal, because SIMONA might wait for mosaik
      waitForMosaik.signal();
    }

    simonaLock.unlock();

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

    log.info("Mosaik next time is '{}', next regular time is '{}'.", nextMosaikTick, nextRegularMosaikTick);
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
    
    if (maybeNextTick.isPresent()) {
      long tick = maybeNextTick.get();
      
      if (tick == mosaikTick.get()) {
        return nextMosaikTick;
      } else {
        return tick;
      }
    }
    
    return nextMosaikTick;
  }

  @Override
  public void syncWithSIMONA() {
    long mosaikTime = mosaikTick.get();
    
    // updating, since SIMONA might still be locked
    mosaikTick.set(mosaikTime);
    
    goToNextTick = mosaikTime < simonaTick.get();
  }

  @Override
  public boolean isFinished() {
    syncWithSIMONA();
    return goToNextTick;
  }
}
