import java.util.*;
import java.util.stream.Collectors;

public class TxHandler {

    private UTXOPool pool;
    private List<Transaction> addedtx = new ArrayList<>();
    private boolean added;
    private Queue<Transaction> queue;
    Boolean doubleSpend = false;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     * <B> (collection of unspent transaction outputs) </B> ??
     */
    public TxHandler(UTXOPool utxoPool) {
        pool = new UTXOPool(utxoPool);
        addedtx = new ArrayList<>();
        queue = new LinkedList<>();
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool,
     * (2) the signatures on each input of {@code tx} are valid,
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     * values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        double srcSum = 0;
        double destSum = 0;
        Set<UTXO> usedKeys = new HashSet<>();

        for (int idx = 0; idx < tx.getInputs().size(); idx++) {
            Transaction.Input input = tx.getInput(idx);
            UTXO srcKey = new UTXO(input.prevTxHash, input.outputIndex);
            Transaction.Output srcOut = pool.getTxOutput(srcKey);

            if (srcOut == null                                                                          // (1)
                    || !Crypto.verifySignature(srcOut.address, tx.getRawDataToSign(idx), input.signature) // (2)
                    || usedKeys.contains(srcKey)) {                                                  // extra
                return false;
            }

            srcSum += srcOut.value;
            usedKeys.add(srcKey);
        }

        for (int i = 0; i < tx.numOutputs(); i++) {
            Transaction.Output output = tx.getOutput(i);
            if (output.value < 0) {                                                                       // (4)
                return false;
            }
            destSum += output.value;
        }

        if (srcSum < destSum) {
            return false;
        }

//        usedKeys.forEach(utxo -> pool.removeUTXO(utxo));
        return true;                                                         // (5)
    }


    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        Queue<Transaction> rejQueue = new LinkedList<>(Arrays.asList(possibleTxs));
        addedtx = new ArrayList<>();
        queue = new LinkedList<>();

        do {
            queue = new LinkedList<>(rejQueue);
            rejQueue = new LinkedList<>();
            added = false;

            while (!queue.isEmpty()) {
                Transaction toCheck = queue.poll();
                if (isValidTx(toCheck)) {
                    added = true;
                    addedtx.add(toCheck);
                    updatePool(toCheck, pool);
                    continue;
                }
                rejQueue.add(toCheck);
            }
        } while (added);

        return addedtx.toArray(new Transaction[addedtx.size()]);
    }



    private boolean doubleS(Transaction tx) {
        doubleSpend = false;
        Set<UTXO> removedKeys = new HashSet<>();

        tx.getInputs().forEach(input -> {
            UTXO key = new UTXO(input.prevTxHash, input.outputIndex);
            if (removedKeys.contains(key)) {
                doubleSpend = true;
            }
            removedKeys.add(key);
        });

        return !doubleSpend;
    }

    private void updatePool(Transaction tx, UTXOPool utxoPool) {
        for (int i = 0; i < tx.numOutputs(); i++) {
            Transaction.Output output = tx.getOutput(i);
            UTXO utx = new UTXO(tx.getHash(), i);
            utxoPool.addUTXO(utx, output);
        }

        for (int i = 0; i < tx.numInputs(); i++) {
            Transaction.Input input = tx.getInput(i);
            UTXO utx = new UTXO(input.prevTxHash, input.outputIndex);
            utxoPool.removeUTXO(utx);
        }
    }
}
