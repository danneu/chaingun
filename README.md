# chaingun

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
2. To compile chaingun into a `.jar`, you'll need Clojure's build tool: [Leiningen](https://github.com/technomancy/leiningen)

## Install & Import Demo

[INSTALL.md](https://github.com/danneu/chaingun/blob/master/INSTALL.md) (Note: out of date)

Walks through the process of:

1. Downloading and compiling chaingun
2. Setting up Datomic (database)
3. Parsing the first 300 blocks of the blockchain into Datomic
4. Exploring the local blockchain with chaingun's explorer.

## Screenshots

### Local Blockchain Explorer

![Block view](http://i.imgur.com/LlzQrwZ.png)

![Txn view](http://i.imgur.com/w8gHSth.png)
