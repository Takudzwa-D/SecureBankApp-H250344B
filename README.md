# SecureBankApp

A console-based banking application demonstrating object-oriented design and secure coding basics.

## Requirements

- Java 17 or newer (uses the standard JDK only).

## Compile and run

From this directory, compile and run the application:

```powershell
javac main.java account.java transaction.java
java Main
```

The app supports registration and login, account creation, balance and transaction-history viewing, deposits, and withdrawals. Registration requires a username of 3–32 allowed characters and a password of at least 10 characters.

## Persistence

The application creates a `data` directory in its working directory. `users.txt` contains usernames and Base64-encoded salts/password hashes; passwords are never stored in plaintext. `accounts.txt` contains account balances and full transaction histories. Updates are written through temporary files and atomically replaced where the filesystem supports it.

Keep the `data` directory private and back it up securely. This is an educational, local-file application—not production banking software. It does not provide encrypted-at-rest files, multi-process locking, account recovery, or production-grade operational controls.