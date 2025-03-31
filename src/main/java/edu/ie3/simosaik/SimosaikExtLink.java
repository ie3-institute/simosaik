/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik;

import edu.ie3.simona.api.ExtLinkInterface;
import edu.ie3.simona.api.simulation.ExtSimAdapterData;
import edu.ie3.simosaik.config.ArgsParser;
import edu.ie3.simosaik.flexibility.FlexCommunicationSimulation;
import edu.ie3.simosaik.flexibility.FlexCommunicationSimulator;
import edu.ie3.simosaik.flexibility.FlexOptionOptimizerSimulation;
import edu.ie3.simosaik.primaryResultSimulator.PrimaryResultSimulation;
import edu.ie3.simosaik.primaryResultSimulator.PrimaryResultSimulator;

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
              PrimaryResultSimulator simulator = new PrimaryResultSimulator(stepSize);
              yield new PrimaryResultSimulation(mosaikIP, simulator);
          }
          case FLEX_COMMUNICATION -> {
              FlexCommunicationSimulator simulator = new FlexCommunicationSimulator(stepSize);
              yield new FlexCommunicationSimulation(mosaikIP, simulator);
          }
          case FLEX_OPTION_OPTIMIZER ->
              new FlexOptionOptimizerSimulation(
                  mosaikIP,
                  stepSize,
                  arguments.useFlexOptionEntitiesInsteadOfEmAgents());
        };

    extSim.setAdapterData(data);
  }
}
