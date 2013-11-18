
## hyzhenhok.db/construct-txns

This constructor function is used in `construct-block`:

   `:block/txns (construct-txns db (:txns block))`

It returns a vector of constructed txns, and it only makes sense witinin the context of the block constructor because it increments the :txn/idx, :txIn/idx, and :txOut/idx since they're ordered in a block.

The problem I'm trying to solve here is that I've implemented the db schema such that txnIns actually point to a txOut. For every txin, the Bitcoin network gives us only a :txn-hash and a :txout-idx, but I don't want to look that up at query-time if I can do it now.

When inserting a txn (1+ txins) into the db, I use each txin's :txn-hash and :txout-idx to lookup the specified :txOut entity in the db so that I can construct the txin's :txIn/prevTxOut.

However, since txIns can point to txOuts *within the same block*, (thus txOuts earlier in this constructor's loop), I append each constructed txn to the db value on each iteration so that the query `(find-txout-by-hash-and-idx db)` will query against the actual persisted database and all previously-constructed txns as if it all was in the same database.
