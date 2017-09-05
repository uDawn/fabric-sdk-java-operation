import hyperledger.fabric.operation.Operation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;

public class TestOper {

    @Test
    public void test() throws Exception {

        Operation testOperation = new Operation();
        final boolean needConstruct = false;

        if (needConstruct) {
            testOperation.constructSetup();
        } else {
            try {
                testOperation.setup();
            } catch (Throwable ignored) {
            }
        }

        String accountA = "a";
        String accountB = "b";

        /*
        // ========================= Test Query =======================================================

        String valueA = testOperation.query(accountA);
        String valueB = testOperation.query(accountB);
        System.out.println(String.format("Query for account a, b:  valueA:%s , valueB:%s", valueA, valueB));

        // ========================= Test Transfer =====================================================
        testOperation.transfer(accountA, accountB, "10");
        valueA = testOperation.query(accountA);
        valueB = testOperation.query(accountB);
        System.out.println(String.format("After transfer, account a, b: valueA:%s , valueB:%s", valueA, valueB));

        // ========================= Test Initiate =====================================================
        String newAccount = "sgZhao";
        String newAmount = "1000";
        System.out.println("New account with name " + newAccount + " and value " + newAmount);
        testOperation.initiate(newAccount, newAmount);
        String newAccountVal = testOperation.query(newAccount);
        System.out.println(String.format("newAccountVal:%s", newAccountVal));

        testOperation.transfer(accountA, newAccount, "10");
        valueA = testOperation.query(accountA);
        newAccountVal = testOperation.query(newAccount);
        System.out.println(String.format("After transfer, account a, newAccount: valueA:%s , newAccountVal:%s", valueA, newAccountVal));

        */
        // ========================= Test History Query =================================================
        testOperation.queryHistoryByAccount(accountA);
    }
}
