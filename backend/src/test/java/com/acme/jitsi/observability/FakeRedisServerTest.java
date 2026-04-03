package com.acme.jitsi.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FakeRedisServerTest {

  @Test
  void supportsBasicRedisCommandsAndTracksObservedCommands() throws Exception {
    try (FakeRedisServer server = FakeRedisServer.start();
         Socket socket = new Socket("127.0.0.1", server.getPort());
         InputStream inputStream = new BufferedInputStream(socket.getInputStream());
         OutputStream outputStream = new BufferedOutputStream(socket.getOutputStream())) {
      socket.setSoTimeout(2000);

      sendCommand(outputStream, "PING");
      assertThat(readSimpleString(inputStream)).isEqualTo("PONG");

      sendCommand(outputStream, "SET", "alpha", "one");
      assertThat(readSimpleString(inputStream)).isEqualTo("OK");

      sendCommand(outputStream, "GET", "alpha");
      assertThat(readBulkString(inputStream)).isEqualTo("one");

      sendCommand(outputStream, "DEL", "alpha");
      assertThat(readInteger(inputStream)).isEqualTo(1);

      sendCommand(outputStream, "GET", "alpha");
      assertThat(readBulkString(inputStream)).isNull();

      sendCommand(outputStream, "QUIT");
      assertThat(readSimpleString(inputStream)).isEqualTo("OK");

      assertThat(server.observedCommands())
          .containsExactly("PING", "SET alpha one", "GET alpha", "DEL alpha", "GET alpha", "QUIT");
    }
  }

  @Test
  void expiresKeysForPxAndPexpireCommands() throws Exception {
    try (FakeRedisServer server = FakeRedisServer.start();
         Socket socket = new Socket("127.0.0.1", server.getPort());
         InputStream inputStream = new BufferedInputStream(socket.getInputStream());
         OutputStream outputStream = new BufferedOutputStream(socket.getOutputStream())) {
      socket.setSoTimeout(2000);

      sendCommand(outputStream, "SET", "alpha", "one", "PX", "25");
      assertThat(readSimpleString(inputStream)).isEqualTo("OK");
      assertEventuallyMissing(outputStream, inputStream, "alpha");

      sendCommand(outputStream, "SET", "beta", "two");
      assertThat(readSimpleString(inputStream)).isEqualTo("OK");

      sendCommand(outputStream, "PEXPIRE", "beta", "25");
      assertThat(readInteger(inputStream)).isEqualTo(1);

      assertEventuallyMissing(outputStream, inputStream, "beta");
    }
  }

  private void assertEventuallyMissing(OutputStream outputStream, InputStream inputStream, String key)
      throws Exception {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(2));
    while (Instant.now().isBefore(deadline)) {
      sendCommand(outputStream, "GET", key);
      if (readBulkString(inputStream) == null) {
        return;
      }
      Thread.sleep(20);
    }
    fail("Expected key '%s' to expire before timeout".formatted(key));
  }

  private void sendCommand(OutputStream outputStream, String... command) throws IOException {
    outputStream.write(('*'));
    outputStream.write(Integer.toString(command.length).getBytes(StandardCharsets.UTF_8));
    outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
    for (String part : command) {
      byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
      outputStream.write('$');
      outputStream.write(Integer.toString(bytes.length).getBytes(StandardCharsets.UTF_8));
      outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
      outputStream.write(bytes);
      outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }
    outputStream.flush();
  }

  private String readSimpleString(InputStream inputStream) throws IOException {
    int prefix = inputStream.read();
    assertThat((char) prefix).isEqualTo('+');
    return readLine(inputStream);
  }

  private String readBulkString(InputStream inputStream) throws IOException {
    int prefix = inputStream.read();
    assertThat((char) prefix).isEqualTo('$');
    int length = Integer.parseInt(readLine(inputStream));
    if (length == -1) {
      return null;
    }
    byte[] bytes = inputStream.readNBytes(length);
    consumeCrLf(inputStream);
    return new String(bytes, StandardCharsets.UTF_8);
  }

  private int readInteger(InputStream inputStream) throws IOException {
    int prefix = inputStream.read();
    assertThat((char) prefix).isEqualTo(':');
    return Integer.parseInt(readLine(inputStream));
  }

  private String readLine(InputStream inputStream) throws IOException {
    StringBuilder builder = new StringBuilder();
    while (true) {
      int current = inputStream.read();
      if (current == -1) {
        throw new IOException("Unexpected EOF while reading line");
      }
      if (current == '\r') {
        int lineFeed = inputStream.read();
        if (lineFeed != '\n') {
          throw new IOException("Malformed line ending");
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