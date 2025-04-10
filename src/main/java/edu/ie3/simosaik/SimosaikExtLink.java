/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik;

import edu.ie3.simona.api.ExtLinkInterface;
import edu.ie3.simona.api.simulation.ExtSimAdapterData;
import edu.ie3.simosaik.config.ArgsParser;
import edu.ie3.simosaik.flexibility.FlexOptionOptimizerSimulation;

public class SimosaikExtLink implements ExtLinkInterface {
  private MosaikSimulation extSim;

  @Override
  public MosaikSimulation getExtSimulation() {
    return extSim;
  }

  @Override
  public void setup(ExtSimAdapterData data) {
    ArgsParser.Arguments arguments = ArgsParser.parse(data.getMainArgs());

    String mosaikIP = arguments.mosaikIP();
    int stepSize = arguments.stepSize();

    extSim =
        switch (arguments.simulation()) {
          case PRIMARY_RESULT -> {
            MosaikSimulator simulator = new MosaikSimulator("PrimaryResultSimulator", stepSize);
            yield new MosaikSimulation("PrimaryResultSimulation", mosaikIP, simulator);
          }
          case FLEX_COMMUNICATION -> {
            MosaikSimulator simulator = new MosaikSimulator("FlexCommunicationSimulator", stepSize);
            yield new MosaikSimulation("FlexCommunicationSimulation", mosaikIP, simulator);
          }
          case FLEX_OPTION_OPTIMIZER ->
              new FlexOptionOptimizerSimulation(
                  mosaikIP, stepSize, arguments.useFlexOptionEntitiesInsteadOfEmAgents());
        };

    extSim.setAdapterData(data);
  }
}
