package com.acme.jitsi.observability;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class FakeRedisServer implements Closeable {

  private static final byte[] CRLF = "\r\n".getBytes(StandardCharsets.UTF_8);

  private final ServerSocket serverSocket;
  private final Thread acceptThread;
  private final Map<String, String> values = new ConcurrentHashMap<>();
  private final Map<String, Long> expirations = new ConcurrentHashMap<>();
  private final List<String> observedCommands = new CopyOnWriteArrayList<>();

  private volatile boolean running = true;

  private FakeRedisServer(ServerSocket serverSocket) {
    this.serverSocket = serverSocket;
    this.acceptThread = new Thread(this::acceptLoop, "fake-redis-server");
    this.acceptThread.setDaemon(true);
    this.acceptThread.start();
  }

  public static FakeRedisServer start() throws IOException {
    ServerSocket socket = new ServerSocket(0);
    return new FakeRedisServer(socket);
  }

  public int getPort() {
    return serverSocket.getLocalPort();
  }

  public List<String> observedCommands() {
    return List.copyOf(observedCommands);
  }

  @Override
  public void close() throws IOException {
    running = false;
    serverSocket.close();
    try {
      acceptThread.join(2000);
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
    }
  }

  private void acceptLoop() {
    while (running) {
      try {
        Socket socket = serverSocket.accept();
        Thread clientThread = new Thread(() -> handleClient(socket), "fake-redis-client");
        clientThread.setDaemon(true);
        clientThread.start();
      } catch (SocketException socketException) {
        if (running) {
          throw new IllegalStateException("Fake Redis server socket failed", socketException);
        }
        return;
      } catch (IOException ioException) {
        if (running) {
          throw new IllegalStateException("Failed to accept fake Redis connection", ioException);
        }
        return;
      }
    }
  }

  private void handleClient(Socket socket) {
    try (socket;
         InputStream rawInput = new BufferedInputStream(socket.getInputStream());
         OutputStream rawOutput = new BufferedOutputStream(socket.getOutputStream())) {
      while (running && !socket.isClosed()) {
        List<String> command = readCommand(rawInput);
        if (command == null || command.isEmpty()) {
          return;
        }
        observedCommands.add(String.join(" ", command));
        writeResponse(command, rawOutput);
        rawOutput.flush();
      }
    } catch (SocketException ignored) {
      // Client disconnected.
    } catch (IOException ioException) {
      throw new IllegalStateException("Fake Redis client handling failed", ioException);
    }
  }

  private List<String> readCommand(InputStream inputStream) throws IOException {
    int prefix = inputStream.read();
    if (prefix == -1) {
      return null;
    }
    if (prefix != '*') {
      throw new IOException("Unsupported RESP prefix: " + (char) prefix);
    }

    int elementCount = Integer.parseInt(readLine(inputStream));
    List<String> parts = new ArrayList<>(elementCount);
    for (int index = 0; index < elementCount; index++) {
      int bulkPrefix = inputStream.read();
      if (bulkPrefix != '$') {
        throw new IOException("Expected bulk string but got: " + (char) bulkPrefix);
      }
      int length = Integer.parseInt(readLine(inputStream));
      byte[] bytes = inputStream.readNBytes(length);
      if (bytes.length != length) {
        throw new IOException("Unexpected EOF while reading bulk string");
      }
      consumeCrLf(inputStream);
      parts.add(new String(bytes, StandardCharsets.UTF_8));
    }
    return parts;
  }

  private void writeResponse(List<String> command, OutputStream outputStream) throws IOException {
    String verb = command.get(0).toUpperCase();
    switch (verb) {
      case "HELLO" -> writeHello(outputStream);
      case "CLIENT", "PING", "SELECT" -> writeSimpleString(outputStream, verb.equals("PING") ? "PONG" : "OK");
      case "COMMAND" -> writeArrayHeader(outputStream, 0);
      case "INFO" -> writeBulkString(outputStream, "redis_version:7.2.0\r\nrole:master\r\n");
      case "SET" -> handleSet(command, outputStream);
      case "GET" -> handleGet(command, outputStream);
      case "DEL" -> handleDelete(command, outputStream);
      case "PEXPIRE" -> handleExpire(command, outputStream, true);
      case "EXPIRE" -> handleExpire(command, outputStream, false);
      case "PSETEX" -> handleSetWithExpiration(command, outputStream, true);
      case "SETEX" -> handleSetWithExpiration(command, outputStream, false);
      case "QUIT" -> writeSimpleString(outputStream, "OK");
      default -> writeSimpleString(outputStream, "OK");
    }
  }

  private void handleSet(List<String> command, OutputStream outputStream) throws IOException {
    String key = command.get(1);
    String value = command.get(2);
    purgeIfExpired(key);
    boolean nx = command.stream().skip(3).anyMatch("NX"::equalsIgnoreCase);
    if (nx && values.containsKey(key)) {
      writeNullBulkString(outputStream);
      return;
    }
    values.put(key, value);
    applySetExpiration(command, key);
    writeSimpleString(outputStream, "OK");
  }

  private void handleGet(List<String> command, OutputStream outputStream) throws IOException {
    String key = command.get(1);
    purgeIfExpired(key);
    String value = values.get(key);
    if (value == null) {
      writeNullBulkString(outputStream);
      return;
    }
    writeBulkString(outputStream, value);
  }

  private void handleDelete(List<String> command, OutputStream outputStream) throws IOException {
    int removed = 0;
    for (int index = 1; index < command.size(); index++) {
      String key = command.get(index);
      purgeIfExpired(key);
      removed += values.remove(key) != null ? 1 : 0;
      expirations.remove(key);
    }
    writeInteger(outputStream, removed);
  }

  private void handleExpire(List<String> command, OutputStream outputStream, boolean milliseconds) throws IOException {
    String key = command.get(1);
    purgeIfExpired(key);
    if (!values.containsKey(key)) {
      writeInteger(outputStream, 0);
      return;
    }

    long ttl = Long.parseLong(command.get(2));
    long ttlMillis = milliseconds ? ttl : ttl * 1000;
    expirations.put(key, System.currentTimeMillis() + ttlMillis);
    writeInteger(outputStream, 1);
  }

  private void handleSetWithExpiration(List<String> command, OutputStream outputStream, boolean milliseconds) throws IOException {
    String key = command.get(1);
    String value = command.get(2);
    long ttl = Long.parseLong(command.get(3));
    long ttlMillis = milliseconds ? ttl : ttl * 1000;
    values.put(key, value);
    expirations.put(key, System.currentTimeMillis() + ttlMillis);
    writeSimpleString(outputStream, "OK");
  }

  private void applySetExpiration(List<String> command, String key) {
    expirations.remove(key);
    for (int index = 3; index < command.size() - 1; index++) {
      String option = command.get(index);
      if ("PX".equalsIgnoreCase(option)) {
        expirations.put(key, System.currentTimeMillis() + Long.parseLong(command.get(index + 1)));
        return;
      }
      if ("EX".equalsIgnoreCase(option)) {
        expirations.put(key, System.currentTimeMillis() + Long.parseLong(command.get(index + 1)) * 1000);
        return;
      }
    }
  }

  private void purgeIfExpired(String key) {
    Long expiresAt = expirations.get(key);
    if (expiresAt == null) {
      return;
    }
    if (expiresAt <= System.currentTimeMillis()) {
      expirations.remove(key);
      values.remove(key);
    }
  }

  private void writeHello(OutputStream outputStream) throws IOException {
    writeMapHeader(outputStream, 6);
    writeSimpleString(outputStream, "server");
    writeSimpleString(outputStream, "redis");
    writeSimpleString(outputStream, "version");
    writeSimpleString(outputStream, "7.2.0");
    writeSimpleString(outputStream, "proto");
    writeInteger(outputStream, 3);
    writeSimpleString(outputStream, "id");
    writeInteger(outputStream, 1);
    writeSimpleString(outputStream, "mode");
    writeSimpleString(outputStream, "standalone");
    writeSimpleString(outputStream, "role");
    writeSimpleString(outputStream, "master");
  }

  private void writeSimpleString(OutputStream outputStream, String value) throws IOException {
    outputStream.write('+');
    outputStream.write(value.getBytes(StandardCharsets.UTF_8));
    outputStream.write(CRLF);
  }

  private void writeBulkString(OutputStream outputStream, String value) throws IOException {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    outputStream.write('$');
    outputStream.write(Integer.toString(bytes.length).getBytes(StandardCharsets.UTF_8));
    outputStream.write(CRLF);
    outputStream.write(bytes);
    outputStream.write(CRLF);
  }

  private void writeNullBulkString(OutputStream outputStream) throws IOException {
    outputStream.write("$-1\r\n".getBytes(StandardCharsets.UTF_8));
  }

  private void writeInteger(OutputStream outputStream, int value) throws IOException {
    outputStream.write(':');
    outputStream.write(Integer.toString(value).getBytes(StandardCharsets.UTF_8));
    outputStream.write(CRLF);
  }

  private void writeArrayHeader(OutputStream outputStream, int size) throws IOException {
    outputStream.write('*');
    outputStream.write(Integer.toString(size).getBytes(StandardCharsets.UTF_8));
    outputStream.write(CRLF);
  }

  private void writeMapHeader(OutputStream outputStream, int size) throws IOException {
    outputStream.write('%');
    outputStream.write(Integer.toString(size).getBytes(StandardCharsets.UTF_8));
    outputStream.write(CRLF);
  }

  private String readLine(InputStream inputStream) throws IOException {
    StringBuilder builder = new StringBuilder();
    while (true) {
      int current = inputStream.read();
      if (current == -1) {
        throw new IOException("Unexpected EOF while reading RESP line");
      }
      if (current == '\r') {
        int lineFeed = inputStream.read();
        if (lineFeed != '\n') {
          throw new IOException("Malformed RESP line ending");
        }
        return builder.toString();
      }
      builder.append((char) current);
    }
  }

  private void consumeCrLf(InputStream inputStream) throws IOException {
    int carriageReturn = inputStream.read();
    int lineFeed = inputStream.read();
    if (carriageReturn != '\r' || lineFeed != '\n') {
      throw new IOException("Malformed RESP bulk string termination");
    }
  }
}
