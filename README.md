## Loads STIX-2.1 to a Neo4j graph database

This application **StixToNeoDB**, loads [STIX-2.1](https://docs.google.com/document/d/1yvqWaPPnPW-2NiVCLqzRszcx91ffMowfT5MmE9Nsy_w/edit#) 
objects and relations from json and zip files into a [Neo4j](https://neo4j.com/) graph database. 

The [OASIS](https://www.oasis-open.org/) open standard Structured Threat Information Expression [STIX-2.1](https://docs.google.com/document/d/1yvqWaPPnPW-2NiVCLqzRszcx91ffMowfT5MmE9Nsy_w/edit#) 
is a language for expressing cyber threat and observable information.

[Neo4j](https://neo4j.com/) "is a highly scalable native graph database that leverages data 
relationships as first-class entities, helping enterprises build intelligent applications 
to meet today’s evolving data challenges."
In essence, a graph database and processing engine that is used here for storing Stix objects 
and their relationships.
 
**StixToNeoDB** converts [STIX-2.1](https://docs.google.com/document/d/1yvqWaPPnPW-2NiVCLqzRszcx91ffMowfT5MmE9Nsy_w/edit#) 
domain objects (SDO) and relationships (SRO) to [Neo4j](https://neo4j.com/) nodes and relations 
using the [Java Neo4j API](https://neo4j.com/docs/java-reference/current/javadocs/). This allows for creating a new database or for adding new nodes and relations to an existing Neo4j graph database.
         
Once the Stix objects are in a Neo4j graph database, you can use the [export tools](https://github.com/jexp/neo4j-shell-tools) to          
convert them into GraphML, Cypher statements, CSV and binary formats. See also 
[how-2-convert](how-2-convert.md) for some explanations on how to convert into those formats.         
                         
### References
 
1) [Neo4j](https://neo4j.com/)

2) [Java Neo4j API](https://neo4j.com/docs/java-reference/current/javadocs/)

3) [ScalaStix](https://github.com/workingDog/scalastix)

4) [STIX-2.1](https://docs.google.com/document/d/1yvqWaPPnPW-2NiVCLqzRszcx91ffMowfT5MmE9Nsy_w/edit)

### Dependencies and requirements

Depends on the scala [ScalaStix](https://github.com/workingDog/scalastix) library
(included in the "lib" directory).

Java 8 is required and Neo4j-3.2.1 needs to be installed.

See also the build file.

### Installation and packaging

The easiest way to compile and package the application from source is to use [SBT](http://www.scala-sbt.org/).
To assemble the application and all its dependencies into a single jar file type:

    sbt assembly

This will produce "stixtoneodb-1.0.jar" in the "./target/scala-2.12" directory.

For convenience a **"stixtoneodb-1.0.jar"** file is in the "distrib" directory ready for use.

### Usage

To load the Stix objects into a Neo4j graph database, simply type at the prompt:
 
    java -jar stixtoneodb-1.0.jar --csv stix_file.json db_dir
    or
    java -jar stixtoneodb-1.0.jar --zip stix_file.zip db_dir
 
With the option **--csv** the input file "stix_file.json" is the file containing a 
bundle of Stix objects you want to convert, and "db_dir" is the location path to the neo4j database directory.
If "db_dir" is absent, the default output directory will be in the current directory with the name "stixdb". 

With the option **--zip** the input file must be a zip file with one or more entry files containing a single bundle of Stix objects 
in each.
  
If the database already exists, the data will be added to it, otherwise a new neo4j database will be created.  

To view the data, launch the Neo4j-3.2.1 app, select your "db_dir" as the database 
location and click start. Once the status is started, open a browser on "http://localhost:7474". 

 #### For very large files
 
 To process very large files use the following options:
 
     java -jar stixtoneodb-1.0.jar --csvx stix_file.json db_dir
     or
     java -jar stixtoneodb-1.0.jar --zipx stix_file.zip db_dir
 
 With the **--csvx** option the input file must contain a Stix object on one line 
 ending with a new line. Similarly when using the **--zipx** option, each input zip file entries must 
 contain a Stix object on one line ending with a new line. When using these options 
 the processing is done one line at a time.
 
### Status

no testing done.

Using Scala 2.12, Java 8, SBT-0.13.15 and Neo4j-3.2.1.


