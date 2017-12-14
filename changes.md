Change Log
==========

### changes in 2.0

* use stixtoneolib library for processing 


### changes in 1.0

* remove all circe dependencies
* add Play json dependency
* replace all circe code with Play json code
* added counting the nodes
* added custom properties
* added the missing hashes of ExternalReference
* remove description from ObservablesMaker
* added custom (for custom properties x_...) to ObservablesMaker
* update to scalastix-0.7, for STIX-2.0 specs 
* due to STIX-2.0 specs, 
** modify object_refs as optional for Report
** comment out the lang and confidence parameters.

* updated scala, sbt, plugins