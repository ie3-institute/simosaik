# Models

Simosaik can provide mosaik with SIMONA models. Due to the limitations of the internal mapping, that is used during the
translation from mosaik to SIMONA and vise versa, most of the models provided by simosaik are either input or result models.
Which can either provide SIMONA with input data or mosaik with result data.

All models support the [tiered times](https://mosaik.readthedocs.io/en/latest/explanations/tiered-time.html) concept of
mosaik.


An overview of all available attributes with their values can be found [here](/attributes).


## Limitations

There are currently these limitations:

1. For now only the energy management models support bidirectional data exchange.
2. The order in which simosaik handles the data exchange needs to be considered. First, primary data is handles, then em data and lastly result data is sent to mosaik.


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

* - Results
  - "res", "results"
  - u[pu], u[RAD], I[A], I[RAD], Congestion, P[MW], Q[MVAr], P_th[MW], SOC[%]
  - The attributes for which an output is given, depends on the actual asset.
```

## Energy management models

The energy management models can support both input and output attributes at the same time. Currently, we support these
energy management models:

```{list-table}
:widths: auto
:class: wrapping
:header-rows: 1

* - Model name
  - Possible arguments in mosaik
  - Input attributes
  - Output attributes
  - Additional information

* - EM
  - "em"
  - Flex[request], EM[setPoint]
  - Flex[options], Simona[nextTick]
  -

* - EmCommunication
  - "communication", "Communication"
  - Flex[request], Flex[options], EM[setPoint], Flex[com]
  - Flex[request], Flex[options], EM[setPoint], Flex[com], Simona[nextTick]
  -
  
* - EmOptimizer
  - "em_optimizer"
  - Flex[request], EM[setPoint]
  - Flex[options], Simona[nextTick]
  -
```
