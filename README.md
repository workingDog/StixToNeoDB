## Loads STIX-2 to a Neo4j graph database

This application **StixToNeoDB**, loads [STIX-2](https://oasis-open.github.io/cti-documentation/) 
objects and relations from json and zip files into a [Neo4j](https://neo4j.com/) graph database. 

The [OASIS](https://www.oasis-open.org/) open standard Structured Threat Information Expression [STIX-2](https://oasis-open.github.io/cti-documentation/) 
is a language for expressing cyber threat and observable information.

[Neo4j](https://neo4j.com/) "is a highly scalable native graph database that leverages data 
relationships as first-class entities, helping enterprises build intelligent applications 
to meet today’s evolving data challenges."
In essence, a graph database and processing engine that is used here for storing Stix objects 
and their relationships.
 
**StixToNeoDB** converts files of [STIX-2](https://oasis-open.github.io/cti-documentation/) 
domain objects (SDO) and relationships (SRO) to [Neo4j](https://neo4j.com/) nodes and relations 
using the [Java Neo4j API](https://neo4j.com/docs/java-reference/current/javadocs/). 
This allows for creating a new database or for adding new nodes and relations to 
an existing Neo4j graph database. The file types include files containing 
 STIX-2 bundles in JSON format and zip files.
      
### Tools  
       
Once the Stix objects are in a Neo4j graph database you can use the built-in tools to visualise and 
analyse the data. Other tools such as the [Tinkerpop](http://tinkerpop.apache.org/) framework and [Spark GraphX](https://spark.apache.org/graphx/) 
can easily link to the neo4j data for very large data sets processing. 
You can also use these [export tools](https://github.com/jexp/neo4j-shell-tools) to export the data into GraphML, Cypher statements, CSV and binary formats. See also 
[how-2-convert](how-2-convert.md) for some explanations on how to convert Stix objects into those formats.         
     
                         
### References
 
1) [Neo4j](https://neo4j.com/)

2) [Java Neo4j API](https://neo4j.com/docs/java-reference/current/javadocs/)

3) [ScalaStix](https://github.com/workingDog/scalastix)

4) [StixToNeoLib](https://github.com/workingDog/StixToNeoLib)

5) [STIX-2](https://oasis-open.github.io/cti-documentation/)

### Dependencies and requirements

Depends on [ScalaStix](https://github.com/workingDog/scalastix), [StixToNeoLib](https://github.com/workingDog/StixToNeoLib) and 
the associated [Neo4j Community](https://mvnrepository.com/artifact/org.neo4j/neo4j) jar file.

See also the *build.sbt* file.

Java 8 is required and Neo4j Community should be installed to process the results.

### Installation and packaging

To assemble the application and all its dependencies into a single fat jar file, use [SBT](http://www.scala-sbt.org/) and type:

    sbt assembly

This will produce "stixtoneodb-2.0.jar" in the "./target/scala-2.12" directory.

For convenience a **"stixtoneodb-2.0.jar"** file is in the "distrib" directory ready for use.

### Usage

To load the Stix objects into a Neo4j graph database, simply type at the prompt:
 
    java -jar stixtoneodb-2.0.jar --json stix_file.json db_dir
    or
    java -jar stixtoneodb-2.0.jar --zip stix_file.zip db_dir
 
With the option **--json** the input file "stix_file.json" is the file containing a 
bundle of Stix objects you want to convert, and "db_dir" is the location path to the neo4j database directory.
If "db_dir" is absent, the default output directory will be in the current directory with the name "stixdb". 

With the option **--zip** the input file must be a zip file with one or more entry files containing a single bundle of Stix objects 
in each.
  
If the database already exists, the data will be added to it, otherwise a new neo4j database will be created. 
An existing database must not be "opened" by another process. 

Note that **StixToNeoDB** will try to "skip" errors in the objects and relations of the input file, 
e.g. references to non existent objects. The log of the processing can be found in **application.log** 
in the logs directory.

To view the data in Neo4j, launch the Neo4j Community Edition application, select your "db_dir" as the database 
location and click start. Once the status is "Started", open a browser on "http://localhost:7474". 

 #### For very large files
 
 To process very large files use the following experimental options:
 
     java -jar stixtoneodb-2.0.jar --jsonx stix_file.json db_dir
     or
     java -jar stixtoneodb-2.0.jar --zipx stix_file.zip db_dir
 
 With the **--jsonx** option the input file must contain a Stix object on one line 
 ending with a new line. Similarly when using the **--zipx** option, each input zip file entries must 
 contain a Stix object on one line ending with a new line. When using these options 
 the processing is done one line at a time.
 
### Status

work in progress.

only tested on the small ./stix-files/testfull.json



