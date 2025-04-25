/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik;

import edu.ie3.simona.api.data.mapping.DataType;
import java.util.function.Supplier;
import java.util.stream.Stream;

public enum SimonaEntity {
  PRIMARY_P("ActivePower"),
  PRIMARY_PH("ActivePowerAndHeat"),
  PRIMARY_PQ("ComplexPower"),
  PRIMARY_PQH("ComplexPowerAndHeat"),

  EM_SETPOINT("EmSetpoint"),
  EM_COMMUNICATION("EmCommunication"),

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
      case PRIMARY_P, PRIMARY_PH, PRIMARY_PQ, PRIMARY_PQH -> DataType.EXT_PRIMARY_INPUT;
      case EM_SETPOINT -> DataType.EXT_EM_INPUT;
      case EM_COMMUNICATION -> DataType.EXT_EM_COMMUNICATION;
      case GRID_RESULTS, NODE_RESULTS, LINE_RESULTS -> DataType.EXT_GRID_RESULT;
      case PARTICIPANT_RESULTS -> DataType.EXT_PARTICIPANT_RESULT;
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
              case "em_setpoint" -> EM_SETPOINT;
              case "Communication", "communication" -> EM_COMMUNICATION;
              case "Grid", "grid" -> GRID_RESULTS;
              case "Participant", "participant" -> PARTICIPANT_RESULTS;
              default -> throw new IllegalArgumentException("Unknown model type: " + modelType);
            };

    return Stream.of(SimonaEntity.values())
        .filter(e -> e.name.equals(modelType))
        .findFirst()
        .orElseGet(fallback);
  }
}
