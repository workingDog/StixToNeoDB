## To convert Stix-2.0 objects to graphML, Cypher statements, CSV and binary.

Download [neo4j-3.2.1](https://neo4j.com/download/other-releases/) from the zip or tar file, 
not the exe or dmg.

Untar/unzip it into a directory of your choice, let say, "myneo4j"

Setup the tools required for exporting to other formats, go to [neo4j-shell-tools](https://github.com/jexp/neo4j-shell-tools)
and download the "neo4j-shell-tools_3.0.1.zip" file.

Unzip neo4j-shell-tools.zip and copy the 4 jar files into your "myneo4j/lib" directory.

Setup your path variables, 

e.g. macOS

    export NEO4J_HOME=/Users/yourname/myneo4j/neo4j-community-3.2.1
    export PATH=$PATH:$NEO4J_HOME/bin:$NEO4J_HOME/lib

This makes the [neo4j-shell-tools](https://github.com/jexp/neo4j-shell-tools) available.

Now generate a neo4j graph database from your Stix objects file using [StixToNeoDB](https://github.com/workingDog/StixToNeoDB)

    java -jar stixtoneodb-1.0.jar --json stix_file.json stixdb

This will produce a neo4j database in the "stixdb" directory.

After that, in a terminal type:

    neo4j-shell -path /path_to.../stixdb

Then for example exporting to graphML format, type at the shell prompt: 

    $ export-graphml -o out.graphml

This will give you all your data in graphML format, you can then open "out.graphml" using Gephi.

Other [export commands](https://github.com/jexp/neo4j-shell-tools#export) are available, 

    To export your data as Cypher statements, use the Cypher Export command.
    To export your data as CSV, use the Cypher Import command with the -o file option which will output the results of your queries into a CSV file.
    To export your data as GraphML, use the GraphML Export command.
    To export your data as a binary file, use the Export Binary command.
