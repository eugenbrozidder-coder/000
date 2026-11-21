package com.example.cleanhud;

import com.google.gson.JsonObject;

import java.io.*;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

/** Минимальный Discord Rich Presence через локальный IPC (без сторонних библиотек). */
public final class DiscordRpc {
    public static volatile String details = "NLS", state = "В меню";
    private static volatile String appId = "";
    private static Thread thread;
    private static final long START = System.currentTimeMillis() / 1000;

    private interface Io extends Closeable {
        void write(byte[] b) throws IOException;
        void read(byte[] b) throws IOException;
    }

    public static synchronized void start(String id) {
        appId = id == null ? "" : id.trim();
        if (appId.isEmpty() || thread != null) return;
        thread = new Thread(DiscordRpc::run, "NLS-RPC");
        thread.setDaemon(true);
        thread.start();
    }

    private static void run() {
        while (true) {
            String cid = appId;
            try (Io io = open()) {
                JsonObject hs = new JsonObject();
                hs.addProperty("v", 1);
                hs.addProperty("client_id", cid);
                send(io, 0, hs.toString());
                recv(io);
                while (cid.equals(appId)) {
                    send(io, 1, activity().toString());
                    recv(io);
                    Thread.sleep(15000);
                }
            } catch (Exception e) {
                try { Thread.sleep(15000); } catch (InterruptedException ie) { return; }
            }
        }
    }

    private static JsonObject activity() {
        JsonObject act = new JsonObject();
        act.addProperty("details", pad(details));
        act.addProperty("state", pad(state));
        JsonObject ts = new JsonObject();
        ts.addProperty("start", START);
        act.add("timestamps", ts);
        JsonObject args = new JsonObject();
        args.addProperty("pid", ProcessHandle.current().pid());
        args.add("activity", act);
        JsonObject root = new JsonObject();
        root.addProperty("cmd", "SET_ACTIVITY");
        root.add("args", args);
        root.addProperty("nonce", UUID.randomUUID().toString());
        return root;
    }

    private static String pad(String s) { return s == null || s.length() < 2 ? "NLS" : s; }

    private static void send(Io io, int op, String json) throws IOException {
        byte[] p = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer b = ByteBuffer.allocate(8 + p.length).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(op).putInt(p.length).put(p);
        io.write(b.array());
    }

    private static void recv(Io io) throws IOException {
        byte[] h = new byte[8];
        io.read(h);
        io.read(new byte[ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN).getInt(4)]);
    }

    private static Io open() throws IOException {
        boolean win = System.getProperty("os.name").toLowerCase().contains("win");
        String[] bases = {System.getenv("XDG_RUNTIME_DIR"), System.getenv("TMPDIR"), System.getenv("TMP"), System.getenv("TEMP"), "/tmp"};
        for (int i = 0; i < 10; i++) {
            if (win) {
                try {
                    RandomAccessFile f = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw");
                    return new Io() {
                        public void write(byte[] b) throws IOException { f.write(b); }
                        public void read(byte[] b) throws IOException { f.readFully(b); }
                        public void close() throws IOException { f.close(); }
                    };
                } catch (IOException ignored) {}
            } else {
                for (String base : bases) {
                    if (base == null) continue;
                    try {
                        SocketChannel ch = SocketChannel.open(UnixDomainSocketAddress.of(Path.of(base, "discord-ipc-" + i)));
                        return new Io() {
                            public void write(byte[] b) throws IOException {
                                ByteBuffer bb = ByteBuffer.wrap(b);
                                while (bb.hasRemaining()) ch.write(bb);
                            }
                            public void read(byte[] b) throws IOException {
                                ByteBuffer bb = ByteBuffer.wrap(b);
                                while (bb.hasRemaining()) if (ch.read(bb) < 0) throw new EOFException();
                            }
                            public void close() throws IOException { ch.close(); }
                        };
                    } catch (IOException ignored) {}
                }
            }
        }
        throw new IOException("Discord не найден");
    }
}
