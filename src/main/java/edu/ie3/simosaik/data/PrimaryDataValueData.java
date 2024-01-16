package edu.ie3.simosaik.data;

import edu.ie3.datamodel.io.factory.FactoryData;
import edu.ie3.datamodel.models.value.Value;

import java.util.Map;

public class PrimaryDataValueData<V extends Value> extends FactoryData {
    public PrimaryDataValueData(Map<String, String> fieldsToAttributes, Class<V> valueClass) {
        super(fieldsToAttributes, valueClass);
    }

    public String toString() {
        return "PrimaryDataValueData{fieldsToAttributes=" + this.getFieldsToValues() + "} ";
    }
}
