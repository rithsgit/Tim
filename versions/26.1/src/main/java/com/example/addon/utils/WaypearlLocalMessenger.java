package com.example.addon.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A single-peer, loopback-only transport. Minecraft state must be accessed by
 * the listener on the client thread, never by these network threads.
 */
public final class WaypearlLocalMessenger implements AutoCloseable {
    public enum MessageType { MESSAGE, PULL, ARM, CANCEL }

    public interface Listener {
        boolean allowed(String identity);
        void status(String message);
        void received(String identity, long connectionId, MessageType type, String text);
    }

    private static final int PORT = 27841;
    private static final int MAX_FRAME = 4096;
    private static final int PASSWORD_ITERATIONS = 210_000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Gson GSON = new Gson();
    private final Path credentialsPath;
    private final Listener listener;
    private volatile boolean closed;
    private volatile SSLServerSocket server;
    private volatile SSLSocket socket;
    private volatile String peer = "";
    private volatile String fingerprint = "";
    private volatile Sender sender;
    private volatile long connectionId;

    private record Outgoing(MessageType type, String text) {}

    private final class Sender {
        private final SSLSocket connection;
        private final ArrayBlockingQueue<Outgoing> queue = new ArrayBlockingQueue<>(32);

        private Sender(SSLSocket connection) {
            this.connection = connection;
        }

        private void start() {
            Thread.ofVirtual().name("Waypearl-writer").start(() -> {
                try {
                    DataOutputStream out = new DataOutputStream(connection.getOutputStream());
                    while (!connection.isClosed()) {
                        Outgoing message = queue.take();
                        writeFrame(out, message.type().name(), message.text());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (IOException e) {
                    if (!closed && sender == this) listener.status("Send error: " + safeError(e));
                    try {
                        connection.close();
                    } catch (IOException closeError) {
                        listener.status("Failed to close connection: " + safeError(closeError));
                    }
                }
            });
        }
    }

    private static final class Credential {
        String salt;
        String hash;
    }

    public WaypearlLocalMessenger(Path credentialsPath, Listener listener) {
        this.credentialsPath = credentialsPath;
        this.listener = listener;
    }

    public static boolean validName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{1,16}");
    }

    public static boolean validPassword(String password) {
        return password != null && password.length() >= 16 && password.length() <= 128;
    }

    public String fingerprint() { return fingerprint; }
    public String peer() { return peer; }
    public long connectionId() { return connectionId; }

    public synchronized void enroll(String name, String password) throws Exception {
        if (!validName(name) || !validPassword(password)) throw new IllegalArgumentException("Use a valid player name and a password of 16-128 characters.");
        Map<String, Credential> credentials = readCredentials();
        String identity = name.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Credential> entry : credentials.entrySet()) {
            if (entry.getKey().equals(identity)) continue;
            Credential existing = entry.getValue();
            if (existing == null || existing.salt == null || existing.hash == null) {
                throw new IOException("Credential file contains an invalid entry.");
            }
            byte[] existingSalt = Base64.getDecoder().decode(existing.salt);
            byte[] existingHash = Base64.getDecoder().decode(existing.hash);
            if (MessageDigest.isEqual(existingHash, hashPassword(password, existingSalt))) {
                throw new IllegalArgumentException("Each requester must have a different password.");
            }
        }
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        Credential credential = new Credential();
        credential.salt = Base64.getEncoder().encodeToString(salt);
        credential.hash = Base64.getEncoder().encodeToString(hashPassword(password, salt));
        credentials.put(identity, credential);
        Files.createDirectories(credentialsPath.getParent());
        Path temp = Files.createTempFile(credentialsPath.getParent(), "waypearl-credentials-", ".tmp");
        try {
            Files.writeString(temp, GSON.toJson(credentials), StandardCharsets.UTF_8);
            try {
                Files.move(temp, credentialsPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, credentialsPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private Map<String, Credential> readCredentials() throws IOException {
        if (!Files.exists(credentialsPath)) return new HashMap<>();
        try (var reader = Files.newBufferedReader(credentialsPath, StandardCharsets.UTF_8)) {
            Map<String, Credential> data = GSON.fromJson(reader, new TypeToken<Map<String, Credential>>() {}.getType());
            if (data == null) throw new IOException("Credential file is empty.");
            return data;
        } catch (com.google.gson.JsonParseException e) {
            throw new IOException("Credential file is invalid.", e);
        }
    }

    private synchronized boolean authenticate(String name, String password) throws Exception {
        if (!validName(name) || !validPassword(password)) return false;
        Credential credential = readCredentials().get(name.toLowerCase(Locale.ROOT));
        if (credential == null || credential.salt == null || credential.hash == null) return false;
        byte[] salt = Base64.getDecoder().decode(credential.salt);
        byte[] expected = Base64.getDecoder().decode(credential.hash);
        return MessageDigest.isEqual(expected, hashPassword(password, salt));
    }

    private static byte[] hashPassword(String password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PASSWORD_ITERATIONS, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    public void startHost() {
        Thread.ofVirtual().name("Waypearl-host").start(() -> {
            try {
                SSLContext context = createHostContext();
                if (closed) return;
                SSLServerSocket host = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
                synchronized (this) {
                    if (closed) {
                        host.close();
                        return;
                    }
                    server = host;
                }
                host.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT), 4);
                listener.status("Listening on 127.0.0.1:" + PORT);
                while (!closed) {
                    SSLSocket accepted = (SSLSocket) host.accept();
                    synchronized (this) {
                        if (closed) {
                            accepted.close();
                            return;
                        }
                        socket = accepted;
                    }
                    try {
                        accepted.setSoTimeout(5000);
                        accepted.startHandshake();
                        DataInputStream in = new DataInputStream(accepted.getInputStream());
                        DataOutputStream out = new DataOutputStream(accepted.getOutputStream());
                        DataInputStream auth = readFrame(in);
                        String name = auth.readUTF();
                        String password = auth.readUTF();
                        if (auth.available() != 0 || !listener.allowed(name) || !authenticate(name, password)) {
                            writeFrame(out, "DENIED", "");
                            listener.status("Requester authentication failed.");
                            Thread.sleep(1000);
                            continue;
                        }
                        writeFrame(out, "OK", "");
                        connectionId++;
                        peer = name;
                        sender = new Sender(accepted);
                        sender.start();
                        accepted.setSoTimeout(0);
                        listener.status("Connected: " + name);
                        readMessages(in, name, connectionId, true);
                    } catch (Exception e) {
                        if (!closed && !(e instanceof EOFException)) listener.status("Connection error: " + safeError(e));
                    } finally {
                        peer = "";
                        Sender oldSender = sender;
                        sender = null;
                        socket = null;
                        accepted.close();
                        if (oldSender != null) oldSender.queue.offer(new Outgoing(MessageType.MESSAGE, ""));
                        if (!closed) listener.status("Listening on 127.0.0.1:" + PORT);
                    }
                }
            } catch (Exception e) {
                if (!closed) listener.status("Host error: " + safeError(e));
                close();
            }
        });
    }

    public void connect(String name, String password, String pinnedFingerprint) {
        Thread.ofVirtual().name("Waypearl-connect").start(() -> {
            try {
                if (!validName(name) || !validPassword(password)) throw new IllegalArgumentException("Invalid name or password.");
                String pin = pinnedFingerprint.replace(":", "").replace(" ", "").toLowerCase(Locale.ROOT);
                if (!pin.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Enter the assistant's SHA-256 fingerprint.");
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(null, new TrustManager[] {new PinnedTrustManager(pin)}, RANDOM);
                SSLSocket client = (SSLSocket) context.getSocketFactory().createSocket();
                synchronized (this) {
                    if (closed) {
                        client.close();
                        return;
                    }
                    socket = client;
                }
                client.connect(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT), 5000);
                client.setSoTimeout(5000);
                client.startHandshake();
                DataOutputStream out = new DataOutputStream(client.getOutputStream());
                DataInputStream in = new DataInputStream(client.getInputStream());
                writeFrame(out, name, password);
                DataInputStream reply = readFrame(in);
                if (!reply.readUTF().equals("OK") || !reply.readUTF().isEmpty() || reply.available() != 0) {
                    throw new IOException("Authentication rejected.");
                }
                connectionId++;
                peer = name;
                sender = new Sender(client);
                sender.start();
                client.setSoTimeout(0);
                listener.status("Connected to assistant.");
                readMessages(in, name, connectionId, false);
            } catch (Exception e) {
                if (!closed) listener.status("Connection error: " + safeError(e));
            } finally {
                close();
            }
        });
    }

    public void send(boolean pull, String text) throws IOException {
        send(pull ? MessageType.PULL : MessageType.MESSAGE, text);
    }

    public void send(MessageType type, String text) throws IOException {
        if (type == null) throw new IOException("Message type is required.");
        if (text == null || text.isBlank() || text.length() > 512 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            throw new IOException("Message must be 1-512 characters on one line.");
        }
        Sender active = sender;
        if (closed || active == null || peer.isEmpty() || active.connection.isClosed()) throw new IOException("Messenger is not connected.");
        if (!active.queue.offer(new Outgoing(type, text))) throw new IOException("Messenger send queue is full.");
    }

    private void readMessages(DataInputStream in, String name, long sessionId, boolean checkWhitelist) throws IOException {
        long windowStart = System.nanoTime();
        int count = 0;
        while (!closed) {
            DataInputStream frame = readFrame(in);
            String type = frame.readUTF();
            String text = frame.readUTF();
            if (frame.available() != 0 || (!type.equals("PULL") && !type.equals("MESSAGE")
                && !type.equals("ARM") && !type.equals("CANCEL"))
                || text.isBlank() || text.length() > 512 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
                throw new IOException("Invalid messenger frame.");
            }
            long now = System.nanoTime();
            if (now - windowStart >= TimeUnit.SECONDS.toNanos(1)) {
                windowStart = now;
                count = 0;
            }
            if (++count > 20) throw new IOException("Messenger rate limit exceeded.");
            if (checkWhitelist && !listener.allowed(name)) throw new IOException("Requester is no longer whitelisted.");
            listener.received(name, sessionId, MessageType.valueOf(type), text);
        }
    }

    private static DataInputStream readFrame(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length <= 0 || length > MAX_FRAME) throw new IOException("Invalid messenger frame size.");
        byte[] payload = new byte[length];
        in.readFully(payload);
        return new DataInputStream(new ByteArrayInputStream(payload));
    }

    private static void writeFrame(DataOutputStream out, String first, String second) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream frame = new DataOutputStream(bytes);
        frame.writeUTF(first);
        frame.writeUTF(second);
        frame.flush();
        if (bytes.size() > MAX_FRAME) throw new IOException("Messenger frame too large.");
        out.writeInt(bytes.size());
        bytes.writeTo(out);
        out.flush();
    }

    private SSLContext createHostContext() throws Exception {
        Path tempDir = Files.createTempDirectory("waypearl-tls-");
        Path keyStorePath = tempDir.resolve("host.p12");
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String password = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        try {
            Path keytool = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool");
            ProcessBuilder builder = new ProcessBuilder(keytool.toString(), "-genkeypair", "-alias", "waypearl",
                "-keyalg", "EC", "-groupname", "secp256r1", "-validity", "30", "-dname", "CN=Waypearl",
                "-storetype", "PKCS12", "-keystore", keyStorePath.toString(), "-storepass:env", "WAYPEARL_KEY_PASSWORD",
                "-keypass:env", "WAYPEARL_KEY_PASSWORD", "-noprompt");
            builder.environment().put("WAYPEARL_KEY_PASSWORD", password);
            builder.redirectErrorStream(true);
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            Process process = builder.start();
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor();
                throw new IOException("Certificate generation timed out.");
            }
            if (process.exitValue() != 0) throw new IOException("Certificate generation failed; a JDK with keytool is required.");
            KeyStore store = KeyStore.getInstance("PKCS12");
            try (var input = Files.newInputStream(keyStorePath)) {
                store.load(input, password.toCharArray());
            }
            fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(store.getCertificate("waypearl").getEncoded()));
            KeyManagerFactory managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            managers.init(store, password.toCharArray());
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(managers.getKeyManagers(), null, RANDOM);
            return context;
        } finally {
            Files.deleteIfExists(keyStorePath);
            Files.deleteIfExists(tempDir);
        }
    }

    private static final class PinnedTrustManager implements X509TrustManager {
        private final byte[] pin;
        private PinnedTrustManager(String pin) { this.pin = HexFormat.of().parseHex(pin); }
        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            throw new CertificateException("Client certificates are not supported.");
        }
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            try {
                if (chain == null || chain.length != 1) throw new CertificateException("Invalid certificate chain.");
                chain[0].checkValidity();
                if (!MessageDigest.isEqual(pin, MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded()))) {
                    throw new CertificateException("Certificate fingerprint mismatch.");
                }
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new CertificateException(e);
            }
        }
    }

    private static String safeError(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    @Override public synchronized void close() {
        closed = true;
        peer = "";
        Sender oldSender = sender;
        sender = null;
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            listener.status("Failed to close connection: " + safeError(e));
        }
        if (oldSender != null) oldSender.queue.offer(new Outgoing(MessageType.MESSAGE, ""));
        try {
            if (server != null) server.close();
        } catch (IOException e) {
            listener.status("Failed to close listener: " + safeError(e));
        }
    }
}
