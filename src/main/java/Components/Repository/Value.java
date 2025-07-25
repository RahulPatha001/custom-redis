package Components.Repository;

import java.time.LocalDateTime;

public class Value {

    public String val;
    public LocalDateTime created;
    public LocalDateTime expiry;
    public boolean isDeletedinTransaction; // a small flag to delete after transaction

    public Value(String val, LocalDateTime created, LocalDateTime expiry) {
        this.val = val;
        this.created = created;
        this.expiry = expiry;
        this.isDeletedinTransaction = false;
    }
}
