# hyzhenhok

A highly experimental/unorganized fledgling Bitcoin implementation written in Clojure. A sandbox for my brain.

Predominantly a test-bed for hilarious abstractions and unfamiliar libraries as I figure things out.

## Current features so far

- Implements most of the wire protocol (`codec.clj`) and can talk to nodes (like exchanging the version/verack handshake and downloading blocks)
- Stores the Bitcoin blockchain in a Datomic database
- Parses serialized blocks from `~/.bitcoin/blkXXXXX.dat` files and can insert them into its db
- Exposes a blockexplorer.com-like interface for browsing the contents of its db locally (`explorer.clj`)
- Includes a simple validation stack-machine (`script.clj`)

## Current goals

- **Import full blockchain.** - While I've been able to parse every `blkXXXXX.dat` file that I've tried, I haven't yet attempted to parse all of them into the database at once.
- **Implement OP_CHECKSIG and other non-trival script ops.** - The abstraction in script.clj is pleasant and makes almost all of the ops trivial to implement, but the non-trivial ops need to be implemented to justify the abstraction before moving on.

## Prerequisites

1. Java JDK version 6 or later.
2. To compile hyzhenhok into a `.jar`, you'll need Clojure's build tool: [Leiningen](https://github.com/technomancy/leiningen)

## Install & Import Demo

### (This uses an old version of the import code that I haven't yet pruned, so I doubt this demo will work)

I'll walk through the process of:

1. Downloading and compiling hyzhenhok
2. Setting up Datomic (database)
3. Parsing the first 300 blocks of the blockchain into Datomic
4. Exploring the local blockchain with hyzhenhok's explorer.

Clone the hyzhenhok source :

    $ git clone https://github.com/danneu/hyzhenhok.git ~/hyzhenhok

(For the rest of this guide, I'll now pretend `~/hyzhenhok` exists)

Compile hyzhenhok:

    $ lein uberjar

(It should say that it created `~/hyzhenhok/hyzhenhok-standalone.jar`)

Download Datomic:

- [Download the lastest datomic-free-x.x.xxxx.zip](https://my.datomic.com/downloads/free) (~40mb) - You can just unzip it into `~/hyzhenhok/`.

Launch the Datomic transactor in another terminal window:

    $ ~/<path-to-unzipped-datomic>/bin/transactor config/samples/free-transactor-template.properties

You should see output that looks something like:

    Launching with Java options -server -Xms1g -Xmx1g -XX:NewRatio=4 ...
    Starting datomic:free://localhost:4334/<DB-NAME>, storing data in: data ...
    System started datomic:free://localhost:4334/<DB-NAME>, storing data in: data

(The default config is fine for this 300-block demo.)

With the Datomic transactor running in another terminal, tell hyzhenhok to seed the db:

    $ java -jar hyzhenhok-standalone.jar db:seed

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

    $ java -jar hyzhenhok-standalone.jar explorer
    Launching database explorer at http://localhost:3000/...

You should now be able to open [http://localhost:3000/](http://localhost:3000/) in your browser and browse the first 300 blocks of the Bitcoin network.

## Screenshots

### Local Blockchain Explorer

![Block view](http://i.imgur.com/LlzQrwZ.png)

![Txn view](http://i.imgur.com/w8gHSth.png)
