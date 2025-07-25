/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.synchronization;

import edu.ie3.simona.api.data.ExtDataContainerQueue;
import edu.ie3.simona.api.data.container.ExtInputContainer;
import edu.ie3.simona.api.data.container.ExtResultContainer;
import edu.ie3.simosaik.initialization.InitializationData;
import edu.ie3.simosaik.initialization.InitializationQueue;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Synchronizer implements SIMONAPart, MosaikPart {

  private final Logger log = LoggerFactory.getLogger(Synchronizer.class);

  // mosaik fields
  private final AtomicLong mosaikTick = new AtomicLong(-1);
  private long mosaikStepSize = 0L;
  private long nextRegularMosaikTick = 0L;
  private long nextMosaikTick = 0L;
  private boolean noInputs = false;
  private boolean noOutputs = false;

  // SIMONA fields
  private final AtomicLong simonaTick = new AtomicLong(-1);
  private final AtomicReference<Optional<Long>> simonaNextTick =
      new AtomicReference<>(Optional.empty());
  private boolean hasNextTickChanged;
  private boolean isFinished = false;

  // general fields
  private final ReentrantLock simosaikLock = new ReentrantLock();
  private final Condition continueSIMONA = simosaikLock.newCondition();
  private boolean simonaIsWaiting = false;

  private final Condition continueMosaik = simosaikLock.newCondition();
  private boolean mosaikIsWaiting = false;

  // data queues
  private final InitializationQueue initDataQueue = new InitializationQueue();
  private ExtDataContainerQueue<ExtInputContainer> queueToSimona;
  private ExtDataContainerQueue<ExtResultContainer> queueToExt;

  private boolean goToNextTick;

  public Synchronizer() {}

  // universal

  @Override
  public boolean isFinished() {
    return goToNextTick || isFinished;
  }

  // SIMONA part

  @Override
  public void updateTickSIMONA(long tick) throws InterruptedException {
    isFinished = false;

    long mosaikTime = mosaikTick.get();

    // set new SIMONA tick
    simonaTick.set(tick);

    // get simosaik lock
    while (!simosaikLock.tryLock()) {
      log.warn("SIMONA: Waiting for simosaik lock.");
    }

    if (tick < mosaikTime) {
      // SIMONA is behind
      log.warn(
          "SIMONA cannot receive data for tick '{}', because mosaik is already at time '{}'.",
          tick,
          mosaikTime);

      isFinished = true;
    } else if (tick > mosaikTime) {
      // mosaik is behind

      // signal, because mosaik might wait for SIMONA
      continueMosaik.signal();

      if (mosaikIsWaiting) {
        // mosaik is waiting for the next SIMONA tick
        log.warn(
            "Mosaik with time '{}' is waiting for SIMONA, but SIMONA provided data for tick '{}'!",
            mosaikTime,
            tick);

      } else {
        // wait for mosaik
        log.info("Mosaik is behind SIMONA. SIMONA will wait.");

        goToNextTick = true;

        // tell SIMONA to wait for mosaik, will continue, if mosaik sends a signal
        simonaIsWaiting = true;
        continueSIMONA.await();
        simonaIsWaiting = false;
      }
    } else {
      // both simulators are synced

      // signal, because mosaik might wait for SIMONA
      continueMosaik.signal();
    }

    // release the mosaik lock
    simosaikLock.unlock();
  }

  @Override
  public void updateNextTickSIMONA(Optional<Long> maybeNextTick) {
    Optional<Long> oldValue = simonaNextTick.get();

    if (oldValue == maybeNextTick) {
      hasNextTickChanged = false;
    } else {
      simonaNextTick.set(maybeNextTick);
      hasNextTickChanged = true;
    }
  }

  @Override
  public <R extends InitializationData> R getInitializationData(Class<R> clazz)
      throws InterruptedException {
    return initDataQueue.take(clazz);
  }

  @Override
  public boolean expectInput() {
    return noInputs;
  }

  @Override
  public void setDataQueues(
      ExtDataContainerQueue<ExtInputContainer> queueToSimona,
      ExtDataContainerQueue<ExtResultContainer> queueToExt) {
    this.queueToSimona = queueToSimona;
    this.queueToExt = queueToExt;
  }

  @Override
  public void setFinishedFlag() {
    isFinished = true;
  }

  // mosaik part

  @Override
  public void updateMosaikTime(long time) throws InterruptedException {
    noInputs = false;
    long simonaTime = simonaTick.get();

    log.info("Mosaik provided time: {}, (SIMONA: {})", time, simonaTime);

    mosaikTick.set(time);

    // get simosaik lock
    while (!simosaikLock.tryLock()) {
      log.warn("Mosaik: Waiting for simosaik lock.");
    }

    if (time == nextRegularMosaikTick) {
      // the received time is the next regular tick, that we expected

      noOutputs = false;

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

    // check if synced with SIMONA
    if (time < simonaTime) {
      // mosaik is behind
      // we don't need to update the time
      goToNextTick = true;

      if (simonaTime < nextRegularMosaikTick) {
        log.info("SIOMANA requires an intermediate tick for: {}", simonaTime);
        nextMosaikTick = simonaTime;
      }

    } else if (time > simonaTime) {
      goToNextTick = false;

      // signal, because SIMONA might wait for mosaik
      continueSIMONA.signal();

      if (simonaIsWaiting) {
        // SIMONA is waiting for mosaik to provide data for the next SIMONA tick
        long nextSimonaTick = getNextTick();

        if (time > nextSimonaTick) {
          // mosaik is providing for a tick, that lies in the future for SIMONA => error
          throw new IllegalStateException(
              "SIMONA is waiting for tick '"
                  + nextSimonaTick
                  + "', but mosaik provided data for tick '"
                  + time
                  + "'!");
        }

      } else {
        log.info("SIMONA is behind MOSAIK. Mosaik will wait.");

        // wait for SIMONA
        mosaikIsWaiting = true;
        continueMosaik.await();
        mosaikIsWaiting = false;
      }

    } else {
      // both simulators are synced
      goToNextTick = false;

      // signal, because SIMONA might wait for mosaik
      continueSIMONA.signal();
    }

    log.info(
        "Mosaik next time is '{}', next regular time is '{}'.",
        nextMosaikTick,
        nextRegularMosaikTick);

    // release the lock
    simosaikLock.unlock();
  }

  @Override
  public void sendInitData(InitializationData initialisationData) throws InterruptedException {
    initDataQueue.put(initialisationData);
  }

  @Override
  public boolean sendInputData(ExtInputContainer inputData) {
    try {
      queueToSimona.queueData(inputData);

      return true;
    } catch (InterruptedException ignored) {
    }

    // could not queue the input data
    return false;
  }

  @Override
  public Optional<ExtResultContainer> requestResults() {
    Optional<ExtResultContainer> container;

    try {
      if (isFinished()) {
        container = Optional.empty();
      } else {

        container = queueToExt.pollContainer(100, TimeUnit.MILLISECONDS);

        while (container.isEmpty()) {
          // no data found

          if (!isFinished()) {
            // SIMONA is not finished for the current tick
            container = queueToExt.pollContainer(100, TimeUnit.MILLISECONDS);
          } else {
            // SIMONA went to the next tick, there will be no more data for the current tick
            container = Optional.empty();
          }
        }
      }

    } catch (InterruptedException e) {
      container = Optional.empty();
    }

    return container;
  }

  @Override
  public long getNextTick() {
    Optional<Long> maybeNextTick = simonaNextTick.get();

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
  public boolean outputNextTick() {
    // we only output the next tick, if we can send outputs to mosaik, the next tick has changed and
    // the next tick is not equal to the next regular mosaik tick
    return !noOutputs && hasNextTickChanged && getNextTick() != nextRegularMosaikTick;
  }

  @Override
  public void setNoInputFlag() {
    noInputs = true;
  }

  @Override
  public void setNoOutputFlag() {
    noOutputs = true;
  }

  @Override
  public void setMosaikStepSize(long stepSize) {
    log.info("Mosaik step size is: {}", stepSize);
    mosaikStepSize = stepSize;
  }
}
