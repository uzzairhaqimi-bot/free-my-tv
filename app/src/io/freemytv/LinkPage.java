package io.freemytv;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Fork extra: a one-time page on the TV's own network where a phone pastes music and video links for the home screen
 * and the games (pasting a link on a phone is easy; typing one with a remote isn't). Like KeyPage: the TV's private
 * address, a random port and path, a few minutes, small bodies only, and nothing escapes its thread.
 */
final class LinkPage {
    interface Listener {
        /** On the page's thread; empty strings for fields left blank. */
        void links(String music, String video, String game);
    }

    static final long OPEN_FOR = 10 * 60_000L;
    private static final int MAX = 16384;

    final String url;
    private final String path;
    private final ServerSocket server;
    private final Listener listener;
    private final long until = System.currentTimeMillis() + OPEN_FOR;
    private volatile boolean closed;

    LinkPage(InetAddress lan, Listener listener) throws IOException {
        this.listener = listener;
        byte[] random = new byte[12];
        new SecureRandom().nextBytes(random);
        path = "/" + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        server = new ServerSocket(0, 4, lan);
        url = "http://" + lan.getHostAddress() + ":" + server.getLocalPort() + path;
        Thread t = new Thread(this::serve, "LinkPage");
        t.setDaemon(true);
        t.start();
    }

    void close() {
        closed = true;
        try { server.close(); } catch (IOException ignored) { }
    }

    private void serve() {
        try {
            while (!closed && System.currentTimeMillis() < until) {
                server.setSoTimeout(1000);
                Socket s;
                try { s = server.accept(); } catch (java.net.SocketTimeoutException e) { continue; }
                try {
                    s.setSoTimeout(10_000);
                    answer(s.getInputStream(), s.getOutputStream());
                } catch (Throwable ignored) {
                    // a phone that went away
                } finally {
                    try { s.close(); } catch (IOException ignored) { }
                }
            }
        } catch (Throwable t) {
            // the page closed
        } finally {
            close();
        }
    }

    private void answer(InputStream in, OutputStream out) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int c, n = 0;
        while ((c = in.read()) >= 0 && n++ < 8192) {
            head.write(c);
            byte[] b = head.toByteArray();
            int l = b.length;
            if (l >= 4 && b[l - 4] == '\r' && b[l - 3] == '\n' && b[l - 2] == '\r' && b[l - 1] == '\n') break;
        }
        String h = head.toString("ISO-8859-1");
        String first = h.split("\r\n", 2)[0];
        String[] parts = first.split(" ");
        if (parts.length < 2 || !parts[1].equals(path)) { respond(out, "404 Not Found", "text/plain", "Tiada".getBytes(StandardCharsets.UTF_8)); return; }
        if (parts[0].equals("GET")) { respond(out, "200 OK", "text/html; charset=utf-8", page(false)); return; }
        if (!parts[0].equals("POST")) { respond(out, "405 Method Not Allowed", "text/plain", new byte[0]); return; }
        int len = 0;
        for (String line : h.split("\r\n")) if (line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:")) {
            try { len = Integer.parseInt(line.substring(15).trim()); } catch (NumberFormatException ignored) { }
        }
        if (len <= 0 || len > MAX) { respond(out, "413 Payload Too Large", "text/plain", new byte[0]); return; }
        byte[] body = new byte[len];
        int got = 0;
        while (got < len) { int r = in.read(body, got, len - got); if (r < 0) break; got += r; }
        String form = new String(body, 0, got, StandardCharsets.ISO_8859_1);
        listener.links(field(form, "music"), field(form, "video"), field(form, "game"));
        respond(out, "200 OK", "text/html; charset=utf-8", page(true));
    }

    private static String field(String form, String name) {
        for (String pair : form.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                try { return URLDecoder.decode(pair.substring(eq + 1), "UTF-8").trim(); } catch (Exception e) { return ""; }
            }
        }
        return "";
    }

    private static void respond(OutputStream out, String status, String type, byte[] body) throws IOException {
        String head = "HTTP/1.1 " + status + "\r\nContent-Type: " + type + "\r\nContent-Length: " + body.length
                + "\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }

    private byte[] page(boolean done) {
        String css = "body{font-family:sans-serif;background:#0f172a;color:#f8fafc;margin:0;padding:20px}h1{font-size:22px}"
                + "label{display:block;margin:18px 0 6px;font-weight:bold}textarea{width:100%;box-sizing:border-box;min-height:70px;"
                + "font-size:16px;border-radius:10px;border:0;padding:10px}button{margin-top:22px;width:100%;padding:16px;font-size:18px;"
                + "border:0;border-radius:12px;background:#22c55e;color:#fff;font-weight:bold}p{color:#cbd5e1;font-size:14px}";
        String html = done
                ? "<!doctype html><meta name=viewport content='width=device-width,initial-scale=1'><style>" + css + "</style>"
                + "<h1>✅ Dihantar ke TV!</h1><p>Lihat skrin TV. Boleh hantar lagi dengan butang Kembali.</p>"
                : "<!doctype html><meta name=viewport content='width=device-width,initial-scale=1'><style>" + css + "</style>"
                + "<h1>🎵 Hantar pautan ke TV</h1>"
                + "<p>Dalam YouTube: tekan <b>Kongsi / Share</b> → <b>Salin pautan / Copy link</b>, kemudian tekan lama kotak di bawah → <b>Tampal / Paste</b>. "
                + "Lebih dari satu pautan: satu setiap baris. Biarkan kosong untuk tidak tukar.</p>"
                + "<form method=post><label>Lagu skrin utama</label><textarea name=music placeholder='https://youtu.be/…'></textarea>"
                + "<label>Video latar skrin utama</label><textarea name=video placeholder='https://youtu.be/…'></textarea>"
                + "<label>Lagu dalam permainan</label><textarea name=game placeholder='https://youtu.be/…'></textarea>"
                + "<button>Hantar ke TV</button></form>";
        return html.getBytes(StandardCharsets.UTF_8);
    }
}
