/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik;

import edu.ie3.simona.api.mapping.DataType;
import java.util.function.Supplier;
import java.util.stream.Stream;

public enum SimonaEntity {
  PRIMARY_P("ActivePower"),
  PRIMARY_PH("ActivePowerAndHeat"),
  PRIMARY_PQ("ComplexPower"),
  PRIMARY_PQH("ComplexPowerAndHeat"),
  EM("EM"),
  EM_COMMUNICATION("EmCommunication"),
  EM_OPTIMIZER("EmOptimizer"),

  GRID_RESULTS("GridResults"),
  NODE_RESULTS("NodeResults"),
  LINE_RESULTS("LineResults"),

  PARTICIPANT_RESULTS("ParticipantResults");

  public final String name;

  SimonaEntity(String name) {
    this.name = name;
  }

  public static DataType toType(SimonaEntity simonaEntity) {
    return switch (simonaEntity) {
      case PRIMARY_P, PRIMARY_PH, PRIMARY_PQ, PRIMARY_PQH -> DataType.PRIMARY;
      case EM, EM_OPTIMIZER, EM_COMMUNICATION -> DataType.EM;
      case GRID_RESULTS, NODE_RESULTS, LINE_RESULTS, PARTICIPANT_RESULTS -> DataType.RESULT;
    };
  }

  public static SimonaEntity parseType(String modelType) {
    Supplier<SimonaEntity> fallback =
        () ->
            switch (modelType) {
              case "p", "P" -> PRIMARY_P;
              case "ph", "PH", "Ph" -> PRIMARY_PH;
              case "pq", "PQ", "Pq" -> PRIMARY_PQ;
              case "pqh", "PQH", "Pqh" -> PRIMARY_PQH;
              case "em" -> EM;
              case "Communication", "communication" -> EM_COMMUNICATION;
              case "em_optimizer" -> EM_OPTIMIZER;
              case "Grid", "grid" -> GRID_RESULTS;
              case "node_res", "Node_res" -> NODE_RESULTS;
              case "line_res", "Line_res" -> LINE_RESULTS;
              case "Participant", "participant" -> PARTICIPANT_RESULTS;
              default -> throw new IllegalArgumentException("Unknown model type: " + modelType);
            };

    return Stream.of(SimonaEntity.values())
        .filter(e -> e.name.equals(modelType))
        .findFirst()
        .orElseGet(fallback);
  }
}
