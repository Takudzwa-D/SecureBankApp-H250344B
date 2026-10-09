import java.io.Console;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Console banking application. Data files are created in the working directory. */
final class Main {
	private static final Path DATA_DIRECTORY = Path.of("data");
	private static final Path USERS_FILE = DATA_DIRECTORY.resolve("users.txt");
	private static final Path ACCOUNTS_FILE = DATA_DIRECTORY.resolve("accounts.txt");
	private static final int HASH_ITERATIONS = 210_000;
	private static final int SALT_BYTES = 16;
	private static final int HASH_BITS = 256;
	private static final SecureRandom RANDOM = new SecureRandom();

	private final Scanner scanner = new Scanner(System.in);
	private final Map<String, User> users = new LinkedHashMap<>();
	private final Map<String, Account> accounts = new LinkedHashMap<>();
	private boolean running = true;

	public static void main(String[] args) {
		try {
			new Main().run();
		} catch (IOException | GeneralSecurityException exception) {
			System.err.println("Unable to start or save SecureBank: " + exception.getMessage());
		}
	}

	private void run() throws IOException, GeneralSecurityException {
		loadData();
		System.out.println("Welcome to SecureBank.");
		while (running) {
			System.out.println("\n1. Log in   2. Register   3. Exit");
			String choice = prompt("Choose an option: ");
			switch (choice) {
				case "1" -> login();
				case "2" -> register();
				case "3" -> running = false;
				default -> System.out.println("Please choose 1, 2, or 3.");
			}
		}
		System.out.println("Goodbye.");
	}

	private void register() throws IOException, GeneralSecurityException {
		String username = prompt("Choose a username (3-32 letters, digits, . _ or -): ").trim();
		if (!username.matches("[A-Za-z0-9._-]{3,32}")) {
			System.out.println("Username must be 3-32 characters and use only letters, digits, ., _ or -.");
			return;
		}
		String key = username.toLowerCase(Locale.ROOT);
		if (users.containsKey(key)) {
			System.out.println("That username is already registered.");
			return;
		}

		char[] password = readPassword("Choose a password (at least 10 characters): ");
		if (password.length < 10) {
			clear(password);
			System.out.println("Password must contain at least 10 characters.");
			return;
		}
		byte[] salt = new byte[SALT_BYTES];
		RANDOM.nextBytes(salt);
		byte[] passwordHash = hashPassword(password, salt);
		clear(password);
		users.put(key, new User(username, salt, passwordHash));
		try {
			saveUsers();
		} catch (IOException exception) {
			users.remove(key);
			throw exception;
		}
		System.out.println("Registration complete. You can now log in.");
	}

	private void login() throws IOException, GeneralSecurityException {
		String username = prompt("Username: ").trim();
		String key = username.toLowerCase(Locale.ROOT);
		User user = users.get(key);
		char[] password = readPassword("Password: ");
		boolean authenticated = false;
		if (user != null) {
			byte[] candidate = hashPassword(password, user.salt);
			authenticated = MessageDigest.isEqual(candidate, user.passwordHash);
		}
		clear(password);
		if (!authenticated) {
			System.out.println("Invalid username or password.");
			return;
		}
		System.out.println("Welcome, " + user.username + ".");
		accountMenu(user);
	}

	private void accountMenu(User user) throws IOException {
		boolean loggedIn = true;
		while (loggedIn) {
			System.out.println("\n1. Create account   2. List accounts   3. View balance/history");
			System.out.println("4. Deposit   5. Withdraw   6. Log out");
			switch (prompt("Choose an option: ")) {
				case "1" -> createAccount(user);
				case "2" -> listAccounts(user);
				case "3" -> viewAccount(user);
				case "4" -> transact(user, true);
				case "5" -> transact(user, false);
				case "6" -> loggedIn = false;
				default -> System.out.println("Please choose a number from 1 to 6.");
			}
		}
	}

	private void createAccount(User user) throws IOException {
		String accountNumber;
		do {
			accountNumber = String.format(Locale.ROOT, "%010d", RANDOM.nextInt(1_000_000_000));
		} while (accounts.containsKey(accountNumber));
		Account account = new Account(accountNumber, user.username);
		accounts.put(accountNumber, account);
		try {
			saveAccounts();
		} catch (IOException exception) {
			accounts.remove(accountNumber);
			throw exception;
		}
		System.out.println("Account created. Account number: " + accountNumber);
	}

	private void listAccounts(User user) {
		boolean found = false;
		for (Account account : accounts.values()) {
			if (account.getOwnerUsername().equalsIgnoreCase(user.username)) {
				System.out.println(account.getAccountNumber() + " — balance: $" + account.getBalance());
				found = true;
			}
		}
		if (!found) {
			System.out.println("You do not have any accounts yet.");
		}
	}

	private void viewAccount(User user) {
		Account account = ownedAccount(user);
		if (account == null) return;
		System.out.println("Balance: $" + account.getBalance());
		List<Transaction> history = account.getTransactions();
		if (history.isEmpty()) {
			System.out.println("No transactions yet.");
			return;
		}
		System.out.println("Transaction history:");
		for (Transaction transaction : history) {
			System.out.printf(Locale.ROOT, "%s | %s | $%s | balance $%s%n",
					transaction.getTimestamp(), transaction.getType(),
					transaction.getAmount(), transaction.getBalanceAfter());
		}
	}

	private void transact(User user, boolean deposit) throws IOException {
		Account account = ownedAccount(user);
		if (account == null) return;
		BigDecimal amount;
		try {
			amount = new BigDecimal(prompt("Amount: ").trim());
			if (amount.scale() > 2 || amount.signum() <= 0) {
				throw new IllegalArgumentException("Enter a positive amount with at most two decimal places.");
			}
		} catch (IllegalArgumentException exception) {
			System.out.println("Invalid amount. Enter a positive amount with at most two decimal places.");
			return;
		}

		BigDecimal previousBalance = account.getBalance();
		int previousCount = account.getTransactions().size();
		try {
			if (deposit) account.deposit(amount);
			else account.withdraw(amount);
			saveAccounts();
			System.out.println((deposit ? "Deposit" : "Withdrawal") + " complete. New balance: $" + account.getBalance());
		} catch (IllegalStateException exception) {
			System.out.println(exception.getMessage());
		} catch (IOException exception) {
			// Roll back the in-memory change so the displayed state agrees with the saved state.
			List<Transaction> oldHistory = new ArrayList<>(account.getTransactions());
			oldHistory = oldHistory.subList(0, previousCount);
			accounts.put(account.getAccountNumber(), new Account(
					account.getAccountNumber(), account.getOwnerUsername(), previousBalance, oldHistory));
			throw exception;
		}
	}

	private Account ownedAccount(User user) {
		String number = prompt("Account number: ").trim();
		Account account = accounts.get(number);
		if (account == null || !account.getOwnerUsername().equalsIgnoreCase(user.username)) {
			System.out.println("Account not found.");
			return null;
		}
		return account;
	}

	private void loadData() throws IOException, GeneralSecurityException {
		Files.createDirectories(DATA_DIRECTORY);
		if (Files.exists(USERS_FILE)) {
			for (String line : Files.readAllLines(USERS_FILE, StandardCharsets.UTF_8)) {
				if (line.isBlank()) continue;
				String[] fields = line.split("\\t", -1);
				if (fields.length != 3) throw new IOException("Invalid user data file.");
				try {
					User user = new User(fields[0], Base64.getDecoder().decode(fields[1]),
							Base64.getDecoder().decode(fields[2]));
					users.put(user.username.toLowerCase(Locale.ROOT), user);
				} catch (IllegalArgumentException exception) {
					throw new IOException("Invalid user data file.", exception);
				}
			}
		}
		if (Files.exists(ACCOUNTS_FILE)) {
			for (String line : Files.readAllLines(ACCOUNTS_FILE, StandardCharsets.UTF_8)) {
				if (line.isBlank()) continue;
				String[] fields = line.split("\\t", -1);
				if (fields.length != 4) throw new IOException("Invalid account data file.");
				try {
					List<Transaction> history = new ArrayList<>();
					if (!fields[3].isEmpty()) {
						for (String encodedTransaction : fields[3].split(";", -1)) {
							String[] transactionFields = encodedTransaction.split(",", -1);
							if (transactionFields.length != 4) throw new IllegalArgumentException();
							history.add(new Transaction(Transaction.Type.valueOf(transactionFields[0]),
									new BigDecimal(transactionFields[1]), new BigDecimal(transactionFields[2]),
									Instant.parse(transactionFields[3])));
						}
					}
					Account account = new Account(fields[0], fields[1], new BigDecimal(fields[2]), history);
					if (!users.containsKey(account.getOwnerUsername().toLowerCase(Locale.ROOT))
							|| accounts.putIfAbsent(account.getAccountNumber(), account) != null) {
						throw new IllegalArgumentException();
					}
				} catch (RuntimeException exception) {
					throw new IOException("Invalid account data file.", exception);
				}
			}
		}
	}

	private void saveUsers() throws IOException {
		List<String> lines = new ArrayList<>();
		for (User user : users.values()) {
			lines.add(user.username + "\t" + Base64.getEncoder().encodeToString(user.salt)
					+ "\t" + Base64.getEncoder().encodeToString(user.passwordHash));
		}
		writeAtomically(USERS_FILE, lines);
	}

	private void saveAccounts() throws IOException {
		List<String> lines = new ArrayList<>();
		for (Account account : accounts.values()) {
			StringBuilder history = new StringBuilder();
			for (Transaction transaction : account.getTransactions()) {
				if (history.length() > 0) history.append(';');
				history.append(transaction.getType()).append(',')
						.append(transaction.getAmount().toPlainString()).append(',')
						.append(transaction.getBalanceAfter().toPlainString()).append(',')
						.append(transaction.getTimestamp());
			}
			lines.add(account.getAccountNumber() + "\t" + account.getOwnerUsername() + "\t"
					+ account.getBalance().toPlainString() + "\t" + history);
		}
		writeAtomically(ACCOUNTS_FILE, lines);
	}

	private static void writeAtomically(Path target, List<String> lines) throws IOException {
		Path temporary = Files.createTempFile(DATA_DIRECTORY, target.getFileName().toString(), ".tmp");
		try {
			Files.write(temporary, lines, StandardCharsets.UTF_8);
			try {
				Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException exception) {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	private static byte[] hashPassword(char[] password, byte[] salt) throws GeneralSecurityException {
		PBEKeySpec spec = new PBEKeySpec(password, salt, HASH_ITERATIONS, HASH_BITS);
		try {
			return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
		} finally {
			spec.clearPassword();
		}
	}

	private String prompt(String message) {
		System.out.print(message);
		return scanner.nextLine();
	}

	private char[] readPassword(String message) {
		Console console = System.console();
		if (console != null) return console.readPassword("%s", message);
		return prompt(message).toCharArray();
	}

	private static void clear(char[] value) {
		java.util.Arrays.fill(value, '\0');
	}

	private static final class User {
		private final String username;
		private final byte[] salt;
		private final byte[] passwordHash;

		private User(String username, byte[] salt, byte[] passwordHash) {
			this.username = username;
			this.salt = salt.clone();
			this.passwordHash = passwordHash.clone();
		}
	}
}
