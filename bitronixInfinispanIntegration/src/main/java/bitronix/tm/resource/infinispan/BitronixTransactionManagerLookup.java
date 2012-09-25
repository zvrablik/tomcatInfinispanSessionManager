package bitronix.tm.resource.infinispan;

import bitronix.tm.TransactionManagerServices;
import org.infinispan.transaction.lookup.TransactionManagerLookup;

import javax.transaction.TransactionManager;

/**
 * Lookup transaction manager directly from bitronix.
 * Details see http://old.nabble.com/file/p26986702/btm-ispn.zip
 */
public class BitronixTransactionManagerLookup implements TransactionManagerLookup {
        @Override
        public TransactionManager getTransactionManager() throws Exception {
            return TransactionManagerServices.getTransactionManager();
        }
}
