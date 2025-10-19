#!/bin/bash

# Script para demonstrar a comunicação cliente-servidor com respostas

echo "=== Demonstração da comunicação cliente-servidor com respostas ==="
echo

echo "1. Compile o projeto:"
echo "mvn compile"
echo

echo "2. Em um terminal, inicie o servidor:"
echo "mvn exec:java -Dexec.mainClass=\"MainKt\" -Dexec.args=\"server\""
echo

echo "3. Em outro terminal, execute o cliente:"
echo "mvn exec:java -Dexec.mainClass=\"MainKt\" -Dexec.args=\"client\""
echo

echo "4. Você verá logs como:"
echo "[CLIENT] Connecting to 127.0.0.1:9000..."
echo "[CLIENT] Connected to server: 127.0.0.1"
echo "[CLIENT] Preparing authentication for user: testuser"
echo "[CLIENT] Sending authentication (52 bytes total)"
echo "[CLIENT] Authentication sent in 15ms"
echo "[CLIENT-AUTH-RESPONSE] Starting read of 1 bytes with 5000ms timeout"
echo "[CLIENT-AUTH-RESPONSE] Read 1/1 bytes in 23ms"
echo "[CLIENT] ✅ Authentication SUCCESS"
echo "[CLIENT] Encrypting message: 'Hello Server' (12 chars)"
echo "[CLIENT] Sending message (44 bytes total)"
echo "[CLIENT] Message sent in 8ms"
echo "[CLIENT-MSG-ACK] Starting read of 1 bytes with 3000ms timeout"
echo "[CLIENT-MSG-ACK] Read 1/1 bytes in 15ms"
echo "[CLIENT] ✅ Message received and processed by server"
echo "[CLIENT] Session completed in 1234ms"