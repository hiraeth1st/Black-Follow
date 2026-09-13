import com.blackapps.follow.RetryPolicy;

public class RetryPolicyTest {
    private static int checks;
    static void check(boolean ok,String name) {checks++;if(!ok)throw new AssertionError(name);}
    public static void main(String[] args) {
        long now=1_700_000_000_000L;
        check(RetryPolicy.serverDelay("120",now)==120000,"server delta seconds");
        check(RetryPolicy.serverDelay("Tue, 14 Nov 2023 22:15:20 GMT",now)==120000,"server HTTP date");
        check(RetryPolicy.serverDelay("Tue, 14 Nov 2023 22:12:20 GMT",now)==0,"expired HTTP date");
        check(RetryPolicy.serverDelay(null,now)==-1,"no supplied duration");
        check(RetryPolicy.serverDelay("broken",now)==-1,"invalid header does not become a deadline");
        check(RetryPolicy.serverDelay("-10",now)==-1,"negative duration rejected");
        check(RetryPolicy.serverDelay("9999999999999999999999999",now)>86400000,"overflow cannot erase a server wait");
        check(RetryPolicy.delay(-1,0)==900000,"unknown duration starts at local 15 minutes");
        check(RetryPolicy.delay(-1,1)==1800000,"repeated limit doubles wait");
        check(RetryPolicy.delay(-1,20)==86400000,"local maximum one day");
        check(RetryPolicy.delay(172800000,0)==172800000,"server wait is not truncated at local maximum");
        check(RetryPolicy.delay(0,0)==60000,"minimum wait avoids a retry loop");
        check(RetryPolicy.until(now+86400000,now,900000)==now+86400000,"existing legacy deadline is not silently removed");
        check(RetryPolicy.until(now-1,now,900000)==now+900000,"expired deadline permits new wait");
        check(RetryPolicy.until(0,now,Long.MAX_VALUE)==Long.MAX_VALUE,"deadline arithmetic does not overflow");
        System.out.println("PASS: "+checks+" retry policy checks");
    }
}
