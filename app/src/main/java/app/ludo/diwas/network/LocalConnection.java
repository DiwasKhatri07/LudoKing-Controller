package app.ludo.diwas.network;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Local TCP Socket Server for Controller communication.
 */
public class LocalConnection {
    private static final String TAG = "LocalConnection";
    public static final int DEFAULT_PORT = 8080;

    public interface Listener {
        void onClientConnected(String address);
        void onClientDisconnected(String address);
        void onCommandReceived(JSONObject command);
    }

    private ServerSocket serverSocket;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Set<ClientHandler> clients = Collections.synchronizedSet(new HashSet<>());
    private volatile boolean isRunning = false;
    private Listener listener;
    private int port = DEFAULT_PORT;

    public LocalConnection() {}

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public synchronized void start(int port) {
        if (isRunning) return;
        this.port = port;
        this.isRunning = true;

        executor.execute(() -> {
            try {
                serverSocket = new ServerSocket(port);
                Log.d(TAG, "[GAME] LocalConnection server started on port " + port);
                while (isRunning && !serverSocket.isClosed()) {
                    Socket socket = serverSocket.accept();
                    ClientHandler handler = new ClientHandler(socket);
                    clients.add(handler);
                    executor.execute(handler);
                }
            } catch (Exception e) {
                if (isRunning) {
                    Log.e(TAG, "[GAME] Server error: " + e.getMessage());
                }
            }
        });
    }

    public synchronized void stop() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (Exception ignored) {}

        synchronized (clients) {
            for (ClientHandler client : clients) {
                client.close();
            }
            clients.clear();
        }
        Log.d(TAG, "[GAME] LocalConnection server stopped");
    }

    public void broadcastEvent(String eventJson) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                client.send(eventJson);
            }
        }
    }

    public boolean hasConnectedClients() {
        return !clients.isEmpty();
    }

    /** True when the TCP server socket is actually listening. */
    public synchronized boolean isServerRunning() {
        return isRunning && serverSocket != null && !serverSocket.isClosed();
    }

    /** Port the server was last asked to listen on. */
    public synchronized int getBoundPort() {
        return port;
    }

    private class ClientHandler implements Runnable {
        private final Socket socket;
        private PrintWriter writer;
        private BufferedReader reader;
        private final String clientAddress;

        ClientHandler(Socket socket) {
            this.socket = socket;
            this.clientAddress = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        }

        @Override
        public void run() {
            Log.d(TAG, "[CTRL] Controller connected from " + clientAddress);
            if (listener != null) {
                listener.onClientConnected(clientAddress);
            }

            try {
                writer = new PrintWriter(new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)), true);
                reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

                String line;
                while (isRunning && (line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    Log.d(TAG, "[CTRL] Command received: " + line);
                    try {
                        JSONObject json = new JSONObject(line);
                        if (listener != null) {
                            listener.onCommandReceived(json);
                        }
                    } catch (Exception jsonEx) {
                        Log.e(TAG, "[CTRL] Invalid JSON command: " + line);
                    }
                }
            } catch (Exception e) {
                Log.d(TAG, "[CTRL] Client communication closed: " + e.getMessage());
            } finally {
                close();
                clients.remove(this);
                Log.d(TAG, "[CTRL] Controller disconnected: " + clientAddress);
                if (listener != null) {
                    listener.onClientDisconnected(clientAddress);
                }
            }
        }

        void send(String text) {
            executor.execute(() -> {
                try {
                    if (writer != null) {
                        writer.println(text);
                        writer.flush();
                    }
                } catch (Exception ignored) {}
            });
        }

        void close() {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (Exception ignored) {}
        }
    }
}
