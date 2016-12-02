import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;

public class TxHandler {
	
	private UTXOPool uPool;

	/* Creates a public ledger whose current UTXOPool (collection of unspent 
	 * transaction outputs) is utxoPool. This should make a defensive copy of 
	 * utxoPool by using the UTXOPool(UTXOPool uPool) constructor.
	 */
	public TxHandler(UTXOPool utxoPool) {
		// IMPLEMENT THIS
		uPool = new UTXOPool(utxoPool);
	}

	/* Returns true if 
	 * (1) all outputs claimed by tx are in the current UTXO pool, 
	 * (2) the signatures on each input of tx are valid, 
	 * (3) no UTXO is claimed multiple times by tx, 
	 * (4) all of tx’s output values are non-negative, and
	 * (5) the sum of tx’s input values is greater than or equal to the sum of   
	        its output values;
	   and false otherwise.
	 */

	public boolean isValidTx(Transaction tx) {
		// IMPLEMENT THIS
		ArrayList<Transaction.Output> outputs = tx.getOutputs();
		ArrayList<Transaction.Input> inputs = tx.getInputs();
		double value = 0;
		
		for (Transaction.Input i : inputs) {
			UTXO ut = new UTXO(i.prevTxHash, i.outputIndex);
			if (!uPool.contains(ut)) {
				return false;
			}
			byte[] sig = i.signature;
			Transaction.Output prevOutput = uPool.getTxOutput(ut);
			RSAKey pubKey = prevOutput.address;
			if (!pubKey.verifySignature(tx.getRawDataToSign(i), sig)) {
				return false;
			}
			value += prevOutput.value;
		}
		
		for (Transaction.Output o : outputs) {
			if (o.value < 0) {
				return false;
			}
			value -= o.value;
		}
		
		for (int i = 0; i < tx.numInputs() - 1; ++i) {
			UTXO ut = new UTXO(inputs.get(i).prevTxHash, inputs.get(i).outputIndex);
			for (int j = i + 1; j < tx.numInputs(); ++j) {
				UTXO utx = new UTXO(inputs.get(j).prevTxHash, inputs.get(j).outputIndex);
				if (ut.equals(utx)) {
					return false;
				}
			}
		}
		
		if (value < 0) {
			return false;
		}
					
		return true;
	}

	/* Handles each epoch by receiving an unordered array of proposed 
	 * transactions, checking each transaction for correctness, 
	 * returning a mutually valid array of accepted transactions, 
	 * and updating the current UTXO pool as appropriate.
	 */
	public Transaction[] handleTxs(Transaction[] possibleTxs) {
		// IMPLEMENT THIS
		ArrayList<Transaction> txsList = new ArrayList<Transaction>();
		ArrayList<Transaction> validTxs = new ArrayList<Transaction>();
		int numValid = 0;
		
		for (int i = 0; i < possibleTxs.length; ++i) {
			if (isValidTx(possibleTxs[i])) {
				UTXO ut = new UTXO(possibleTxs[i].getHash(), i);
				uPool.addUTXO(ut, possibleTxs[i].getOutput(numValid));
				validTxs.add(possibleTxs[i]);
			} else {
				txsList.add(possibleTxs[i]);
			}
		}
			
		do {
			numValid = 0;
			for (Transaction tx : txsList) {
				if (isValidTx(tx)) {
					validTxs.add(tx);
					txsList.remove(tx);
					
					UTXO ut = new UTXO(tx.getHash(), numValid);
					uPool.addUTXO(ut, tx.getOutput(numValid));
					
					numValid += 1;
				}
			}
		} while(numValid > 0);
		
		return validTxs.toArray(new Transaction[0]);
	}
	
	/* Returns the current UTXO pool. If no outstanding UTXOs, returns
	 * an empty (non-null) UTXOPool object. 
	 */
	public UTXOPool getUTXOPool() {
		// IMPLEMENT THIS
		return uPool;
	}

} 