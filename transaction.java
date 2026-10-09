import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Immutable record of a deposit or withdrawal. */
final class Transaction {
    public enum Type {
        DEPOSIT,
        WITHDRAWAL
    }

    private final Type type;
    private final BigDecimal amount;
    private final BigDecimal balanceAfter;
    private final Instant timestamp;

    Transaction(Type type, BigDecimal amount, BigDecimal balanceAfter) {
        this(type, amount, balanceAfter, Instant.now());
    }

    Transaction(Type type, BigDecimal amount, BigDecimal balanceAfter, Instant timestamp) {
        this.type = Objects.requireNonNull(type, "Transaction type is required.");
        this.amount = Objects.requireNonNull(amount, "Amount is required.");
        this.balanceAfter = Objects.requireNonNull(balanceAfter, "Balance is required.");
        this.timestamp = Objects.requireNonNull(timestamp, "Timestamp is required.");
    }

    public Type getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}