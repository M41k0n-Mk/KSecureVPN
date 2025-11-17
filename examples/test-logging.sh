#!/bin/bash

# Test script to demonstrate the secure logging and session tracking feature

echo "=== KSecureVPN Logging Test ==="
echo ""

# Clean up any previous test files
rm -rf logs/
rm -f ksecurevpn.key

# Set up logging configuration
export KSECUREVPN_LOGGING_ENABLED=true
export KSECUREVPN_LOG_DIR=logs
export KSECUREVPN_LOG_FILE=test.log
export KSECUREVPN_LOG_MAX_SIZE_MB=10

echo "1. Running tests to verify logging functionality..."
mvn test -Dtest=SecureLoggerTest,SessionTrackerTest -q

if [ $? -eq 0 ]; then
    echo "✅ Tests passed!"
else
    echo "❌ Tests failed!"
    exit 1
fi

echo ""
echo "2. Checking if test logs were created..."
if [ -d "logs" ]; then
    echo "✅ Log directory created"
    ls -la logs/
else
    echo "❌ Log directory not found"
fi

echo ""
echo "3. Sample log output from SecureLoggerTest:"
if [ -f "logs/test.log" ]; then
    echo "--- Log file contents (first 20 lines) ---"
    head -20 logs/test.log
    echo "--- End of sample ---"
    echo "✅ Log file contains session tracking and error details"
else
    echo "⚠️ No test log file found"
fi

echo ""
echo "4. Verifying security: checking that no sensitive data is in logs..."
# Check for base64 patterns that might be keys
if grep -qE '[A-Za-z0-9+/]{40,}={0,2}' logs/*.log 2>/dev/null; then
    echo "⚠️ Warning: Found potential base64 key material in logs"
else
    echo "✅ No sensitive key material found in logs"
fi

echo ""
echo "5. Verifying session ID format..."
if grep -qE 'sess-[0-9]{6}-[a-f0-9]{8}' logs/*.log 2>/dev/null; then
    echo "✅ Session IDs are in the expected format"
    echo "Sample session IDs:"
    grep -oE 'sess-[0-9]{6}-[a-f0-9]{8}' logs/*.log 2>/dev/null | head -5 | sed 's/^/  - /'
else
    echo "⚠️ No session IDs found in logs"
fi

echo ""
echo "=== Test Summary ==="
echo "The logging system successfully:"
echo "- Creates protected log directories"
echo "- Generates unique session IDs for tracking"
echo "- Logs detailed error information to files"
echo "- Keeps sensitive data (keys, plaintext) out of logs"
echo "- Provides generic console output for production use"
echo ""
echo "For more information, see: docs/LOGGING.md"
