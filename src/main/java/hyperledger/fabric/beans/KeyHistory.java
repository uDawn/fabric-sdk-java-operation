package hyperledger.fabric.beans;

import java.util.ArrayList;
import java.util.List;

public class KeyHistory {
    private List<KeyHistoryItem> historyItemList;

    public KeyHistory() {
        historyItemList = new ArrayList<KeyHistoryItem>();
    }

    public void addItem(KeyHistoryItem item) {
        this.historyItemList.add(item);
    }

    public List<KeyHistoryItem> getHistoryItemList() {
        return historyItemList;
    }

    public static class KeyHistoryItem {
        String accountFrom;
        String accountTo;
        Double transferValue;
        String txID;
        String timeStamp;
        Double currentValue;

        public KeyHistoryItem() {
        }

        public KeyHistoryItem(String accountFrom, String accountTo, Double transferValue, String txID, String timeStamp, Double currentValue) {
            this.accountFrom = accountFrom;
            this.accountTo = accountTo;
            this.transferValue = transferValue;
            this.txID = txID;
            this.timeStamp = timeStamp;
            this.currentValue = currentValue;
        }

        public String getAccountFrom() {
            return accountFrom;
        }

        public void setAccountFrom(String accountFrom) {
            this.accountFrom = accountFrom;
        }

        public String getAccountTo() {
            return accountTo;
        }

        public void setAccountTo(String accountTo) {
            this.accountTo = accountTo;
        }

        public Double getTransferValue() {
            return transferValue;
        }

        public void setTransferValue(Double transferValue) {
            this.transferValue = transferValue;
        }

        public String getTxID() {
            return txID;
        }

        public void setTxID(String txID) {
            this.txID = txID;
        }

        public String getTimeStamp() {
            return timeStamp;
        }

        public void setTimeStamp(String timeStamp) {
            this.timeStamp = timeStamp;
        }

        public Double getCurrentValue() {
            return currentValue;
        }

        public void setCurrentValue(Double currentValue) {
            this.currentValue = currentValue;
        }
    }
}
