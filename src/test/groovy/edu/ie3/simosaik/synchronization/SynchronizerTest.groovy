package edu.ie3.simosaik.synchronization

import edu.ie3.simona.api.data.ExtDataContainerQueue
import edu.ie3.simona.api.data.container.ExtInputContainer
import edu.ie3.simona.api.data.container.ExtResultContainer
import edu.ie3.simosaik.initialization.InitializationData
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

class SynchronizerTest extends Specification {

    // testing method seen by SIMONA

    def "The SIMONA part of the synchronizer should update the tick of SIMONA correctly, if mosaik has the same tick"() {
        given:
        Synchronizer synchronizer = new Synchronizer()
        SIMONAPart simonaPart = synchronizer as SIMONAPart

        synchronizer.mosaikTick.set(900L)

        when:
        simonaPart.updateTickSIMONA(900L)

        then:
        synchronizer.simonaTick.get() == 900L
        synchronizer.simonaNextTick.get() == Optional.empty()
        !synchronizer.hasNextTickChanged
        !synchronizer.isFinished
    }

    def "The SIMONA part of the synchronizer should update the tick of SIMONA correctly, if mosaik is ahead"() {
        given:
        Synchronizer synchronizer = new Synchronizer()
        SIMONAPart simonaPart = synchronizer as SIMONAPart

        synchronizer.mosaikTick.set(1800L)

        when:
        simonaPart.updateTickSIMONA(900L)

        then:
        synchronizer.simonaTick.get() == 900L
        synchronizer.simonaNextTick.get() == Optional.empty()
        !synchronizer.hasNextTickChanged
        synchronizer.isFinished
    }

    def "The SIMONA part of the synchronizer should update the tick of SIMONA correctly, if mosaik is behind"() {
        given:
        Synchronizer synchronizer = new Synchronizer()
        SIMONAPart simonaPart = synchronizer as SIMONAPart

        synchronizer.mosaikTick.set(800L)
        synchronizer.mosaikStepSize = 100L
        synchronizer.nextRegularMosaikTick = 900L

        when:
        def task = CompletableFuture.supplyAsync { simonaPart.updateTickSIMONA(900L) }
        synchronizer.updateMosaikTime(900L)
        task.get()

        then:
        synchronizer.simonaTick.get() == 900L

        synchronizer.simonaTick.get() == 900L
        synchronizer.simonaNextTick.get() == Optional.empty()
        !synchronizer.hasNextTickChanged
        !synchronizer.isFinished
    }

    def "The SIMONA part of the synchronizer should update the next tick of SIMONA correctly"() {
        given:
        Synchronizer synchronizer = new Synchronizer()
        SIMONAPart simonaPart = synchronizer as SIMONAPart

        synchronizer.simonaTick.set(900L)

        when:
        simonaPart.updateNextTickSIMONA(Optional.of(1000L))

        then:
        synchronizer.simonaTick.get() == 900L
        synchronizer.simonaNextTick.get() == Optional.of(1000L)
        synchronizer.hasNextTickChanged
        !synchronizer.isFinished
    }

    def "The SIMONA part of the synchronizer should retrieve initialization data correctly"() {
        given:
        Synchronizer synchronizer = new Synchronizer()
        SIMONAPart simonaPart = synchronizer as SIMONAPart

        synchronizer.initDataQueue.put(new InitializationData.SimulatorData(3600L, false))

        when:
        def data = simonaPart.getInitializationData(InitializationData.SimulatorData)

        then:
        data.stepSize() == 3600L
        !data.disaggregate()
    }

    def "The SIMONA part of the synchronizer should return if the tick is finished correctly"() {
        given:
        Synchronizer synchronizer = new Synchronizer()
        SIMONAPart simonaPart = synchronizer as SIMONAPart

        expect:

        synchronizer.isFinished == simonaPart.isFinished()

        simonaPart.setFinishedFlag()
        synchronizer.isFinished == simonaPart.isFinished()
    }

    def "The SIMONA part of the synchronizer should return if input is to be expected correctly"() {
            given:
            Synchronizer synchronizer = new Synchronizer()
            SIMONAPart simonaPart = synchronizer as SIMONAPart

            expect:
            !simonaPart.expectInput()

            synchronizer.setNoInputFlag()
            simonaPart.expectInput()
        }

    def "The SIMONA part of the synchronizer should set the data queues correctly"() {
        given:
        Synchronizer synchronizer = new Synchronizer()
        SIMONAPart simonaPart = synchronizer as SIMONAPart

        ExtDataContainerQueue<ExtInputContainer> queueToSIMONA = new ExtDataContainerQueue<>()
        ExtDataContainerQueue<ExtResultContainer> queueToExt = new ExtDataContainerQueue<>()

        when:
        simonaPart.setDataQueues(queueToSIMONA, queueToExt)

        then:
        synchronizer.queueToSimona == queueToSIMONA
        synchronizer.queueToExt == queueToExt
    }

    def "The SIMONA part of the synchronizer should set the finished flag correctly"() {
        given:
        Synchronizer synchronizer = new Synchronizer()
        SIMONAPart simonaPart = synchronizer as SIMONAPart

        expect:
        !synchronizer.isFinished
        simonaPart.setFinishedFlag()
        synchronizer.isFinished
    }


    // testing method seen by mosaik

    def "The mosaik part of the synchronizer should update the tick of mosaik correctly, if SIMONA has the same tick"() {
        given:
        Synchronizer synchronizer = new Synchronizer()
        MosaikPart mosaikPart = synchronizer as MosaikPart

        synchronizer.mosaikStepSize = 900L
        synchronizer.nextRegularMosaikTick = 900L

        synchronizer.simonaTick.set(900L)

        when:
        mosaikPart.updateMosaikTime(900L)

        then:
        synchronizer.mosaikTick.get() == 900L
        synchronizer.nextRegularMosaikTick == 1800L
        synchronizer.nextMosaikTick == 1800L
        !synchronizer.noInputs
    }

    def "The mosaik part of the synchronizer should update the tick of mosaik correctly, if SIMONA is ahead"() {
        given:
        Synchronizer synchronizer = new Synchronizer()
        MosaikPart mosaikPart = synchronizer as MosaikPart

        synchronizer.mosaikStepSize = 900L
        synchronizer.nextRegularMosaikTick = 1800L

        synchronizer.simonaTick.set(1800L)

        when:
        mosaikPart.updateMosaikTime(1000L)

        then:
        synchronizer.mosaikTick.get() == 1000L
        synchronizer.nextRegularMosaikTick == 1800L
        synchronizer.nextMosaikTick == 1800L
        !synchronizer.noInputs
    }

    def "The mosaik part of the synchronizer should update the tick of mosaik correctly, if SIMONA is behind"() {
        given:
        Synchronizer synchronizer = new Synchronizer()
        MosaikPart mosaikPart = synchronizer as MosaikPart

        synchronizer.mosaikStepSize = 100L
        synchronizer.nextRegularMosaikTick = 900L

        synchronizer.simonaTick.set(800L)

        when:
        def task = CompletableFuture.supplyAsync { mosaikPart.updateMosaikTime(900L) }
        synchronizer.updateTickSIMONA(900L)
        task.get()

        then:
        synchronizer.mosaikTick.get() == 900L
        synchronizer.nextRegularMosaikTick == 1000L
        synchronizer.nextMosaikTick == 1000L
        !synchronizer.noInputs
    }

    def "The mosaik part of the synchronizer should send initialization data correctly"() {
        given:
        Synchronizer synchronizer = new Synchronizer()
        MosaikPart mosaikPart = synchronizer as MosaikPart

        when:
        mosaikPart.sendInitData(new InitializationData.SimulatorData(900L, true))

        then:
        synchronizer.initDataQueue.size() == 1
        def data = synchronizer.initDataQueue.take(InitializationData.SimulatorData)
        data.stepSize() == 900L
        data.disaggregate()
    }

    def "The mosaik part of the synchronizer should send input data correctly"() {
        given:
        Synchronizer synchronizer = new Synchronizer()
        MosaikPart mosaikPart = synchronizer as MosaikPart

        ExtDataContainerQueue<ExtInputContainer> queueToSIMONA = new ExtDataContainerQueue<>()
        ExtDataContainerQueue<ExtResultContainer> queueToExt = new ExtDataContainerQueue<>()
        synchronizer.setDataQueues(queueToSIMONA, queueToExt)

        when:
        mosaikPart.sendInputData(new ExtInputContainer(900L))

        then:
        synchronizer.queueToSimona.size() == 1
        def data = synchronizer.queueToSimona.takeContainer()
        data.isEmpty()
    }

    def "The mosaik part of the synchronizer should request results correctly"() {
        given:
        Synchronizer synchronizer = new Synchronizer()
        MosaikPart mosaikPart = synchronizer as MosaikPart

        ExtDataContainerQueue<ExtInputContainer> queueToSIMONA = new ExtDataContainerQueue<>()
        ExtDataContainerQueue<ExtResultContainer> queueToExt = new ExtDataContainerQueue<>()
        synchronizer.setDataQueues(queueToSIMONA, queueToExt)

        when:
        def task = CompletableFuture.supplyAsync { mosaikPart.requestResults() }
        synchronizer.queueToExt.queueData(new ExtResultContainer(900L, [:]))
        def results = task.get()

        then:
        results.isPresent()
        ExtResultContainer container = results.get()
        container.empty
    }

    def "The mosaik part of the synchronizer should retrieve the next SIMONA tick correctly"() {
        given:
        Synchronizer synchronizer = new Synchronizer()
        MosaikPart mosaikPart = synchronizer as MosaikPart

        synchronizer.mosaikStepSize = 100L
        synchronizer.nextRegularMosaikTick = 900L
        synchronizer.nextMosaikTick = 900L


        expect:
        mosaikPart.getNextTick() == 900L

        synchronizer.updateNextTickSIMONA(Optional.of(900L))
        mosaikPart.getNextTick() == 900L

        synchronizer.updateNextTickSIMONA(Optional.of(850L))
        mosaikPart.getNextTick() == 850L
    }

    def "The mosaik part of the synchronizer should retrieve if the next tick should be outputted correctly"() {
        given:
        Synchronizer synchronizer = new Synchronizer()
        MosaikPart mosaikPart = synchronizer as MosaikPart

        synchronizer.nextRegularMosaikTick = 900L


        expect:
        !mosaikPart.outputNextTick()

        synchronizer.updateNextTickSIMONA(Optional.of(900L))
        !mosaikPart.outputNextTick()

        synchronizer.updateNextTickSIMONA(Optional.of(850L))
        mosaikPart.outputNextTick()
    }

    def "The mosaik part of the synchronizer should return if the tick is finished correctly"() {
        given:
        Synchronizer synchronizer = new Synchronizer()
        MosaikPart mosaikPart = synchronizer as MosaikPart

        expect:
        !mosaikPart.isFinished()

        synchronizer.setFinishedFlag()
        mosaikPart.isFinished()
    }

    def "The mosaik part of the synchronizer should set the no input flag correctly"() {
        given:
        Synchronizer synchronizer = new Synchronizer()
        MosaikPart mosaikPart = synchronizer as MosaikPart

        expect:
        !synchronizer.noInputs

        mosaikPart.setNoInputFlag()
        synchronizer.noInputs
    }

    def "The mosaik part of the synchronizer should set the no ouput flag correctly"() {
        given:
        Synchronizer synchronizer = new Synchronizer()
        MosaikPart mosaikPart = synchronizer as MosaikPart

        synchronizer.updateNextTickSIMONA(Optional.of(100L))

        expect:
        !synchronizer.noOutputs
        synchronizer.outputNextTick()

        mosaikPart.setNoOutputFlag()
        synchronizer.noOutputs
        !synchronizer.outputNextTick()
    }

    def "The mosaik part of the synchronizer should set the mosaik step size correctly"() {
        given:
        Synchronizer synchronizer = new Synchronizer()
        MosaikPart mosaikPart = synchronizer as MosaikPart

        expect:
        synchronizer.mosaikStepSize == 0L

        mosaikPart.setMosaikStepSize(100L)
        synchronizer.mosaikStepSize == 100L

        mosaikPart.setMosaikStepSize(953L)
        synchronizer.mosaikStepSize == 953L
    }
}
