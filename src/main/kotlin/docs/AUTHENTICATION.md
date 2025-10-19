```md
# Authentication (MVP)

This file documents the simple username/password authentication mechanism (MVP).

Design
- Passwords are stored as PBKDF2WithHmacSHA256 derived keys with per-user salt and iterations.
- Storage: `config/users.properties` with lines formatted as:
  `username=iterations:base64salt:base64hash`

Client-server flow
1. Client connects to server TCP.
2. Client sends first encrypted frame containing authentication payload:
   - Plaintext payload format:
     ```
     AUTH
     <username>
     <password>
     ```
   - The payload is encrypted with the shared AES key (existing AESCipher).
3. Server decrypts first frame and validates credentials via AuthService.
4. If authentication succeeds, server continues to read normal frames; otherwise, it closes the connection.

Management
- Use `auth.UserTool` to create users locally (run `create-user <username>` and follow prompts).
- For production, populate `config/users.properties` securely (limit file permissions).

Local development key handling
- The application requires a 32-byte AES key provided as base64 via the `KSECUREVPN_KEY` environment variable.
- For security, the application will not print keys to stdout or logs. For local development you can generate and store a key securely:
  - Generate a key (Linux/macOS):
    ```bash
    head -c 32 /dev/urandom | base64 | tee ksecurevpn.key
    ```
  - Restrict file permissions:
    ```bash
    chmod 600 ksecurevpn.key
    ```
  - Use it for running the app:
    ```bash
    export KSECUREVPN_KEY=$(cat ksecurevpn.key)
    ./run-your-app
    ```
  - Alternatively, store the key in a secrets manager (Vault/KMS) and inject it into the environment at runtime.

If the application generates a key automatically (only in dev/no env provided), it will write the key to `./ksecurevpn.key` with restrictive permissions and print instructions on how to set `KSECUREVPN_KEY` instead of printing the key material.

Security notes
- Credentials are transmitted over an encrypted channel (AES); do not print passwords to logs.
- This is an MVP. Future improvements:
  - move to a secure secret store (Vault/KMS),
  - add account lockout, rate-limiting,
  - upgrade to AEAD (AES-GCM) if not yet present,
  - add token or certificate-based auth.
```