## Loads STIX-2 to a Neo4j graph database

This command prompt application **StixToNeoDB**, loads [STIX-2](https://oasis-open.github.io/cti-documentation/) 
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
   
### Installation and usage

Ensure you have Java 11 installed and the latest [SBT](http://www.scala-sbt.org/) on your system.
Clone or download the **StixToNeoDB** files from this repository.

### Usage

#### Preferred method using SBT 

In the StixToNeoDB directory type:

    sbt "run -f localhost:7687 stix_file db_dir" 

This will compile and run **StixToNeoDB** with the given parameters.
See below for the parameters description.
 
#### Making a large java **jar** file 

If you really need a single java file to run **StixToNeoDB**, then first 
compile from source code and assemble the application and all its dependencies 
into a single fat **jar** file. Use [SBT](http://www.scala-sbt.org/) and type:
   
    sbt assembly
   
This will produce *stixtoneodb-6.0.jar* in the *./target/scala-2.13* directory 
that you can use to load Stix objects into a Neo4j. 

To load your Stix objects data into a Neo4j graph database, type at the prompt where 
you have put the *stixtoneodb-6.0.jar*:
 
    java -jar stixtoneodb-6.0.jar -f hostAddress stix_file db_dir
    or
    java -jar stixtoneodb-6.0.jar -x hostAddress stix_file db_dir

#### Parameters 

**hostAddress** is of the form **localhost:7687** and specifies the address 
of the local database service to listen to.

With the option **-f** the input file *stix_file* must be a file containing the Stix objects data that you want to convert, 
and *db_dir* is the location path to the Neo4j database directory.
The input file can be a text file containing a single bundle in json format or a zip file containing one or more 
bundle files (.zip). Only *.json* and *.stix* files in the zip file are processed.

If *db_dir* is absent, the default output directory will be in the current directory with the name *stixdb*. 
If the database already exists, the data will be added to it, otherwise a new neo4j database will be created. 
An existing database must not be "opened" by another process during processing. 

The **-x** option is for the **experimental** processing of large file one line at a time.
The input file must contain a Stix object on one line 
ending with a new line. Similarly, if the input file is a zip file, each zip file entry must 
contain Stix objects on one line ending with a new line. 

Note that **StixToNeoDB** will try to "skip" errors in the objects and relations of the input file, 
e.g. references to non-existent objects. The log of the processing can be found in *application.log* 
in the *logs* directory.

To view the data in Neo4j, launch the Neo4j Community Edition server, 
then open a browser on *http://localhost:7474*, select your *db_dir* as the database to view.

### Dependencies and requirements

Requires Java 11 or greater.

Depends on [StixToNeoLib](https://github.com/workingDog/StixToNeoLib) which depends on 
[ScalaStix](https://github.com/workingDog/scalastix), and 
the associated [Neo4j Community 3.3.9](https://mvnrepository.com/artifact/org.neo4j/neo4j) jar file.

Since **StixToNeoDB** relies on the **neo4j-3.3.9** jar file, use 
Neo4j Community (3.5.19) server with the **dbms.allow_upgrade=true** in 
the **neo4j.conf** to process the results.
          
### References
 
1) [Neo4j](https://neo4j.com/)

2) [Java Neo4j API](https://neo4j.com/docs/java-reference/current/javadocs/)

3) [ScalaStix](https://github.com/workingDog/scalastix)

4) [StixToNeoLib](https://github.com/workingDog/StixToNeoLib)

5) [STIX-2](https://oasis-open.github.io/cti-documentation/)


### Status

work in progress

