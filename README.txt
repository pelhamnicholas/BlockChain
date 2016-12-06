Author: Nicholas Pelham <npelh001@ucr.edu>
SSID:   861238376

I worked alone on this project, with only conversational involvement with other students and no collaboration on code or implementation specifics. Outside of office hours with Payas I had no one else look at my code either for my benefit or theirs. Payas helped tremendously in understanding some concepts I was unclear on, notably the CUT_OFF_AGE, maxHeight, and heads list; as well as helped me to pass the final three tests by explaining the purpose of the transaction pool in the BlockChain.

Changes to Given Files:

I overloaded the getRawDataToSign method in the Transaction Class to take an Input Oject instead of an index, because my implementation of the isValidTx member in TxHandler used for each loops to iterate through all inputs. It seemed a more intuitive solution to overload the member to accept an Input object instead of using an Input object to retrieve an index for the member to use that index to get the Input object that I had started with.