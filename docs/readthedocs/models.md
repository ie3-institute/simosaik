# Models

Simosaik can provide mosaik with SIMONA models. Due to the limitations of the internal mapping, that is used during the
translation from mosaik to SIMONA and vise versa, most of the models provided by simosaik are either input or result models.
Which can either provide SIMONA with input data or mosaik with result data.

Some specific models, like the energy management communication model, can do both.

An overview of all available attributes with their values can be found [here](/attributes).

## Input models

Input models are used to provide SIMONA with external data. Therefore, these models are only supporting input attributes.
Currently, we support these input models:

```{list-table}
:widths: auto
:class: wrapping
:header-rows: 1

* - Model name
  - Possible arguments in mosaik
  - Input attributes
  - Additional information

* - ActivePower
  - "p", "P"
  - P[MW]
  - These models can only provide SIMONA with active power.

* - ActivePowerAndHeat
  - "ph", "PH", "Ph"
  - P[MW], P_th[MW]
  - These models can provide SIMONA with active power and thermal power (heat).

* - ComplexPower
  - "pq", "PQ", "Pq"
  - P[MW], Q[MVAr]
  - These models can provide SIMONA with active and/or reactive power.

* - ComplexPowerAndHeat
  - "pqh", "PQH", "Pqh"
  - P[MW], Q[MVAr], P_th[MW]
  - These models can provide SIMONA with active and/or reactive power and thermal power (heat).
```

## Result models

Result models are used to provide mosaik with SIMONA data. Therefore, these models are only supporting output attributes.
Currently, we support these result models:

```{list-table}
:widths: auto
:class: wrapping
:header-rows: 1

* - Model name
  - Possible arguments in mosaik
  - Output attributes
  - Additional information

* - GridResults
  - "grid", "Grid"
  - u[pu], u[RAD], I[A], I[RAD]
  - The attributes for which an output is given, depends on the actual asset.

* - NodeResults
  - "node_res", "Node_res"
  - u[pu], u[RAD]
  - Only returns node results.

* - LineResults
  - "line_res", "Line_res"
  - I[A], I[RAD]
  - Only returns line results.

* - ParticipantResults
  - "participant", "Participant"
  - P[MW], Q[MVAr], P_th[MW], SOC[%]
  - The attributes for which an output is given, depends on the actual participant.
```

## Energy management models

The energy management models can support both input and output attributes at the same time. Currently, we support these
result models:

```{list-table}
:widths: auto
:class: wrapping
:header-rows: 1

* - Model name
  - Possible arguments in mosaik
  - Input attributes
  - Output attributes
  - Additional information

* - EmSetpoint
  - "em_setpoint"
  - EM[setPoint]
  - Flex[options], Flex[diaggregated]
  -

* - EmCommunication
  - "communication", "Communication"
  - Flex[request], Flex[options], EM[setPoint]
  - Flex[request], Flex[options], EM[setPoint]
  -
  
* - EmOptimizer
  - "em_optimizer"
  - P[MW], Q[MVAr], EM[setPoint]
  - PMin[MW], PRef[MW], PMax[MW], idToPMin[MW], idToPRef[MW], idToPMax[MW], Flex[options], Flex[diaggregated]
  -
```
