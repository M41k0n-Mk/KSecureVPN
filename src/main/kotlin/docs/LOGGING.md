# Logging and Session Tracking

KSecureVPN includes a comprehensive logging system designed to help debug issues while maintaining security best practices. The logging system tracks sessions, logs decryption errors with detailed debug information, and ensures that sensitive data (keys, plaintext) never appears in console output or log files.

## Features

### Session Tracking
Every client connection is assigned a unique session ID that is used throughout the connection lifecycle for tracking and correlation of log entries. Session IDs follow the format: `sess-<timestamp>-<uuid>`.

### Secure Logging
The logging system provides two levels of output:
- **Console**: Generic, safe messages suitable for production environments
- **Log Files**: Detailed debug information stored in protected log files

### What Gets Logged
- Session connection/disconnection events
- Authentication attempts (success/failure)
- Decryption errors with full exception details
- Message processing events

### Security Guarantees
- **No sensitive data in logs**: Keys, plaintext messages, and passwords are never logged
- **Protected log files**: Log directory has restrictive permissions (Unix: `rwx------`)
- **Configurable**: Logging can be disabled or configured via environment variables
- **Automatic rotation**: Log files are automatically rotated when they exceed size limits

## Configuration

The logging system can be configured using environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `KSECUREVPN_LOGGING_ENABLED` | `true` | Enable/disable detailed logging to files |
| `KSECUREVPN_LOG_DIR` | `logs` | Directory where log files are stored |
| `KSECUREVPN_LOG_FILE` | `ksecurevpn.log` | Name of the log file |
| `KSECUREVPN_LOG_MAX_SIZE_MB` | `10` | Maximum log file size in MB before rotation |
| `KSECUREVPN_LOG_STACK_TRACES` | `true` | Include stack traces in error logs |

### Example Configuration

```bash
# Enable logging with custom settings
export KSECUREVPN_LOGGING_ENABLED=true
export KSECUREVPN_LOG_DIR=/var/log/ksecurevpn
export KSECUREVPN_LOG_FILE=server.log
export KSECUREVPN_LOG_MAX_SIZE_MB=20

# Run the server
./run-server.sh
```

### Production Configuration

For production environments, we recommend:

```bash
# Enable logging but store in a protected location
export KSECUREVPN_LOGGING_ENABLED=true
export KSECUREVPN_LOG_DIR=/var/log/ksecurevpn  # Ensure this has restrictive permissions
export KSECUREVPN_LOG_MAX_SIZE_MB=50
export KSECUREVPN_LOG_STACK_TRACES=true

# Ensure the log directory has proper permissions
sudo mkdir -p /var/log/ksecurevpn
sudo chmod 700 /var/log/ksecurevpn
sudo chown <service-user>:<service-group> /var/log/ksecurevpn
```

## Log File Format

Log entries follow this structure:

```
[2025-10-20 05:35:00.123] [ERROR] [Session: sess-123456-abc12345]
Context: Authentication phase
Error Type: javax.crypto.BadPaddingException
Error Message: Given final block not properly padded
Stack Trace:
  at javax.crypto.CipherInputStream.getMoreData(...)
  at javax.crypto.CipherInputStream.read(...)
  ...
Additional Info:
  remoteAddress: /192.168.1.100
  cipherTextLength: 256
  ivLength: 16
---
```

## Access Control

### Log File Permissions

The logging system automatically attempts to set restrictive permissions on the log directory:
- **Unix/Linux/macOS**: `rwx------` (700) - Only the owner can read, write, or execute
- **Windows**: Relies on NTFS permissions; should be configured manually

### Best Practices

1. **Restrict access**: Only authorized administrators should have access to log files
2. **Regular rotation**: Monitor log file sizes and rotate them regularly
3. **Secure storage**: Store logs on encrypted filesystems when possible
4. **Audit access**: Monitor who accesses log files
5. **Retention policy**: Define and implement a log retention policy

### Production Deployment

For production deployments:

1. **Use a dedicated log directory** with restricted permissions:
   ```bash
   sudo mkdir -p /var/log/ksecurevpn
   sudo chmod 700 /var/log/ksecurevpn
   sudo chown vpnuser:vpnuser /var/log/ksecurevpn
   ```

2. **Configure log rotation** using `logrotate`:
   ```
   /var/log/ksecurevpn/*.log {
       daily
       rotate 7
       compress
       delaycompress
       notifempty
       missingok
       create 0600 vpnuser vpnuser
   }
   ```

3. **Monitor logs** using centralized logging systems (e.g., ELK stack, Splunk)

4. **Regular audits**: Periodically review logs for security incidents

## Troubleshooting

### Finding Session IDs
When investigating issues, use session IDs to correlate log entries:
```bash
grep "sess-123456-abc12345" logs/ksecurevpn.log
```

### Common Decryption Errors

**BadPaddingException**: Usually indicates a key mismatch or corrupted data
```
[ERROR] [Session: sess-xxx] 
Context: Authentication phase
Error Type: javax.crypto.BadPaddingException
```

**IllegalBlockSizeException**: Indicates incomplete or improperly sized encrypted data
```
[ERROR] [Session: sess-xxx]
Context: Message decryption
Error Type: javax.crypto.IllegalBlockSizeException
```

### Disabling Logging

To disable detailed logging (only console output remains):
```bash
export KSECUREVPN_LOGGING_ENABLED=false
```

## Privacy and Compliance

The logging system is designed with privacy in mind:
- **No PII**: Personal identifiable information is minimized in logs
- **No secrets**: Encryption keys and passwords are never logged
- **No plaintext**: Decrypted message content is never logged
- **IP addresses**: Logged only in detailed logs, not in console output

For GDPR or other compliance requirements, implement appropriate log retention and deletion policies.

## Integration

To use the logging system in custom components:

```kotlin
import logging.LogLevel
import logging.SecureLogger
import session.SessionTracker

// Create a session
val sessionId = SessionTracker.createSession(clientAddress)

// Get the logger instance
val logger = SecureLogger.getInstance()

// Log events
logger.logSessionEvent(
    sessionId = sessionId,
    level = LogLevel.INFO,
    message = "Custom event occurred",
    details = mapOf("key" to "value")
)

// Log errors
logger.logDecryptionError(
    sessionId = sessionId,
    context = "Custom decryption operation",
    error = exception,
    additionalInfo = mapOf("detail" to "info")
)

// End session
SessionTracker.endSession(sessionId)
```
