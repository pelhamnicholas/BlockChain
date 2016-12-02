import java.io.FileNotFoundException;
import java.io.IOException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Arrays;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.math.BigInteger;

public class DropboxTestBlockChain {
   
   public int nPeople;
   public int nUTXOTx;
   public int maxUTXOTxOutput;
   public double maxValue;
   public int nTxPerTest;
   public int maxInput;
   public int maxOutput;
   
   public ArrayList<RSAKeyPair> people;

   public DropboxTestBlockChain() throws FileNotFoundException, IOException {
      
      this.nPeople = 20;
      this.nUTXOTx = 20;
      this.maxUTXOTxOutput = 20;
      this.maxValue = 10;
      this.nTxPerTest = 50;
      this.maxInput = 4;
      this.maxOutput = 20;
      
      byte[] key = new byte[32];
      for (int i = 0; i < 32; i++) {
         key[i] = (byte) 1;
      }
      
      PRGen prGen = new PRGen(key);
      
      people = new ArrayList<RSAKeyPair>();
      for (int i = 0; i < nPeople; i++)
         people.add(new RSAKeyPair(prGen, 265));
   }
   
   public int test1() {
      System.out.println("Process a block with no transactions");
      
      Block genesisBlock = new Block(null, people.get(0).getPublicKey());
      genesisBlock.finalize();
      
      BlockChain blockChain = new BlockChain(genesisBlock);
      BlockHandler blockHandler = new BlockHandler(blockChain);
      
      Block block = new Block(genesisBlock.getHash(), people.get(1).getPublicKey());
      block.finalize();
      
      return UtilCOS.printPassFail(blockHandler.processBlock(block));
   }
   
   public int test2() {
      System.out.println("Process a block with a single valid transaction");
      
      Block genesisBlock = new Block(null, people.get(0).getPublicKey());
      genesisBlock.finalize();
      
      BlockChain blockChain = new BlockChain(genesisBlock);
      BlockHandler blockHandler = new BlockHandler(blockChain);
      
      Block block = new Block(genesisBlock.getHash(), people.get(1).getPublicKey());
      Transaction spendCoinbaseTx = new Transaction();
      spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
      spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublicKey());
      spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
      spendCoinbaseTx.finalize();
      block.addTransaction(spendCoinbaseTx);
      block.finalize();
      
      return UtilCOS.printPassFail(blockHandler.processBlock(block));
   }
   
   public int test3() {
      System.out.println("Process a block with many valid transactions");
      
      boolean passes = true;
      
      for (int k = 0; k < 20; k++) {
         Block genesisBlock = new Block(null, people.get(0).getPublicKey());
         genesisBlock.finalize();
         
         BlockChain blockChain = new BlockChain(genesisBlock);
         BlockHandler blockHandler = new BlockHandler(blockChain);
         
         Block block = new Block(genesisBlock.getHash(), people.get(1).getPublicKey());
         Transaction spendCoinbaseTx = new Transaction();
         spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
         
         double totalValue = 0;
         UTXOPool utxoPool = new UTXOPool();
         int numOutputs = 0;
         HashMap<UTXO, RSAKeyPair> utxoToKeyPair = new HashMap<UTXO, RSAKeyPair>();
         HashMap<Integer, RSAKeyPair> keyPairAtIndex = new HashMap<Integer, RSAKeyPair>();
         
         for (int j = 0; j < maxUTXOTxOutput; j++) {
            int rIndex = SampleRandom.randomInt(people.size());
            RSAKey addr = people.get(rIndex).getPublicKey();
            double value = SampleRandom.randomDouble(maxValue);
            if (totalValue + value > Block.COINBASE)
               break;
            spendCoinbaseTx.addOutput(value, addr);
            keyPairAtIndex.put(j, people.get(rIndex));
            totalValue += value;
            numOutputs++;
         }
         
         spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
         spendCoinbaseTx.finalize();
         block.addTransaction(spendCoinbaseTx);
         
         for (int j = 0; j < numOutputs; j++) {
            UTXO ut = new UTXO(spendCoinbaseTx.getHash(), j);
            utxoPool.addUTXO(ut, spendCoinbaseTx.getOutput(j));
            utxoToKeyPair.put(ut, keyPairAtIndex.get(j));
         }
         
         ArrayList<UTXO> utxoSet = utxoPool.getAllUTXO();
         HashSet<UTXO> utxosSeen = new HashSet<UTXO>();
         int maxValidInput = Math.min(maxInput, utxoSet.size());
         
         for (int i = 0; i < nTxPerTest; i++) {
            Transaction tx = new Transaction();
            HashMap<Integer, UTXO> utxoAtIndex = new HashMap<Integer, UTXO>();
            int nInput = SampleRandom.randomInt(maxValidInput) + 1;
            int numInputs = 0;
            double inputValue = 0;
            for (int j = 0; j < nInput; j++) {
               UTXO utxo = utxoSet.get(SampleRandom.randomInt(utxoSet.size()));
               if (!utxosSeen.add(utxo)) {
                  j--;
                  nInput--;
                  continue;
               }
               tx.addInput(utxo.getTxHash(), utxo.getIndex());
               inputValue += utxoPool.getTxOutput(utxo).value;
               utxoAtIndex.put(j, utxo);
               numInputs++;
            }
            
            if (numInputs == 0)
               continue;
            
            int nOutput = SampleRandom.randomInt(maxOutput) + 1;
            double outputValue = 0;
            for (int j = 0; j < nOutput; j++) {
               double value = SampleRandom.randomDouble(maxValue);
               if (outputValue + value > inputValue)
                  break;
               int rIndex = SampleRandom.randomInt(people.size());
               RSAKey addr = people.get(rIndex).getPublicKey();
               tx.addOutput(value, addr);
               outputValue += value;
            }
            for (int j = 0; j < numInputs; j++) {
               tx.addSignature(utxoToKeyPair.get(utxoAtIndex.get(j)).getPrivateKey().sign(tx.getRawDataToSign(j)), j);
            }
            tx.finalize();
            block.addTransaction(tx);
         }
         
         block.finalize();
         
         passes = passes && blockHandler.processBlock(block);
      }
      
      return UtilCOS.printPassFail(passes);
   }
   
   public int test14() {
      System.out.println("Process a block with some double spends");
      
      boolean passes = true;
      
      for (int k = 0; k < 20; k++) {
         Block genesisBlock = new Block(null, people.get(0).getPublicKey());
         genesisBlock.finalize();
         
         BlockChain blockChain = new BlockChain(genesisBlock);
         BlockHandler blockHandler = new BlockHandler(blockChain);
         
         Block block = new Block(genesisBlock.getHash(), people.get(1).getPublicKey());
         Transaction spendCoinbaseTx = new Transaction();
         spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
         
         double totalValue = 0;
         UTXOPool utxoPool = new UTXOPool();
         int numOutputs = 0;
         HashMap<UTXO, RSAKeyPair> utxoToKeyPair = new HashMap<UTXO, RSAKeyPair>();
         HashMap<Integer, RSAKeyPair> keyPairAtIndex = new HashMap<Integer, RSAKeyPair>();
         
         for (int j = 0; j < maxUTXOTxOutput; j++) {
            int rIndex = SampleRandom.randomInt(people.size());
            RSAKey addr = people.get(rIndex).getPublicKey();
            double value = SampleRandom.randomDouble(maxValue);
            if (totalValue + value > Block.COINBASE)
               break;
            spendCoinbaseTx.addOutput(value, addr);
            keyPairAtIndex.put(j, people.get(rIndex));
            totalValue += value;
            numOutputs++;
         }
         
         spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
         spendCoinbaseTx.finalize();
         block.addTransaction(spendCoinbaseTx);
         
         for (int j = 0; j < numOutputs; j++) {
            UTXO ut = new UTXO(spendCoinbaseTx.getHash(), j);
            utxoPool.addUTXO(ut, spendCoinbaseTx.getOutput(j));
            utxoToKeyPair.put(ut, keyPairAtIndex.get(j));
         }
         
         ArrayList<UTXO> utxoSet = utxoPool.getAllUTXO();
         HashSet<UTXO> utxosSeen = new HashSet<UTXO>();
         int maxValidInput = Math.min(maxInput, utxoSet.size());
         
         boolean notCorrupted = true;
         
         for (int i = 0; i < nTxPerTest; i++) {
            Transaction tx = new Transaction();
            HashMap<Integer, UTXO> utxoAtIndex = new HashMap<Integer, UTXO>();
            int nInput = SampleRandom.randomInt(maxValidInput) + 1;
            int numInputs = 0;
            double inputValue = 0;
            for (int j = 0; j < nInput; j++) {
               UTXO utxo = utxoSet.get(SampleRandom.randomInt(utxoSet.size()));
               if (!utxosSeen.add(utxo)) {
                  notCorrupted = false;
               }
               tx.addInput(utxo.getTxHash(), utxo.getIndex());
               inputValue += utxoPool.getTxOutput(utxo).value;
               utxoAtIndex.put(j, utxo);
               numInputs++;
            }
            
            if (numInputs == 0)
               continue;
            
            int nOutput = SampleRandom.randomInt(maxOutput) + 1;
            double outputValue = 0;
            for (int j = 0; j < nOutput; j++) {
               double value = SampleRandom.randomDouble(maxValue);
               if (outputValue + value > inputValue)
                  break;
               int rIndex = SampleRandom.randomInt(people.size());
               RSAKey addr = people.get(rIndex).getPublicKey();
               tx.addOutput(value, addr);
               outputValue += value;
            }
            for (int j = 0; j < numInputs; j++) {
               tx.addSignature(utxoToKeyPair.get(utxoAtIndex.get(j)).getPrivateKey().sign(tx.getRawDataToSign(j)), j);
            }
            tx.finalize();
            block.addTransaction(tx);
         }
         
         block.finalize();
         
         passes = passes && (blockHandler.processBlock(block) == notCorrupted);
      }
      
      return UtilCOS.printPassFail(passes);
   }
   
   public int test4() {
      System.out.println("Process a new genesis block");
      
      Block genesisBlock = new Block(null, people.get(0).getPublicKey());
      genesisBlock.finalize();
      
      BlockChain blockChain = new BlockChain(genesisBlock);
      BlockHandler blockHandler = new BlockHandler(blockChain);
      
      Block genesisblock = new Block(null, people.get(1).getPublicKey());
      genesisblock.finalize();
      
      return UtilCOS.printPassFail(!blockHandler.processBlock(genesisblock));
   }
   
   public int test5() {
      System.out.println("Process a block with an invalid prevBlockHash");
      
      Block genesisBlock = new Block(null, people.get(0).getPublicKey());
      genesisBlock.finalize();
      BlockChain blockChain = new BlockChain(genesisBlock);
      BlockHandler blockHandler = new BlockHandler(blockChain);
      
      byte[] hash = genesisBlock.getHash();
      byte[] hashCopy = Arrays.copyOf(hash, hash.length);
      hashCopy[0]++;
      Block block = new Block(hashCopy, people.get(1).getPublicKey());
      block.finalize();
      
      return UtilCOS.printPassFail(!blockHandler.processBlock(block));
   }
   
   public int test6() {
      System.out.println("Process blocks with different sorts of invalid transactions");
      
      boolean passes = true;
      
      Block genesisBlock = new Block(null, people.get(0).getPublicKey());
      genesisBlock.finalize();
      
      BlockChain blockChain = new BlockChain(genesisBlock);
      BlockHandler blockHandler = new BlockHandler(blockChain);
      
      Block block = new Block(genesisBlock.getHash(), people.get(1).getPublicKey());
      Transaction spendCoinbaseTx = new Transaction();
      spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
      spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublicKey());
      byte[] rawData = spendCoinbaseTx.getRawDataToSign(0);
      rawData[0]++;
      spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(rawData), 0);
      spendCoinbaseTx.finalize();
      block.addTransaction(spendCoinbaseTx);
      block.finalize();
      
      passes = passes && !blockHandler.processBlock(block);
      
      block = new Block(genesisBlock.getHash(), people.get(1).getPublicKey());
      spendCoinbaseTx = new Transaction();
      spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
      spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublicKey());
      spendCoinbaseTx.addSignature(people.get(1).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
      spendCoinbaseTx.finalize();
      block.addTransaction(spendCoinbaseTx);
      block.finalize();
      
      passes = passes && !blockHandler.processBlock(block);
      
      block = new Block(genesisBlock.getHash(), people.get(1).getPublicKey());
      spendCoinbaseTx = new Transaction();
      spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
      spendCoinbaseTx.addOutput(Block.COINBASE + 1, people.get(1).getPublicKey());
      spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
      spendCoinbaseTx.finalize();
      block.addTransaction(spendCoinbaseTx);
      block.finalize();
      
      passes = passes && !blockHandler.processBlock(block);
      
      block = new Block(genesisBlock.getHash(), people.get(1).getPublicKey());
      spendCoinbaseTx = new Transaction();
      byte[] hash = genesisBlock.getCoinbase().getHash();
      byte[] hashCopy = Arrays.copyOf(hash, hash.length);
      hashCopy[0]++;
      spendCoinbaseTx.addInput(hashCopy, 0);
      spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublicKey());
      spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
      spendCoinbaseTx.finalize();
      block.addTransaction(spendCoinbaseTx);
      block.finalize();
      
      passes = passes && !blockHandler.processBlock(block);
      
      block = new Block(genesisBlock.getHash(), people.get(1).getPublicKey());
      spendCoinbaseTx = new Transaction();
      spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 1);
      spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublicKey());
      spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
      spendCoinbaseTx.finalize();
      block.addTransaction(spendCoinbaseTx);
      block.finalize();
      
      passes = passes && !blockHandler.processBlock(block);
      
      block = new Block(genesisBlock.getHash(), people.get(1).getPublicKey());
      spendCoinbaseTx = new Transaction();
      spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
      spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
      spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublicKey());
      spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
      spendCoinbaseTx.finalize();
      block.addTransaction(spendCoinbaseTx);
      block.finalize();
      
      passes = passes && !blockHandler.processBlock(block);
      
      block = new Block(genesisBlock.getHash(), people.get(1).getPublicKey());
      spendCoinbaseTx = new Transaction();
      spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
      spendCoinbaseTx.addOutput(-Block.COINBASE, people.get(1).getPublicKey());
      spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
      spendCoinbaseTx.finalize();
      block.addTransaction(spendCoinbaseTx);
      block.finalize();
      
      passes = passes && !blockHandler.processBlock(block);
      
      return UtilCOS.printPassFail(passes);
   }
   
   public int test7() {
      System.out.println("Process multiple blocks directly on top of the genesis block");
      
      Block genesisBlock = new Block(null, people.get(0).getPublicKey());
      genesisBlock.finalize();
      
      BlockChain blockChain = new BlockChain(genesisBlock);
      BlockHandler blockHandler = new BlockHandler(blockChain);
      
      boolean passes = true;
      Block block;
      Transaction spendCoinbaseTx;
      
      for (int i = 0; i < 100; i++) {
         block = new Block(genesisBlock.getHash(), people.get(1).getPublicKey());
         spendCoinbaseTx = new Transaction();
         spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
         spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublicKey());
         spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
         spendCoinbaseTx.finalize();
         block.addTransaction(spendCoinbaseTx);
         block.finalize();
         passes = passes && blockHandler.processBlock(block);
      }
      
      return UtilCOS.printPassFail(passes);
   }
   
   public int test15() {
      System.out.println("Process a block containing a transaction that claims a UTXO already claimed by a transaction in its parent");
      
      Block genesisBlock = new Block(null, people.get(0).getPublicKey());
      genesisBlock.finalize();
      
      BlockChain blockChain = new BlockChain(genesisBlock);
      BlockHandler blockHandler = new BlockHandler(blockChain);
      
      boolean passes = true;
      
      Block block = new Block(genesisBlock.getHash(), people.get(1).getPublicKey());
      Transaction spendCoinbaseTx = new Transaction();
      spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
      spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublicKey());
      spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
      spendCoinbaseTx.finalize();
      block.addTransaction(spendCoinbaseTx);
      block.finalize();
      
      passes = passes && blockHandler.processBlock(block);
      
      Block prevBlock = block;
      
      block = new Block(prevBlock.getHash(), people.get(2).getPublicKey());
      spendCoinbaseTx = new Transaction();
      spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
      spendCoinbaseTx.addOutput(Block.COINBASE - 1, people.get(2).getPublicKey());
      spendCoinbaseTx.addSignature(people.get(1).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
      spendCoinbaseTx.finalize();
      block.addTransaction(spendCoinbaseTx);
      block.finalize();
      
      passes = passes && !blockHandler.processBlock(block);
      
      return UtilCOS.printPassFail(passes);
   }
   
   public int test16() {
      System.out.println("Process a block containing a transaction that claims a UTXO not on its branch");
      
      Block genesisBlock = new Block(null, people.get(0).getPublicKey());
      genesisBlock.finalize();
      
      BlockChain blockChain = new BlockChain(genesisBlock);
      BlockHandler blockHandler = new BlockHandler(blockChain);
      
      boolean passes = true;
      
      Block block = new Block(genesisBlock.getHash(), people.get(1).getPublicKey());
      Transaction spendCoinbaseTx = new Transaction();
      spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
      spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublicKey());
      spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
      spendCoinbaseTx.finalize();
      block.addTransaction(spendCoinbaseTx);
      block.finalize();
      
      passes = passes && blockHandler.processBlock(block);
      
      Block prevBlock = block;
      
      block = new Block(genesisBlock.getHash(), people.get(2).getPublicKey());
      spendCoinbaseTx = new Transaction();
      spendCoinbaseTx.addInput(prevBlock.getCoinbase().getHash(), 0);
      spendCoinbaseTx.addOutput(Block.COINBASE, people.get(2).getPublicKey());
      spendCoinbaseTx.addSignature(people.get(1).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
      spendCoinbaseTx.finalize();
      block.addTransaction(spendCoinbaseTx);
      block.finalize();
      
      passes = passes && !blockHandler.processBlock(block);
      
      return UtilCOS.printPassFail(passes);
   }
   
   public int test17() {
      System.out.println("Process a block containing a transaction that claims a UTXO from earlier in its branch that has not yet been claimed");
      
      Block genesisBlock = new Block(null, people.get(0).getPublicKey());
      genesisBlock.finalize();
      
      BlockChain blockChain = new BlockChain(genesisBlock);
      BlockHandler blockHandler = new BlockHandler(blockChain);
      
      boolean passes = true;
      
      Block block = new Block(genesisBlock.getHash(), people.get(1).getPublicKey());
      Transaction spendCoinbaseTx = new Transaction();
      spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
      spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublicKey());
      spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
      spendCoinbaseTx.finalize();
      block.addTransaction(spendCoinbaseTx);
      block.finalize();
      
      passes = passes && blockHandler.processBlock(block);
      
      Block prevBlock = block;
      Transaction retainTx = spendCoinbaseTx;
      
      block = new Block(prevBlock.getHash(), people.get(2).getPublicKey());
      spendCoinbaseTx = new Transaction();
      spendCoinbaseTx.addInput(prevBlock.getCoinbase().getHash(), 0);
      spendCoinbaseTx.addOutput(Block.COINBASE, people.get(2).getPublicKey());
      spendCoinbaseTx.addSignature(people.get(1).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
      spendCoinbaseTx.finalize();
      block.addTransaction(spendCoinbaseTx);
      block.finalize();
      
      passes = passes && blockHandler.processBlock(block);
      
      Block prevPrevBlock = prevBlock;
      prevBlock = block;
      
      block = new Block(prevBlock.getHash(), people.get(3).getPublicKey());
      Transaction spendOldUTXOTransaction = new Transaction();
      spendOldUTXOTransaction.addInput(retainTx.getHash(), 0);
      spendOldUTXOTransaction.addOutput(Block.COINBASE, people.get(2).getPublicKey());
      spendOldUTXOTransaction.addSignature(people.get(1).getPrivateKey().sign(spendOldUTXOTransaction.getRawDataToSign(0)), 0);
      spendOldUTXOTransaction.finalize();
      block.addTransaction(spendOldUTXOTransaction);
      block.finalize();
      
      passes = passes && blockHandler.processBlock(block);
            
      return UtilCOS.printPassFail(passes);
   }

   public int test8() {
      System.out.println("Process a linear chain of blocks");
      
      Block genesisBlock = new Block(null, people.get(0).getPublicKey());
      genesisBlock.finalize();
      
      BlockChain blockChain = new BlockChain(genesisBlock);
      BlockHandler blockHandler = new BlockHandler(blockChain);
      
      boolean passes = true;
      Block block;
      Block prevBlock = genesisBlock;
      Transaction spendCoinbaseTx;
      
      for (int i = 0; i < 100; i++) {
         block = new Block(prevBlock.getHash(), people.get(0).getPublicKey());
         spendCoinbaseTx = new Transaction();
         spendCoinbaseTx.addInput(prevBlock.getCoinbase().getHash(), 0);
         spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublicKey());
         spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
         spendCoinbaseTx.finalize();
         block.addTransaction(spendCoinbaseTx);
         block.finalize();
         prevBlock = block;
         
         passes = passes && blockHandler.processBlock(block);
      }
      
      return UtilCOS.printPassFail(passes);
   }
   
   public int test9() {
      System.out.println("Process a linear chain of blocks of length CUT_OFF_AGE and then a block on top of the genesis block");
      
      Block genesisBlock = new Block(null, people.get(0).getPublicKey());
      genesisBlock.finalize();
      
      BlockChain blockChain = new BlockChain(genesisBlock);
      BlockHandler blockHandler = new BlockHandler(blockChain);
      
      boolean passes = true;
      Block block;
      Block prevBlock = genesisBlock;
      Transaction spendCoinbaseTx;
      
      for (int i = 0; i < BlockChain.CUT_OFF_AGE; i++) {
         block = new Block(prevBlock.getHash(), people.get(0).getPublicKey());
         spendCoinbaseTx = new Transaction();
         spendCoinbaseTx.addInput(prevBlock.getCoinbase().getHash(), 0);
         spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublicKey());
         spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
         spendCoinbaseTx.finalize();
         block.addTransaction(spendCoinbaseTx);
         block.finalize();
         prevBlock = block;
         
         passes = passes && blockHandler.processBlock(block);
      }
      
      block = new Block(genesisBlock.getHash(), people.get(0).getPublicKey());
      block.finalize();
      
      passes = passes && blockHandler.processBlock(block);
      
      return UtilCOS.printPassFail(passes);
   }
   
   public int test10() {
      System.out.println("Process a linear chain of blocks of length CUT_OFF_AGE + 1 and then a block on top of the genesis block");
      
      Block genesisBlock = new Block(null, people.get(0).getPublicKey());
      genesisBlock.finalize();
      
      BlockChain blockChain = new BlockChain(genesisBlock);
      BlockHandler blockHandler = new BlockHandler(blockChain);
      
      boolean passes = true;
      Block block;
      Block prevBlock = genesisBlock;
      Transaction spendCoinbaseTx;
      
      for (int i = 0; i < BlockChain.CUT_OFF_AGE + 1; i++) {
         block = new Block(prevBlock.getHash(), people.get(0).getPublicKey());
         spendCoinbaseTx = new Transaction();
         spendCoinbaseTx.addInput(prevBlock.getCoinbase().getHash(), 0);
         spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublicKey());
         spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
         spendCoinbaseTx.finalize();
         block.addTransaction(spendCoinbaseTx);
         block.finalize();
         prevBlock = block;
         
         passes = passes && blockHandler.processBlock(block);
      }
      
      block = new Block(genesisBlock.getHash(), people.get(0).getPublicKey());
      block.finalize();
      
      passes = passes && !blockHandler.processBlock(block);
      
      return UtilCOS.printPassFail(passes);
   }
   
   public int test11() {
      System.out.println("Create a block when no transactions have been processed");
      
      Block genesisBlock = new Block(null, people.get(0).getPublicKey());
      genesisBlock.finalize();
      
      BlockChain blockChain = new BlockChain(genesisBlock);
      BlockHandler blockHandler = new BlockHandler(blockChain);
      
      Block createdBlock = blockHandler.createBlock(people.get(1).getPublicKey());
      return UtilCOS.printPassFail(createdBlock != null && createdBlock.getPrevBlockHash().equals(genesisBlock.getHash()) && createdBlock.getTransactions().size() == 0);
   }
   
   public int test12() {
      System.out.println("Create a block after a single valid transaction has been processed");
      
      Block genesisBlock = new Block(null, people.get(0).getPublicKey());
      genesisBlock.finalize();
      
      BlockChain blockChain = new BlockChain(genesisBlock);
      BlockHandler blockHandler = new BlockHandler(blockChain);
      
      Transaction spendCoinbaseTx = new Transaction();
      spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
      spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublicKey());
      spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
      spendCoinbaseTx.finalize();
      blockHandler.processTx(spendCoinbaseTx);
      
      Block createdBlock = blockHandler.createBlock(people.get(1).getPublicKey());
      return UtilCOS.printPassFail(createdBlock != null && createdBlock.getPrevBlockHash().equals(genesisBlock.getHash()) && createdBlock.getTransactions().size() == 1 && createdBlock.getTransaction(0).equals(spendCoinbaseTx));
   }
   
   public int test22() {
      System.out.println("Create a block after a valid transaction has been processed, then create a second block");
      
      Block genesisBlock = new Block(null, people.get(0).getPublicKey());
      genesisBlock.finalize();
      
      BlockChain blockChain = new BlockChain(genesisBlock);
      BlockHandler blockHandler = new BlockHandler(blockChain);
      
      Transaction spendCoinbaseTx = new Transaction();
      spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
      spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublicKey());
      spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
      spendCoinbaseTx.finalize();
      blockHandler.processTx(spendCoinbaseTx);
      
      Block createdBlock = blockHandler.createBlock(people.get(1).getPublicKey());
      Block createdBlock2 = blockHandler.createBlock(people.get(2).getPublicKey());
      
      return UtilCOS.printPassFail(createdBlock != null && createdBlock.getPrevBlockHash().equals(genesisBlock.getHash()) && createdBlock.getTransactions().size() == 1 && createdBlock.getTransaction(0).equals(spendCoinbaseTx) && createdBlock2 != null && createdBlock2.getPrevBlockHash().equals(createdBlock.getHash()) && createdBlock2.getTransactions().size() == 0);
   }
   
   public int test19() {
      System.out.println("Create a block after a valid transaction has been processed that is already in a block in the longest valid branch");
      
      Block genesisBlock = new Block(null, people.get(0).getPublicKey());
      genesisBlock.finalize();
      
      BlockChain blockChain = new BlockChain(genesisBlock);
      BlockHandler blockHandler = new BlockHandler(blockChain);
      
      boolean passes = true;
      
      Block block = new Block(genesisBlock.getHash(), people.get(1).getPublicKey());
      
      Transaction spendCoinbaseTx = new Transaction();
      spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
      spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublicKey());
      spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
      spendCoinbaseTx.finalize();
      block.addTransaction(spendCoinbaseTx);
      block.finalize();
      
      passes = passes && blockHandler.processBlock(block);
      
      blockHandler.processTx(spendCoinbaseTx);
      Block createdBlock = blockHandler.createBlock(people.get(1).getPublicKey());
      
      return UtilCOS.printPassFail(passes && createdBlock != null && createdBlock.getPrevBlockHash().equals(block.getHash()) && createdBlock.getTransactions().size() == 0);
   }
   
   public int test20() {
      System.out.println("Create a block after a valid transaction has been processed that uses a UTXO already claimed by a transaction in the longest valid branch");
      
      Block genesisBlock = new Block(null, people.get(0).getPublicKey());
      genesisBlock.finalize();
      
      BlockChain blockChain = new BlockChain(genesisBlock);
      BlockHandler blockHandler = new BlockHandler(blockChain);
      
      boolean passes = true;
      
      Block block = new Block(genesisBlock.getHash(), people.get(1).getPublicKey());
      
      Transaction spendCoinbaseTx = new Transaction();
      spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
      spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublicKey());
      spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
      spendCoinbaseTx.finalize();
      block.addTransaction(spendCoinbaseTx);
      block.finalize();
      
      passes = passes && blockHandler.processBlock(block);
      
      Transaction spendCoinbaseTx2 = new Transaction();
      spendCoinbaseTx2.addInput(genesisBlock.getCoinbase().getHash(), 0);
      spendCoinbaseTx2.addOutput(Block.COINBASE - 1, people.get(1).getPublicKey());
      spendCoinbaseTx2.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx2.getRawDataToSign(0)), 0);
      spendCoinbaseTx2.finalize();
      
      blockHandler.processTx(spendCoinbaseTx2);
      Block createdBlock = blockHandler.createBlock(people.get(1).getPublicKey());
      
      return UtilCOS.printPassFail(passes && createdBlock != null && createdBlock.getPrevBlockHash().equals(block.getHash()) && createdBlock.getTransactions().size() == 0);
   }
   
   public int test21() {
      System.out.println("Create a block after a valid transaction has been processed that is not a double spend on the longest valid branch and has not yet been included in any other block");
      
      Block genesisBlock = new Block(null, people.get(0).getPublicKey());
      genesisBlock.finalize();
      
      BlockChain blockChain = new BlockChain(genesisBlock);
      BlockHandler blockHandler = new BlockHandler(blockChain);
      
      boolean passes = true;
      
      Block block = new Block(genesisBlock.getHash(), people.get(1).getPublicKey());
      
      Transaction spendCoinbaseTx = new Transaction();
      spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
      spendCoinbaseTx.addOutput(Block.COINBASE - 1, people.get(1).getPublicKey());
      spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
      spendCoinbaseTx.finalize();
      block.addTransaction(spendCoinbaseTx);
      block.finalize();
      
      passes = passes && blockHandler.processBlock(block);
      
      Transaction spendPrevTx = new Transaction();
      spendPrevTx.addInput(block.getCoinbase().getHash(), 0);
      spendPrevTx.addOutput(Block.COINBASE, people.get(1).getPublicKey());
      spendPrevTx.addSignature(people.get(1).getPrivateKey().sign(spendPrevTx.getRawDataToSign(0)), 0);
      spendPrevTx.finalize();
      
      blockHandler.processTx(spendPrevTx);
      Block createdBlock = blockHandler.createBlock(people.get(1).getPublicKey());
      
      return UtilCOS.printPassFail(passes && createdBlock != null && createdBlock.getPrevBlockHash().equals(block.getHash()) && createdBlock.getTransactions().size() == 1 && createdBlock.getTransaction(0).equals(spendPrevTx));
   }

   public int test13() {
      System.out.println("Create a block after only invalid transactions have been processed");
      
      Block genesisBlock = new Block(null, people.get(0).getPublicKey());
      genesisBlock.finalize();
      
      BlockChain blockChain = new BlockChain(genesisBlock);
      BlockHandler blockHandler = new BlockHandler(blockChain);
      
      Transaction spendCoinbaseTx = new Transaction();
      spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
      spendCoinbaseTx.addOutput(Block.COINBASE + 2, people.get(1).getPublicKey());
      spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
      spendCoinbaseTx.finalize();
      blockHandler.processTx(spendCoinbaseTx);
      
      Block createdBlock = blockHandler.createBlock(people.get(1).getPublicKey());
      return UtilCOS.printPassFail(createdBlock != null && createdBlock.getPrevBlockHash().equals(genesisBlock.getHash()) && createdBlock.getTransactions().size() == 0);
   }
   
   public int test23() {
      System.out.println("Process a transaction, create a block, process a transaction, create a block, ...");
      
      Block genesisBlock = new Block(null, people.get(0).getPublicKey());
      genesisBlock.finalize();
      
      BlockChain blockChain = new BlockChain(genesisBlock);
      BlockHandler blockHandler = new BlockHandler(blockChain);
      
      boolean passes = true;
      Transaction spendCoinbaseTx;
      Block prevBlock = genesisBlock;
      
      for (int i = 0; i < 20; i++) {
         spendCoinbaseTx = new Transaction();
         spendCoinbaseTx.addInput(prevBlock.getCoinbase().getHash(), 0);
         spendCoinbaseTx.addOutput(Block.COINBASE, people.get(0).getPublicKey());
         spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
         spendCoinbaseTx.finalize();
         blockHandler.processTx(spendCoinbaseTx);
         
         Block createdBlock = blockHandler.createBlock(people.get(0).getPublicKey());
         
         passes = passes && createdBlock != null && createdBlock.getPrevBlockHash().equals(prevBlock.getHash()) && createdBlock.getTransactions().size() == 1 && createdBlock.getTransaction(0).equals(spendCoinbaseTx);
         prevBlock = createdBlock;
      }
      return UtilCOS.printPassFail(passes);
   }
   
   public int test24() {
      System.out.println("Process a transaction, create a block, then process a block on top of that block with a transaction claiming a UTXO from that transaction");
      
      Block genesisBlock = new Block(null, people.get(0).getPublicKey());
      genesisBlock.finalize();
      
      BlockChain blockChain = new BlockChain(genesisBlock);
      BlockHandler blockHandler = new BlockHandler(blockChain);
      
      Transaction spendCoinbaseTx = new Transaction();
      spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
      spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublicKey());
      spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
      spendCoinbaseTx.finalize();
      blockHandler.processTx(spendCoinbaseTx);
      
      Block createdBlock = blockHandler.createBlock(people.get(1).getPublicKey());
      
      Block newBlock = new Block(createdBlock.getHash(), people.get(1).getPublicKey());
      Transaction spendTx = new Transaction();
      spendTx.addInput(spendCoinbaseTx.getHash(), 0);
      spendTx.addOutput(Block.COINBASE, people.get(2).getPublicKey());
      spendTx.addSignature(people.get(1).getPrivateKey().sign(spendTx.getRawDataToSign(0)), 0);
      spendTx.finalize();
      newBlock.addTransaction(spendTx);
      newBlock.finalize();
      
      return UtilCOS.printPassFail(createdBlock != null && createdBlock.getPrevBlockHash().equals(genesisBlock.getHash()) && createdBlock.getTransactions().size() == 1 && createdBlock.getTransaction(0).equals(spendCoinbaseTx) && blockHandler.processBlock(newBlock));
   }
   

   public int test25() {
      System.out.println("Process a transaction, create a block, then process a block on top of the genesis block with a transaction claiming a UTXO from that transaction");
      
      Block genesisBlock = new Block(null, people.get(0).getPublicKey());
      genesisBlock.finalize();
      
      BlockChain blockChain = new BlockChain(genesisBlock);
      BlockHandler blockHandler = new BlockHandler(blockChain);
      
      Transaction spendCoinbaseTx = new Transaction();
      spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
      spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublicKey());
      spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
      spendCoinbaseTx.finalize();
      blockHandler.processTx(spendCoinbaseTx);
      
      Block createdBlock = blockHandler.createBlock(people.get(1).getPublicKey());
      
      Block newBlock = new Block(genesisBlock.getHash(), people.get(1).getPublicKey());
      Transaction spendTx = new Transaction();
      spendTx.addInput(spendCoinbaseTx.getHash(), 0);
      spendTx.addOutput(Block.COINBASE, people.get(2).getPublicKey());
      spendTx.addSignature(people.get(1).getPrivateKey().sign(spendTx.getRawDataToSign(0)), 0);
      spendTx.finalize();
      newBlock.addTransaction(spendTx);
      newBlock.finalize();
      
      return UtilCOS.printPassFail(createdBlock != null && createdBlock.getPrevBlockHash().equals(genesisBlock.getHash()) && createdBlock.getTransactions().size() == 1 && createdBlock.getTransaction(0).equals(spendCoinbaseTx) && !blockHandler.processBlock(newBlock));
   }
   
   public int test18() {
      System.out.println("Process multiple blocks directly on top of the genesis block, then create a block");
      
      Block genesisBlock = new Block(null, people.get(0).getPublicKey());
      genesisBlock.finalize();
      
      BlockChain blockChain = new BlockChain(genesisBlock);
      BlockHandler blockHandler = new BlockHandler(blockChain);
      
      boolean passes = true;
      Block block;
      Block firstBlock = null;
      Transaction spendCoinbaseTx;
      
      for (int i = 0; i < 100; i++) {
         block = new Block(genesisBlock.getHash(), people.get(1).getPublicKey());
         if (i == 0)
            firstBlock = block;
         
         spendCoinbaseTx = new Transaction();
         spendCoinbaseTx.addInput(genesisBlock.getCoinbase().getHash(), 0);
         spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublicKey());
         spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
         spendCoinbaseTx.finalize();
         block.addTransaction(spendCoinbaseTx);
         block.finalize();
         passes = passes && blockHandler.processBlock(block);
      }

      Block createdBlock = blockHandler.createBlock(people.get(1).getPublicKey());
      
      return UtilCOS.printPassFail(createdBlock != null && createdBlock.getPrevBlockHash().equals(firstBlock.getHash()) && createdBlock.getTransactions().size() == 0);
   }
   
   public int test26() {
      System.out.println("Construct two branches of approximately equal size, ensuring that blocks are always created on the proper branch");
      
      Block genesisBlock = new Block(null, people.get(0).getPublicKey());
      genesisBlock.finalize();
      
      BlockChain blockChain = new BlockChain(genesisBlock);
      BlockHandler blockHandler = new BlockHandler(blockChain);
      
      boolean passes = true;
      boolean flipped = false;
      Block block;
      Block firstBranchPrevBlock = genesisBlock;
      Block secondBranchPrevBlock = genesisBlock;
      Transaction spendCoinbaseTx;
      
      for (int i = 0; i < 30; i++) {
         spendCoinbaseTx = new Transaction();
         if (i % 2 == 0) {
            if (!flipped) {
               spendCoinbaseTx.addInput(firstBranchPrevBlock.getCoinbase().getHash(), 0);
               spendCoinbaseTx.addOutput(Block.COINBASE, people.get(0).getPublicKey());
               spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
               spendCoinbaseTx.finalize();
               blockHandler.processTx(spendCoinbaseTx);
               
               block = blockHandler.createBlock(people.get(0).getPublicKey());
               
               passes = passes && block != null && block.getPrevBlockHash().equals(firstBranchPrevBlock.getHash()) && block.getTransactions().size() == 1 && block.getTransaction(0).equals(spendCoinbaseTx);
               firstBranchPrevBlock = block;
            } else {
               spendCoinbaseTx.addInput(secondBranchPrevBlock.getCoinbase().getHash(), 0);
               spendCoinbaseTx.addOutput(Block.COINBASE, people.get(0).getPublicKey());
               spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
               spendCoinbaseTx.finalize();
               blockHandler.processTx(spendCoinbaseTx);
               
               block = blockHandler.createBlock(people.get(0).getPublicKey());
               
               passes = passes && block != null && block.getPrevBlockHash().equals(secondBranchPrevBlock.getHash()) && block.getTransactions().size() == 1 && block.getTransaction(0).equals(spendCoinbaseTx);
               secondBranchPrevBlock = block;
            }
         } else {
            if (!flipped) {
               // add two blocks two second branch
               block = new Block(secondBranchPrevBlock.getHash(), people.get(0).getPublicKey());
               spendCoinbaseTx = new Transaction();
               spendCoinbaseTx.addInput(secondBranchPrevBlock.getCoinbase().getHash(), 0);
               spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublicKey());
               spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
               spendCoinbaseTx.finalize();
               block.addTransaction(spendCoinbaseTx);
               block.finalize();
               
               passes = passes && blockHandler.processBlock(block);
               secondBranchPrevBlock = block;
               
               block = new Block(secondBranchPrevBlock.getHash(), people.get(0).getPublicKey());
               spendCoinbaseTx = new Transaction();
               spendCoinbaseTx.addInput(secondBranchPrevBlock.getCoinbase().getHash(), 0);
               spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublicKey());
               spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
               spendCoinbaseTx.finalize();
               block.addTransaction(spendCoinbaseTx);
               block.finalize();
               
               passes = passes && blockHandler.processBlock(block);
               secondBranchPrevBlock = block;
               
               if (i > 1) {
                  block = new Block(secondBranchPrevBlock.getHash(), people.get(0).getPublicKey());
                  spendCoinbaseTx = new Transaction();
                  spendCoinbaseTx.addInput(secondBranchPrevBlock.getCoinbase().getHash(), 0);
                  spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublicKey());
                  spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
                  spendCoinbaseTx.finalize();
                  block.addTransaction(spendCoinbaseTx);
                  block.finalize();
                  
                  passes = passes && blockHandler.processBlock(block);
                  secondBranchPrevBlock = block;
               }
            } else {
               block = new Block(firstBranchPrevBlock.getHash(), people.get(0).getPublicKey());
               spendCoinbaseTx = new Transaction();
               spendCoinbaseTx.addInput(firstBranchPrevBlock.getCoinbase().getHash(), 0);
               spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublicKey());
               spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
               spendCoinbaseTx.finalize();
               block.addTransaction(spendCoinbaseTx);
               block.finalize();
               
               passes = passes && blockHandler.processBlock(block);
               firstBranchPrevBlock = block;
               
               block = new Block(firstBranchPrevBlock.getHash(), people.get(0).getPublicKey());
               spendCoinbaseTx = new Transaction();
               spendCoinbaseTx.addInput(firstBranchPrevBlock.getCoinbase().getHash(), 0);
               spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublicKey());
               spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
               spendCoinbaseTx.finalize();
               block.addTransaction(spendCoinbaseTx);
               block.finalize();
               
               passes = passes && blockHandler.processBlock(block);
               firstBranchPrevBlock = block;
               
               if (i > 1) {
                  block = new Block(firstBranchPrevBlock.getHash(), people.get(0).getPublicKey());
                  spendCoinbaseTx = new Transaction();
                  spendCoinbaseTx.addInput(firstBranchPrevBlock.getCoinbase().getHash(), 0);
                  spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublicKey());
                  spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
                  spendCoinbaseTx.finalize();
                  block.addTransaction(spendCoinbaseTx);
                  block.finalize();
                  
                  passes = passes && blockHandler.processBlock(block);
                  firstBranchPrevBlock = block;
               }
            }
            flipped = !flipped;
         }
      }
      return UtilCOS.printPassFail(passes);
   }
   
   private class ForwardBlockNode {
      public Block b;
      public ForwardBlockNode child;
      
      public ForwardBlockNode(Block b) {
         this.b = b;
         this.child = null;
      }
      
      public void setChild(ForwardBlockNode child) {
         this.child = child;
      }
   }
   
   public int test27() {
      System.out.println("Similar to previous test, but then try to process blocks whose parents are at height < maxHeight - CUT_OFF_AGE");
      
      Block genesisBlock = new Block(null, people.get(0).getPublicKey());
      genesisBlock.finalize();
      
      BlockChain blockChain = new BlockChain(genesisBlock);
      BlockHandler blockHandler = new BlockHandler(blockChain);
      
      boolean passes = true;
      boolean flipped = false;
      Block block;
      Block firstBranchPrevBlock = genesisBlock;
      ForwardBlockNode firstBranch = new ForwardBlockNode(firstBranchPrevBlock);
      ForwardBlockNode firstBranchTracker = firstBranch;
      Block secondBranchPrevBlock = genesisBlock;
      ForwardBlockNode secondBranch = new ForwardBlockNode(secondBranchPrevBlock);
      ForwardBlockNode secondBranchTracker = secondBranch;
      Transaction spendCoinbaseTx;
      
      for (int i = 0; i < 3*BlockChain.CUT_OFF_AGE; i++) {
         spendCoinbaseTx = new Transaction();
         if (i % 2 == 0) {
            if (!flipped) {
               spendCoinbaseTx.addInput(firstBranchPrevBlock.getCoinbase().getHash(), 0);
               spendCoinbaseTx.addOutput(Block.COINBASE, people.get(0).getPublicKey());
               spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
               spendCoinbaseTx.finalize();
               blockHandler.processTx(spendCoinbaseTx);
               
               block = blockHandler.createBlock(people.get(0).getPublicKey());
               
               passes = passes && block != null && block.getPrevBlockHash().equals(firstBranchPrevBlock.getHash()) && block.getTransactions().size() == 1 && block.getTransaction(0).equals(spendCoinbaseTx);
               ForwardBlockNode newNode = new ForwardBlockNode(block);
               firstBranchTracker.setChild(newNode);
               firstBranchTracker = newNode;
               firstBranchPrevBlock = block;
            } else {
               spendCoinbaseTx.addInput(secondBranchPrevBlock.getCoinbase().getHash(), 0);
               spendCoinbaseTx.addOutput(Block.COINBASE, people.get(0).getPublicKey());
               spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
               spendCoinbaseTx.finalize();
               blockHandler.processTx(spendCoinbaseTx);
               
               block = blockHandler.createBlock(people.get(0).getPublicKey());
               
               passes = passes && block != null && block.getPrevBlockHash().equals(secondBranchPrevBlock.getHash()) && block.getTransactions().size() == 1 && block.getTransaction(0).equals(spendCoinbaseTx);
               ForwardBlockNode newNode = new ForwardBlockNode(block);
               secondBranchTracker.setChild(newNode);
               secondBranchTracker = newNode;
               secondBranchPrevBlock = block;
            }
         } else {
            if (!flipped) {
               // add two blocks two second branch
               block = new Block(secondBranchPrevBlock.getHash(), people.get(0).getPublicKey());
               spendCoinbaseTx = new Transaction();
               spendCoinbaseTx.addInput(secondBranchPrevBlock.getCoinbase().getHash(), 0);
               spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublicKey());
               spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
               spendCoinbaseTx.finalize();
               block.addTransaction(spendCoinbaseTx);
               block.finalize();
               
               passes = passes && blockHandler.processBlock(block);
               ForwardBlockNode newNode = new ForwardBlockNode(block);
               secondBranchTracker.setChild(newNode);
               secondBranchTracker = newNode;
               secondBranchPrevBlock = block;
               
               block = new Block(secondBranchPrevBlock.getHash(), people.get(0).getPublicKey());
               spendCoinbaseTx = new Transaction();
               spendCoinbaseTx.addInput(secondBranchPrevBlock.getCoinbase().getHash(), 0);
               spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublicKey());
               spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
               spendCoinbaseTx.finalize();
               block.addTransaction(spendCoinbaseTx);
               block.finalize();
               
               passes = passes && blockHandler.processBlock(block);
               newNode = new ForwardBlockNode(block);
               secondBranchTracker.setChild(newNode);
               secondBranchTracker = newNode;
               secondBranchPrevBlock = block;
               
               if (i > 1) {
                  block = new Block(secondBranchPrevBlock.getHash(), people.get(0).getPublicKey());
                  spendCoinbaseTx = new Transaction();
                  spendCoinbaseTx.addInput(secondBranchPrevBlock.getCoinbase().getHash(), 0);
                  spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublicKey());
                  spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
                  spendCoinbaseTx.finalize();
                  block.addTransaction(spendCoinbaseTx);
                  block.finalize();
                  
                  passes = passes && blockHandler.processBlock(block);
                  newNode = new ForwardBlockNode(block);
                  secondBranchTracker.setChild(newNode);
                  secondBranchTracker = newNode;
                  secondBranchPrevBlock = block;
               }
            } else {
               block = new Block(firstBranchPrevBlock.getHash(), people.get(0).getPublicKey());
               spendCoinbaseTx = new Transaction();
               spendCoinbaseTx.addInput(firstBranchPrevBlock.getCoinbase().getHash(), 0);
               spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublicKey());
               spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
               spendCoinbaseTx.finalize();
               block.addTransaction(spendCoinbaseTx);
               block.finalize();
               
               passes = passes && blockHandler.processBlock(block);
               ForwardBlockNode newNode = new ForwardBlockNode(block);
               firstBranchTracker.setChild(newNode);
               firstBranchTracker = newNode;
               firstBranchPrevBlock = block;
               
               block = new Block(firstBranchPrevBlock.getHash(), people.get(0).getPublicKey());
               spendCoinbaseTx = new Transaction();
               spendCoinbaseTx.addInput(firstBranchPrevBlock.getCoinbase().getHash(), 0);
               spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublicKey());
               spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
               spendCoinbaseTx.finalize();
               block.addTransaction(spendCoinbaseTx);
               block.finalize();
               
               passes = passes && blockHandler.processBlock(block);
               newNode = new ForwardBlockNode(block);
               firstBranchTracker.setChild(newNode);
               firstBranchTracker = newNode;
               firstBranchPrevBlock = block;
               
               if (i > 1) {
                  block = new Block(firstBranchPrevBlock.getHash(), people.get(0).getPublicKey());
                  spendCoinbaseTx = new Transaction();
                  spendCoinbaseTx.addInput(firstBranchPrevBlock.getCoinbase().getHash(), 0);
                  spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublicKey());
                  spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
                  spendCoinbaseTx.finalize();
                  block.addTransaction(spendCoinbaseTx);
                  block.finalize();
                  
                  passes = passes && blockHandler.processBlock(block);
                  newNode = new ForwardBlockNode(block);
                  firstBranchTracker.setChild(newNode);
                  firstBranchTracker = newNode;
                  firstBranchPrevBlock = block;
               }
            }
            flipped = !flipped;
         }
      }
      
      int firstBranchHeight = 0;
      firstBranchTracker = firstBranch;
      while (firstBranchTracker != null) {
         firstBranchTracker = firstBranchTracker.child;
         firstBranchHeight++;
      }
      
      int secondBranchHeight = 0;
      secondBranchTracker = secondBranch;
      while (secondBranchTracker != null) {
         secondBranchTracker = secondBranchTracker.child;
         secondBranchHeight++;
      }
      
      int maxHeight = Math.max(firstBranchHeight, secondBranchHeight);
      
      int firstBranchCount = 0;
      firstBranchTracker = firstBranch;
      while (firstBranchTracker.child != null) {
         block = new Block(firstBranchTracker.b.getHash(), people.get(0).getPublicKey());
         spendCoinbaseTx = new Transaction();
         spendCoinbaseTx.addInput(firstBranchTracker.b.getCoinbase().getHash(), 0);
         spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublicKey());
         spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
         spendCoinbaseTx.finalize();
         block.addTransaction(spendCoinbaseTx);
         block.finalize();
         
         if (firstBranchCount < maxHeight - BlockChain.CUT_OFF_AGE - 1) {
             passes = passes && !blockHandler.processBlock(block);
         } else {
             passes = passes && blockHandler.processBlock(block);
         }
        
         firstBranchTracker = firstBranchTracker.child;
         firstBranchCount++;
      }
      
      int secondBranchCount = 0;
      secondBranchTracker = secondBranch;
      while (secondBranchTracker != null) {
         block = new Block(secondBranchTracker.b.getHash(), people.get(0).getPublicKey());
         spendCoinbaseTx = new Transaction();
         spendCoinbaseTx.addInput(secondBranchTracker.b.getCoinbase().getHash(), 0);
         spendCoinbaseTx.addOutput(Block.COINBASE, people.get(1).getPublicKey());
         spendCoinbaseTx.addSignature(people.get(0).getPrivateKey().sign(spendCoinbaseTx.getRawDataToSign(0)), 0);
         spendCoinbaseTx.finalize();
         block.addTransaction(spendCoinbaseTx);
         block.finalize();
         
         if (secondBranchCount < maxHeight - BlockChain.CUT_OFF_AGE - 1) {
             passes = passes && !blockHandler.processBlock(block);
         } else {
             passes = passes && blockHandler.processBlock(block);
         }
         
         secondBranchTracker = secondBranchTracker.child;
         secondBranchCount++;
      }
      
      return UtilCOS.printPassFail(passes);
   }
   
   public static void main(String[] args) throws FileNotFoundException, IOException {
      DropboxTestBlockChain tester = new DropboxTestBlockChain();
      
      int total = 0;
      int numTests = 27;
      
      UtilCOS.printTotalNumTests(numTests);  
      System.out.println("######################\nprocessBlock() tests:\n######################\n");
      total += tester.test1();
      total += tester.test2();
      total += tester.test3();
      total += tester.test14();
      total += tester.test4();
      total += tester.test5();
      total += tester.test6();
      total += tester.test7();
      total += tester.test15();
      total += tester.test16();
      total += tester.test17();
      total += tester.test8();
      total += tester.test9();
      total += tester.test10();
      System.out.println("######################\ncreateBlock() tests:\n######################\n");
      total += tester.test11();
      total += tester.test12();
      total += tester.test13();
      total += tester.test22();
      total += tester.test19();
      total += tester.test20();
      total += tester.test21();
      System.out.println("######################\nCombination tests:\n######################\n");
      total += tester.test23();
      total += tester.test24();
      total += tester.test25();
      total += tester.test18();
      total += tester.test26();
      total += tester.test27();
      
      System.out.println();
      UtilCOS.printNumTestsPassed(total, numTests);
   }  
}