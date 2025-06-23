package edu.ie3.simosaik.synchronisation

import edu.ie3.simona.api.data.ExtDataContainerQueue
import edu.ie3.simona.api.data.container.ExtInputDataContainer
import edu.ie3.simona.api.data.container.ExtResultContainer
import edu.ie3.simosaik.initialization.InitialisationData
import spock.lang.Specification

class SIMONAPartTest extends Specification {

    def "The SIMONA part of the synchronizer should retrieve initialisation data correctly"() {
        given:
        SIMONAPart simonaPart = new Synchronizer()

        simonaPart.initDataQueue.put(new InitialisationData.FlexInitData(3600L, false))

        when:
        def data = simonaPart.getInitialisationData(InitialisationData.FlexInitData)

        then:
        data.stepSize() == 3600L
        !data.disaggregate()
    }

    def "The SIMONA part of the synchronizer should set the data queues correctly"() {
        given:
        SIMONAPart simonaPart = new Synchronizer()

        ExtDataContainerQueue<ExtInputDataContainer> queueToSIMONA = new ExtDataContainerQueue<>()
        ExtDataContainerQueue<ExtResultContainer> queueToExt = new ExtDataContainerQueue<>()

        when:
        simonaPart.setDataQueues(queueToSIMONA, queueToExt)

        then:
        simonaPart.queueToSimona == queueToSIMONA
        simonaPart.queueToExt == queueToExt
    }

}
