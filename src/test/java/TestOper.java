import hyperledger.fabric.operation.Operation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;

public class TestOper {

    @Test
    public void test() throws Exception{
        String res_1 = "not1";
        String res_2 = "not2";
        String res_3 = "not3";
        String res_4 = "not4";
        String res_5 = "not5";
        String res_6 = "not6";
        String res_7 = "not7";
        String new_account = "cc";
        String new_amount = "1000";

        Operation test_operation = new Operation();
        //test_operation.constructSetup();
        try{
            test_operation.setup();
        }catch (Throwable throwable){

        }
        res_1 = test_operation.query("a");
        res_2 = test_operation.query("b");
        System.out.println(String.format("New construct res_1:%s , res_2:%s", res_1, res_2));
        test_operation.transfer("a", "b", "10");
        res_3 = test_operation.query("a");
        res_4 = test_operation.query("b");
        System.out.println(String.format("res_3:%s , res_4:%s", res_3, res_4));

        System.out.println("New account!");
        test_operation.initiate(new_account , new_amount);
        res_5 = test_operation.query(new_account);
        System.out.println(String.format("res_5:%s" , res_5));
        test_operation.transfer("a" , new_account , "10");
        res_6 = test_operation.query("a");
        res_7 = test_operation.query(new_account);
        System.out.println(String.format("res_6:%s , res_7:%s", res_6, res_7));

        //Log logger = LogFactory.getLog(TestOper.class);

        res_1 = test_operation.query("a");
        res_2 = test_operation.query("b");
        System.out.println(String.format("Reconstructure  res_1:%s , res_2:%s" , res_1 , res_2));
        test_operation.transfer("a" , "b" , "10");
        res_3 = test_operation.query("a");
        res_4 = test_operation.query("b");
        System.out.println(String.format("res_3:%s , res_4:%s" , res_3 , res_4));
    }
}
