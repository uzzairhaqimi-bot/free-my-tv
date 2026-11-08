package io.freemytv;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fork extra: phones as game controllers for Retro Arked. While the games are open, a small server on the TV's own
 * network address serves a controller page and a WebSocket. The QR code carries a random token, so only phones that
 * scanned this screen can join. Up to 4 players: the TV remote is always player 1, phones take the next seats.
 * Nothing escapes its threads (:home's watchdog counts crashes there).
 */
final class Party {
    static final int MAX = 4;
    static final int[] COLORS = {0xFF22D3EE, 0xFFFF4FD8, 0xFFFACC15, 0xFF4ADE80};
    static final String[] COLOR_NAMES = {"Biru", "Merah jambu", "Kuning", "Hijau"};

    /** One seat. Inputs are written by the network threads and read by the game loop. */
    static final class Player {
        final int seat;
        volatile float x, y;          // -1..1
        volatile boolean a, b;
        volatile boolean joined, phone;
        volatile int aPresses, bPresses;   // counted so a quick tap between frames is never lost
        Socket sock;

        Player(int seat) { this.seat = seat; }
        int color() { return COLORS[seat]; }
    }

    final Player[] players = new Player[MAX];
    String url;
    private ServerSocket server;
    private final String token;
    private volatile boolean running;

    Party() {
        for (int i = 0; i < MAX; i++) players[i] = new Player(i);
        players[0].joined = true;   // the TV remote / first gamepad
        byte[] t = new byte[6];
        new SecureRandom().nextBytes(t);
        StringBuilder s = new StringBuilder();
        for (byte b : t) s.append(String.format(Locale.US, "%02x", b));
        token = s.toString();
    }

    int count() {
        int n = 0;
        for (Player p : players) if (p.joined) n++;
        return n;
    }

    void start(Context c) {
        if (running) return;
        try {
            InetAddress lan = KeyPage.lanAddress(c);
            if (lan == null) return;
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(lan, 0));
            url = "http://" + lan.getHostAddress() + ":" + server.getLocalPort() + "/" + token;
            running = true;
            Thread t = new Thread(this::accept, "party");
            t.setDaemon(true);
            t.start();
        } catch (Throwable e) {
            AppLog.w("party: " + e);
            url = null;
        }
    }

    void stop() {
        running = false;
        try { if (server != null) server.close(); } catch (Throwable ignored) { }
        for (Player p : players) {
            try { if (p.sock != null) p.sock.close(); } catch (Throwable ignored) { }
            if (p.phone) { p.joined = false; p.phone = false; }
        }
    }

    /** A short buzz on that player's phone (hit, goal, lost). */
    void buzz(int seat, int ms) {
        Player p = players[seat];
        if (!p.phone || p.sock == null) return;
        send(p, "{\"t\":\"buzz\",\"ms\":" + ms + "}");
    }

    private void accept() {
        while (running) {
            try {
                final Socket s = server.accept();
                Thread t = new Thread(() -> handle(s), "party-client");
                t.setDaemon(true);
                t.start();
            } catch (Throwable e) {
                if (running) AppLog.w("party: " + e);
                return;
            }
        }
    }

    private void handle(Socket s) {
        try {
            s.setTcpNoDelay(true);
            s.setSoTimeout(15000);
            InputStream in = new BufferedInputStream(s.getInputStream());
            String head = readHead(in);
            String[] first = head.split("\r\n")[0].split(" ");
            String path = first.length > 1 ? first[1] : "";
            if (!path.startsWith("/" + token)) {
                respond(s.getOutputStream(), "404 Not Found", "text/plain", "Not found".getBytes("UTF-8"));
                s.close();
                return;
            }
            String key = header(head, "sec-websocket-key");
            if (key == null) {
                respond(s.getOutputStream(), "200 OK", "text/html; charset=utf-8", page().getBytes("UTF-8"));
                s.close();
                return;
            }
            Player p = seat();
            OutputStream out = s.getOutputStream();
            String accept = android.util.Base64.encodeToString(MessageDigest.getInstance("SHA-1")
                    .digest((key.trim() + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes("UTF-8")), android.util.Base64.NO_WRAP);
            out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes("UTF-8"));
            out.flush();
            if (p == null) {
                sendRaw(out, "{\"t\":\"full\"}");
                s.close();
                return;
            }
            s.setSoTimeout(0);
            synchronized (p) {
                p.sock = s;
                p.phone = true;
                p.x = p.y = 0;
                p.a = p.b = false;
                p.joined = true;
            }
            send(p, "{\"t\":\"you\",\"seat\":" + (p.seat + 1) + ",\"color\":\"" + String.format("#%06X", p.color() & 0xFFFFFF)
                    + "\",\"name\":\"" + COLOR_NAMES[p.seat] + "\"}");
            readFrames(in, p);
        } catch (Throwable e) {
            // a phone that went away
        } finally {
            for (Player p : players) {
                if (p.sock == s) {
                    synchronized (p) {
                        p.sock = null;
                        p.phone = false;
                        p.joined = false;
                        p.x = p.y = 0;
                        p.a = p.b = false;
                    }
                }
            }
            try { s.close(); } catch (Throwable ignored) { }
        }
    }

    private synchronized Player seat() {
        for (int i = 1; i < MAX; i++) if (!players[i].joined) { players[i].joined = true; return players[i]; }
        return null;
    }

    private void readFrames(InputStream in, Player p) throws IOException {
        while (running) {
            int b0 = in.read(), b1 = in.read();
            if (b0 < 0 || b1 < 0) return;
            int op = b0 & 0x0F;
            long len = b1 & 0x7F;
            if (len == 126) len = (in.read() << 8) | in.read();
            else if (len == 127) { len = 0; for (int i = 0; i < 8; i++) len = (len << 8) | in.read(); }
            if (len > 4096) return;
            byte[] mask = new byte[4];
            if ((b1 & 0x80) != 0) for (int i = 0; i < 4; i++) mask[i] = (byte) in.read();
            byte[] data = new byte[(int) len];
            int got = 0;
            while (got < len) { int n = in.read(data, got, (int) len - got); if (n < 0) return; got += n; }
            for (int i = 0; i < len; i++) data[i] ^= mask[i % 4];
            if (op == 8) return;                              // close
            if (op == 9) continue;                            // ping (the page never sends one)
            if (op != 1) continue;
            try {
                JSONObject o = new JSONObject(new String(data, "UTF-8"));
                if (!"in".equals(o.optString("t"))) continue;
                p.x = (float) Math.max(-1, Math.min(1, o.optDouble("x", 0)));
                p.y = (float) Math.max(-1, Math.min(1, o.optDouble("y", 0)));
                boolean a = o.optInt("a", 0) == 1, bb = o.optInt("b", 0) == 1;
                if (a && !p.a) p.aPresses++;
                if (bb && !p.b) p.bPresses++;
                p.a = a;
                p.b = bb;
            } catch (Throwable ignored) { }
        }
    }

    private void send(Player p, String text) {
        Socket s = p.sock;
        if (s == null) return;
        try {
            synchronized (s) { sendRaw(s.getOutputStream(), text); }
        } catch (Throwable ignored) { }
    }

    private static void sendRaw(OutputStream out, String text) throws IOException {
        byte[] d = text.getBytes("UTF-8");
        out.write(0x81);
        if (d.length < 126) out.write(d.length);
        else { out.write(126); out.write(d.length >> 8); out.write(d.length & 0xFF); }
        out.write(d);
        out.flush();
    }

    private static String readHead(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int m = 0, c;
        while ((c = in.read()) >= 0) {
            b.write(c);
            m = (c == '\r' || c == '\n') ? m + 1 : 0;
            if (m == 4 || b.size() > 8192) break;
        }
        return b.toString("UTF-8");
    }

    private static String header(String head, String name) {
        for (String line : head.split("\r\n")) {
            int i = line.indexOf(':');
            if (i > 0 && line.substring(0, i).trim().equalsIgnoreCase(name)) return line.substring(i + 1).trim();
        }
        return null;
    }

    private static void respond(OutputStream out, String status, String type, byte[] body) throws IOException {
        out.write(("HTTP/1.1 " + status + "\r\nContent-Type: " + type + "\r\nContent-Length: " + body.length
                + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
        out.write(body);
        out.flush();
    }

    /** The controller: a stick on the left, A and B on the right; sends only when something changes. */
    private static String page() {
        return "<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1,user-scalable=no'>"
                + "<title>Retro Arked</title><style>"
                + "html,body{margin:0;height:100%;background:#0b0b2b;color:#fff;font:16px system-ui,sans-serif;overflow:hidden;touch-action:none;-webkit-user-select:none;user-select:none}"
                + "#top{position:fixed;top:0;left:0;right:0;text-align:center;padding:10px;font-weight:600}"
                + "#pad{position:fixed;left:6vw;top:50%;width:42vmin;height:42vmin;margin-top:-21vmin;border-radius:50%;background:rgba(255,255,255,.08);border:2px solid rgba(255,255,255,.18)}"
                + "#knob{position:absolute;left:50%;top:50%;width:16vmin;height:16vmin;margin:-8vmin;border-radius:50%;background:var(--c,#22d3ee);box-shadow:0 0 24px var(--c,#22d3ee)}"
                + ".btn{position:fixed;width:22vmin;height:22vmin;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:7vmin;font-weight:800;border:2px solid rgba(255,255,255,.25)}"
                + "#a{right:6vw;top:50%;margin-top:-4vmin;background:#ff4fd8;box-shadow:0 0 22px #ff4fd8}"
                + "#b{right:calc(6vw + 25vmin);top:50%;margin-top:-20vmin;background:#8b5cf6;box-shadow:0 0 22px #8b5cf6}"
                + ".on{transform:scale(.92);filter:brightness(1.4)}#rot{display:none;position:fixed;inset:0;background:#0b0b2b;align-items:center;justify-content:center;text-align:center;padding:20px}"
                + "@media (orientation:portrait){#rot{display:flex}}</style></head><body>"
                + "<div id='top'>Menyambung…</div><div id='pad'><div id='knob'></div></div><div class='btn' id='b'>B</div><div class='btn' id='a'>A</div>"
                + "<div id='rot'>Pusingkan telefon ke mod melintang 📱↻</div><script>"
                + "var ws=new WebSocket('ws://'+location.host+location.pathname),st={t:'in',x:0,y:0,a:0,b:0},last='';"
                + "function send(){var s=JSON.stringify(st);if(s!==last&&ws.readyState===1){ws.send(s);last=s;}}"
                + "ws.onmessage=function(e){var m=JSON.parse(e.data);if(m.t==='you'){document.getElementById('top').textContent='Pemain '+m.seat+' · '+m.name;"
                + "document.documentElement.style.setProperty('--c',m.color);document.getElementById('top').style.color=m.color;}"
                + "if(m.t==='full')document.getElementById('top').textContent='Penuh: 4 pemain sudah masuk';"
                + "if(m.t==='buzz'&&navigator.vibrate)navigator.vibrate(m.ms||40);};"
                + "ws.onclose=function(){document.getElementById('top').textContent='Terputus. Muat semula halaman.';};"
                + "var pad=document.getElementById('pad'),knob=document.getElementById('knob'),pid=null;"
                + "function mv(e){var r=pad.getBoundingClientRect(),cx=r.left+r.width/2,cy=r.top+r.height/2,R=r.width/2;"
                + "var dx=(e.clientX-cx)/R,dy=(e.clientY-cy)/R,l=Math.hypot(dx,dy);if(l>1){dx/=l;dy/=l;}"
                + "knob.style.transform='translate('+dx*R*.6+'px,'+dy*R*.6+'px)';st.x=Math.round(dx*10)/10;st.y=Math.round(dy*10)/10;send();}"
                + "pad.addEventListener('pointerdown',function(e){pid=e.pointerId;pad.setPointerCapture(pid);mv(e);});"
                + "pad.addEventListener('pointermove',function(e){if(e.pointerId===pid)mv(e);});"
                + "function up(e){if(e.pointerId!==pid)return;pid=null;knob.style.transform='';st.x=0;st.y=0;send();}"
                + "pad.addEventListener('pointerup',up);pad.addEventListener('pointercancel',up);"
                + "['a','b'].forEach(function(k){var el=document.getElementById(k);"
                + "el.addEventListener('pointerdown',function(e){el.setPointerCapture(e.pointerId);el.classList.add('on');st[k]=1;send();if(navigator.vibrate)navigator.vibrate(15);});"
                + "function off(){el.classList.remove('on');st[k]=0;send();}el.addEventListener('pointerup',off);el.addEventListener('pointercancel',off);});"
                + "</script></body></html>";
    }
}
