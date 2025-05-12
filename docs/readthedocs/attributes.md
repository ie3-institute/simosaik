# Attributes

This page will give you an overview of all the attributes, that are currently available. Each attribute specifies a unit,
e.g. `[MW]`.

## Universal attributes

These attribute can be used for input and output models.

```{list-table}
:widths: auto
:class: wrapping
:header-rows: 1

* - Type
  - Attributes
  - Value
  - Additional information

* - Active power
  - P[MW]
  - float
  - Active power in MW

* - Reactive power
  - Q[MVAr]
  - float
  - Reactive power in MVAr

* - Thermal power
  - P_th[MW]
  - float
  - Thermal power in MW

* - Delay
  - delay[ms]
  - float
  - Delay in milliseconds.
```

## Result attributes

These attribute can be only be used for output models.

```{list-table}
:widths: auto
:class: wrapping
:header-rows: 1

* - Type
  - Attributes
  - Value
  - Additional information

* - Voltage magnitude
  - u[pu]
  - float
  - The magnitude of the voltage in per-unit.

* - Voltage angle
  - u[RAD]
  - float
  - The angle of the voltage in radians.

* - Current magnitude
  - I[A]
  - float
  - The magnitude of the current in ampere.

* - Current angle
  - I[RAD]
  - float
  - The angle of the current in radians.

* - State-of-charge
  - SOC[%]
  - float
  - The state-of-charge in percent.
```

## Energy management attributes

These attributes can be only be used for energy management models. Some of the attributes use dictionaries. The structure
of these can be found [here](#energy-management-flex-dictionaries).

```{list-table}
:widths: auto
:class: wrapping
:header-rows: 1

* - Type
  - Attribute
  - Value
  - Additional information

* - Flexibility requests
  - Flex[request]
  - str
  - The sender of the flexibility request.

* - Flexibility options
  - Flex[options]
  - flex option dict
  - The sender is needed, the other keys can be dropped.

* - Flexibility options disaggregated
  - Flex[disaggregated]
  - disaggregated flex option dict
  - The sender is needed, the other keys can be dropped.

* - Energy management set points
  - EM[setPoint]
  - set point dict
  - Currently, SIMONA only supportes active power for set points. The reactive power value is currently ingnored.

* - Minimal flex option
  - PMin[MW]
  - float
  - Minimal active power.
  
* - Reference flex option
  - PRef[MW]
  - float
  - Current (reference) active power.
  
* - Maximal flex option
  - PMax[MW]
  - float
  - Maximal active power.

* - Disaggregated minimal flex option
  - idToPMin[MW]
  - idToPMin dict
  - Mapping of asset ids to the minimal power.
  
* - Disaggregated reference flex option
  - idToPRef[MW]
  - idToPRef dict
  - Mapping of asset ids to the current (reference) active power.
  
* - Disaggregated maximal flex option
  - idToPMax[MW]
  - idToPMax dict
  - Mapping of asset ids to the maximal power.
```

### Energy management flex dictionaries

Below are the structures of all types of dictionaries used for the energy management attributes. For each dictionary the
keys with their corresponding value type is given. The dictionaries used for the communication model support adding a delay
information.

**idToPMin dict:** <br>
```python
# dict: asset eid to minimal flex options
# the aggregated minimal flex option has the key `EM`
{ asset_eid: float }
```

**idToPRef dict:** <br>
```python
# dict: asset eid to reference flex options
# the aggregated reference flex option has the key `EM`
{ asset_eid: float }
```

**idToPMax dict:** <br>
```python
# dict: asset eid to maximal flex options
# the aggregated maximal flex option has the key `EM`
{ asset_eid: float }
```

**Set point dict:** <br>
There are multiple options for the values of the set point dictionary.
```python 
Option 1: { P[MW]: float, Q[MVAr]: float }
Option 2: { P[MW]: float }
Option 3: { Q[MVAr]: float }
Option 4: { P[MW]: float, Q[MVAr]: float, delay[ms]: float }
```

**Flex option dict:** <br>
There are multiple options for the values of the flex option dictionary.
```python
Option 1: { 
    sender: str, 
    PMin[MW]: float, 
    PRef[MW]: float, 
    PMax[MW]: float
}

Option 2: {
    sender: str,
    PMin[MW]: float,
    PRef[MW]: float,
    PMax[MW]: float, 
    delay[ms]: float
}
```

**Disaggregated flex option dict:** <br>
There are multiple options for the values of the disaggregated flex option dictionary.
```python
Option 1: { 
    sender: str, 
    idToPMin[MW]: idToPMin dict, 
    idToPRef[MW]: idToPRef dict, 
    idToPMax[MW]: idToPMax dict
}

Option 2: {
    sender: str,
    idToPMin[MW]: idToPMin dict,
    idToPRef[MW]: idToPRef dict,
    idToPMax[MW]: idToPMax dict,
    delay[ms]: float
}
```
