/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.synchronization;

import edu.ie3.simona.api.data.ExtDataContainerQueue;
import edu.ie3.simona.api.data.container.ExtInputContainer;
import edu.ie3.simona.api.data.container.ExtOutputContainer;
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
  private final AtomicLong scaledMosaikTick = new AtomicLong(-1);
  private double mosaikTimeScaling = 1d;
  private long mosaikStepSize = 0L;
  private long nextRegularMosaikTick = 0L;
  private long nextMosaikTick = 0L;
  private boolean noInputs = false;
  private boolean noOutputs = false;
  private boolean hasSendNextTick = false;

  // SIMONA fields
  private final AtomicLong simonaTick = new AtomicLong(-1);
  private final AtomicReference<Optional<Long>> simonaNextTick =
      new AtomicReference<>(Optional.empty());
  private long stepSizeSIMONA = 0L;
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
  private ExtDataContainerQueue<ExtOutputContainer> queueToExt;

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

    long mosaikTime = scaledMosaikTick.get();

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
  public long getStepSize() {
    return stepSizeSIMONA;
  }

  @Override
  public boolean expectInput() {
    return noInputs;
  }

  @Override
  public long currentMosaikTick() {
    return scaledMosaikTick.get();
  }

  @Override
  public void setDataQueues(
      ExtDataContainerQueue<ExtInputContainer> queueToSimona,
      ExtDataContainerQueue<ExtOutputContainer> queueToExt) {
    this.queueToSimona = queueToSimona;
    this.queueToExt = queueToExt;
  }

  @Override
  public void setFinishedFlag() {
    isFinished = true;
  }

  // mosaik part

  @Override
  public long updateMosaikTime(long time) throws InterruptedException {
    noInputs = false;
    hasSendNextTick = false;
    long simonaTime = simonaTick.get();
    long scaledMosaikTime = (long) (time * mosaikTimeScaling);

    log.info("Mosaik provided time: {} ({}), (SIMONA: {})", scaledMosaikTime, time, simonaTime);

    mosaikTick.set(time);
    scaledMosaikTick.set(scaledMosaikTime);

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
    if (scaledMosaikTime < simonaTime) {
      // mosaik is behind
      // we don't need to update the time
      goToNextTick = true;

      if (simonaTime < nextRegularMosaikTick) {
        long scaledSimonaTime = (long) (time / mosaikTimeScaling);

        log.info("SIMONA requires an intermediate tick for: {}", scaledSimonaTime);
        nextMosaikTick = scaledSimonaTime;
      }

    } else if (scaledMosaikTime > simonaTime) {
      goToNextTick = false;

      // signal, because SIMONA might wait for mosaik
      continueSIMONA.signal();

      if (simonaIsWaiting) {
        // SIMONA is waiting for mosaik to provide data for the next SIMONA tick
        long nextSimonaTick = getNextTick();

        if (scaledMosaikTime > nextSimonaTick) {
          // mosaik is providing for a tick, that lies in the future for SIMONA => error
          throw new IllegalStateException(
              "SIMONA is waiting for tick '"
                  + nextSimonaTick
                  + "', but mosaik provided data for tick '"
                  + scaledMosaikTime
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

    return scaledMosaikTime;
  }

  @Override
  public void sendInitData(InitializationData initialisationData) throws InterruptedException {
    initDataQueue.put(initialisationData);
  }

  @Override
  public boolean sendInputData(ExtInputContainer inputData) {
    try {
      // clear all remaining results, since we received new input data
      queueToExt.clear();

      queueToSimona.queueData(inputData);

      return true;
    } catch (InterruptedException ignored) {
    }

    // could not queue the input data
    return false;
  }

  @Override
  public Optional<ExtOutputContainer> requestResults() {
    Optional<ExtOutputContainer> container;

    try {
      container = queueToExt.pollContainer(100, TimeUnit.MILLISECONDS);
      boolean isFinished = isFinished();

      while (container.isEmpty() && !isFinished) {
        container = queueToExt.pollContainer(100, TimeUnit.MILLISECONDS);
        isFinished = isFinished();
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

      if (tick == scaledMosaikTick.get()) {
        System.out.println("getNextTick() -> _nextMosaikTick=" + mosaikTick);
        return nextMosaikTick;
      } else {
        long t = (long) (tick / mosaikTimeScaling);
        System.out.println("getNextTick() -> tick=" + t);

        return t;
      }
    }

    System.out.println("getNextTick() -> nextMosaikTick=" + mosaikTick);
    return nextMosaikTick;
  }

  @Override
  public boolean outputNextTick() {
    // we only output the next tick, if we can send outputs to mosaik, the next tick has changed and
    // the information has not been sent yet
    return !noOutputs && hasNextTickChanged && !hasSendNextTick;
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
  public void setHasSendNextTick() {
    hasSendNextTick = true;
  }

  @Override
  public void setMosaikStepSize(long stepSize) {
    log.info("Mosaik step size is: {} (Scaled: {})", stepSize, stepSize * mosaikTimeScaling);
    mosaikStepSize = stepSize;
    stepSizeSIMONA = (long) (stepSize * mosaikTimeScaling);
  }

  @Override
  public void setMosaikTimeScaling(double timeScaling) {
    log.info("Mosaik time scaling is: {}", timeScaling);
    mosaikTimeScaling = timeScaling;
  }
}
