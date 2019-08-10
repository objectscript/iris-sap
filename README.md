# iris-sap
IRIS SAP Adapters (very 1st published ENG version)

This is a very prototype (*working prototype*) to deal any conversations with SAP products using IDoc format.

## Features
* Bi-Directional communication between IRIS and SAP using IDocs.
* Cross-Platform (because of Java).

## Limitations
* You have to use IRIS Data Platform 2018.1 or above.
* You have to have Java JRE installed.
* You should be able to download SAP libraries (sapjco3 & sapidoc3 JARs) from sap.com.
* No support for raw ABAP/RFC. IDocs only.

## Build
* Make sure JDK is installed.
* Edit build/build.bat to meet your environment, then execute it.

## Installation
* SAPExchange.jar (exists in repository) & sapjco3.jar & sapidoc3.jar are required.
* You have to ask your SAP representative to provide correct xxx.jcoDestination and yyy.jcoServer configuration files.
* You must check that there is no connection/firewall problems.
* Create new production and import SAPExchange.jar as Java Business Hosts as described in Documentation.
* You have to check and/or modify settings of Production and any Business Components.

## Remarks
* SAPOperation has no pointer to Production in 2018.1, so we're writing logs to file.
* I'll update this repository with new code including IncomingProcess soon.


