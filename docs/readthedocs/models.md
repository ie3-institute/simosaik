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

* - Active
  - "p", "P"
  - P[MW]
  - These models can only provide SIMONA with active power.

* - Complex
  - "pq", "PQ", "Pq"
  - P[MW], Q[MVAr]
  - These models can provide SIMONA with active and/or reactive power.
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

* - Grid results
  - "grid", "Grid"
  - u[pu]
  - Currently, only node voltages supported.
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

* - Em communication
  - "Communication", "communication"
  - "Flex[request]", "Flex[options]", "Flex[setPoint]"
  - 

```
