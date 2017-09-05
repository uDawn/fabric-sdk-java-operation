package hyperledger.fabric.beans;

import java.util.ArrayList;

public class KeyModifications {
    public ArrayList<ModicationItem> History;

    public KeyModifications() {
    }

    public KeyModifications(ArrayList<ModicationItem> history) {
        History = history;
    }

    public ArrayList<ModicationItem> getHistory() {
        return History;
    }

    public void setHistory(ArrayList<ModicationItem> history) {
        History = history;
    }

    public class ModicationItem {
        public String TxId;
        public String Value;
        public String Timestamp;
        public boolean IsDelete;

        public ModicationItem() {
        }

        public ModicationItem(String txId, String value, String timestamp, boolean isDelete) {
            TxId = txId;
            Value = value;
            Timestamp = timestamp;
            IsDelete = isDelete;
        }

        public String getTxId() {
            return TxId;
        }

        public void setTxId(String txId) {
            TxId = txId;
        }

        public String getValue() {
            return Value;
        }

        public void setValue(String value) {
            Value = value;
        }

        public String getTimestamp() {
            return Timestamp;
        }

        public void setTimestamp(String timestamp) {
            Timestamp = timestamp;
        }

        public boolean isDelete() {
            return IsDelete;
        }

        public void setDelete(boolean delete) {
            IsDelete = delete;
        }
    }
}
