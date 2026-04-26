public class TestFilter {
    public static void main(String[] args) {
        String sender = "vm-360edu".toLowerCase();
        String filterPattern = "360";
        
        String senderDigits = sender.replaceAll("[^0-9]", "");
        String queryDigits = filterPattern.replaceAll("[^0-9]", "");
        
        System.out.println("Sender Digits: " + senderDigits);
        System.out.println("Query Digits: " + queryDigits);
        System.out.println("Match: " + sender.contains(filterPattern));
        System.out.println("Match Digits: " + (!queryDigits.isEmpty() && senderDigits.contains(queryDigits)));
    }
}
