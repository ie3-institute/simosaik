# Models

Simosaik can provide mosaik with mosaik models. Due to the internal mapping, that is used during the translation from mosaik
to SIMONA and vise versa, most of the models provided by simosaik are either input or result models. Which can either 
provide SIMONA with input data or mosaik with result data.

Some specific models, like the energy management communication model, can do both.

## Input models

Currently, we support these input models:

```{list-table}
:widths: auto
:header-rows: 1

* - Model name
  - Possible arguments in mosaik
  - Supported units
  - Additional information

* - ActivePower
  - "p", "P"
  - "P[MW]"
  - These models can only provide SIMONA with active power.

* - ActivePowerAndHeat
  - "ph", "PH", "Ph"
  - "P[MW]", "P_th[MW]"
  - These models can provide SIMONA with active power and thermal power (heat).

* - ComplexPower
  - "pq", "PQ", "Pq"
  - "P[MW]", "Q[MVAr]"
  - These models can provide SIMONA with active and/or reactive power.

* - ComplexPowerAndHeat
  - "pqh", "PQH", "Pqh"
  - "P[MW]", "Q[MVAr]", "P_th[MW]"
  - These models can provide SIMONA with active and/or reactive power and thermal power (heat).
```

## Result models

Currently, we support these result models:

```{list-table}
:widths: auto
:header-rows: 1

* - Model name
  - Possible arguments in mosaik
  - Supported units
  - Additional information

* - GridResults
  - "grid", "Grid"
  - "u[pu]", "delta[RAD]", "I_Mag[A]", "I_Ang[RAD]"
  - Returns results for any grid asset.

* - NodeResults
  - "node_res", "Node_res"
  - "u[pu]", "delta[RAD]"
  - Only returns node results.

* - LineResults
  - "line_res", "Line_res"
  - "I_Mag[A]", "I_Ang[RAD]"
  - Only returns line results.

* - ParticipantResults
  - "participant", "Participant"
  - "P[MW]", "Q[MVAr]"
  - Returns the power of the participant.
```

## Energy management models

Currently, we support these result models:

```{list-table}
:widths: auto
:header-rows: 1

* - Model name
  - Possible arguments in mosaik
  - Supported units
  - Additional information

* - EmSetpoint
  - "em_setpoint"
  - "EM[setPoint]"

* - EmCommunication
  - "Communication", "communication"
  - "Flex[request]", "Flex[options]", "EM[setPoint]"
  - 
```
