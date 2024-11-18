/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simpleextsim.data;

import edu.ie3.datamodel.models.value.PValue;
import edu.ie3.simona.api.data.ExtInputDataValue;
import edu.ie3.simona.api.data.em.EmDataFactory;
import edu.ie3.simona.api.exceptions.ConversionException;

public class SimpleEmDataFactory implements EmDataFactory {

  @Override
  public PValue convert(ExtInputDataValue entity) throws ConversionException {
    if (entity instanceof SimpleExtSimValue simpleEntity) {
      if (simpleEntity.value() instanceof PValue pValue) {
        return pValue;
      } else {
        throw new ConversionException("This factory can only convert PValue entities.");
      }
    } else {
      throw new ConversionException("This factory can only convert SimpleExtSimValue entities.");
    }
  }
}
