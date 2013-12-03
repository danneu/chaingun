
# Install & Import Demo

### (This uses an old version of the import code that I haven't yet pruned, so I doubt this demo will work)

I'll walk through the process of:

1. Downloading and compiling chaingun
2. Setting up Datomic (database)
3. Parsing the first 300 blocks of the blockchain into Datomic
4. Exploring the local blockchain with chaingun's explorer.

Clone the chaingun source :

    $ git clone https://github.com/danneu/chaingun.git ~/chaingun

(For the rest of this guide, I'll now pretend `~/chaingun` exists)

Compile chaingun:

    $ lein uberjar

(It should say that it created `~/chaingun/chaingun-standalone.jar`)

Download Datomic:

- [Download the lastest datomic-free-x.x.xxxx.zip](https://my.datomic.com/downloads/free) (~40mb) - You can just unzip it into `~/chaingun/`.

Launch the Datomic transactor in another terminal window:

    $ ~/<path-to-unzipped-datomic>/bin/transactor config/samples/free-transactor-template.properties

You should see output that looks something like:

    Launching with Java options -server -Xms1g -Xmx1g -XX:NewRatio=4 ...
    Starting datomic:free://localhost:4334/<DB-NAME>, storing data in: data ...
    System started datomic:free://localhost:4334/<DB-NAME>, storing data in: data

(The default config is fine for this 300-block demo.)

With the Datomic transactor running in another terminal, tell chaingun to seed the db:

    $ java -jar chaingun-standalone.jar db:seed

(If it doesn't work, ensure the Datomic transactor is actually running from previous step)

You should see this output:

    Creating database...
    Creating genesis block...Done.
    Creating the first 299 post-genesis blocks...
    ............................................
    ............................................
    ...........Done.
    Blocks in database: 300

Launch the local blockchain explorer:

    $ java -jar chaingun-standalone.jar explorer
    Launching database explorer at http://localhost:3000/...

You should now be able to open [http://localhost:3000/](http://localhost:3000/) in your browser and browse the first 300 blocks of the Bitcoin network.
