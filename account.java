import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Represents a bank account and enforces its balance rules. */
final class Account {
    private final String accountNumber;
    private final String ownerUsername;
    private final List<Transaction> transactions = new ArrayList<>();
    private BigDecimal balance = new BigDecimal("0.00");

    public Account(String accountNumber, String ownerUsername) {
        this.accountNumber = requireText(accountNumber, "Account number");
        this.ownerUsername = requireText(ownerUsername, "Owner username");
    }

    Account(String accountNumber, String ownerUsername, BigDecimal restoredBalance,
            List<Transaction> restoredTransactions) {
        this(accountNumber, ownerUsername);
        Objects.requireNonNull(restoredBalance, "Balance is required.");
        Objects.requireNonNull(restoredTransactions, "Transaction history is required.");

        BigDecimal calculatedBalance = new BigDecimal("0.00");
        for (Transaction transaction : restoredTransactions) {
            Objects.requireNonNull(transaction, "Transaction history cannot contain null.");
            BigDecimal amount = validateAmount(transaction.getAmount());
            BigDecimal expectedBalance;
            if (transaction.getType() == Transaction.Type.DEPOSIT) {
                expectedBalance = calculatedBalance.add(amount);
            } else {
                if (amount.compareTo(calculatedBalance) > 0) {
                    throw new IllegalArgumentException("Invalid persisted withdrawal history.");
                }
                expectedBalance = calculatedBalance.subtract(amount);
            }
            if (expectedBalance.compareTo(transaction.getBalanceAfter()) != 0) {
                throw new IllegalArgumentException("Persisted transaction balances are inconsistent.");
            }
            calculatedBalance = expectedBalance;
        }

        BigDecimal validBalance = restoredBalance.setScale(2, RoundingMode.UNNECESSARY);
        if (validBalance.signum() < 0 || validBalance.compareTo(calculatedBalance) != 0) {
            throw new IllegalArgumentException("Persisted account balance does not match its history.");
        }
        this.balance = validBalance;
        this.transactions.addAll(restoredTransactions);
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public synchronized BigDecimal getBalance() {
        return balance;
    }

    /** Adds a positive amount to the account and records the resulting balance. */
    public synchronized void deposit(BigDecimal amount) {
        BigDecimal validAmount = validateAmount(amount);
        balance = balance.add(validAmount);
        transactions.add(new Transaction(
                Transaction.Type.DEPOSIT, validAmount, balance));
    }

    /** Removes a positive amount, rejecting withdrawals greater than the balance. */
    public synchronized void withdraw(BigDecimal amount) {
        BigDecimal validAmount = validateAmount(amount);
        if (validAmount.compareTo(balance) > 0) {
            throw new IllegalStateException("Insufficient funds.");
        }

        balance = balance.subtract(validAmount);
        transactions.add(new Transaction(
                Transaction.Type.WITHDRAWAL, validAmount, balance));
    }

    /** Returns a read-only snapshot of this account's transaction history. */
    public synchronized List<Transaction> getTransactions() {
        return Collections.unmodifiableList(new ArrayList<>(transactions));
    }

    private static BigDecimal validateAmount(BigDecimal amount) {
        Objects.requireNonNull(amount, "Amount is required.");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }

        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Amount cannot have fractions of a cent.", exception);
        }
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " is required.");
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank.");
        }
        return value.trim();
    }
}