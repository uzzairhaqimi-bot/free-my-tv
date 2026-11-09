package io.freemytv;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.SystemClock;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

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
import java.util.HashMap;
import java.util.Map;

/**
 * Fork extra: the parents' page, opened on a phone from a QR code on the TV (Video & LED). It shows the stick (memory,
 * storage, uptime, version) and each child's progress in Kelas Bijak, and sets: the children's names, ages, Mod Mudah
 * and reading aloud; game time per day, bedtime and "learn first" (Parent); music and video links; a message shown on
 * the TV; and it can lighten the stick. Like KeyPage and LinkPage: the TV's private address, a random port and path,
 * thirty minutes, small bodies, nothing escapes its thread. With a PIN set, changes need it.
 */
final class ParentPage {
    static final long OPEN_FOR = 30 * 60_000L;
    final String url;
    private final String path;
    private final ServerSocket server;
    private final Context app;
    private final long until = System.currentTimeMillis() + OPEN_FOR;
    private volatile boolean closed;
    private final Handler main = new Handler(Looper.getMainLooper());

    ParentPage(Context c, InetAddress lan) throws IOException {
        app = c.getApplicationContext();
        byte[] random = new byte[12];
        new SecureRandom().nextBytes(random);
        path = "/" + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        server = new ServerSocket(0, 4, lan);
        url = "http://" + lan.getHostAddress() + ":" + server.getLocalPort() + path;
        Thread t = new Thread(this::serve, "ParentPage");
        t.setDaemon(true);
        t.start();
    }

    void close() {
        closed = true;
        try { server.close(); } catch (IOException ignored) { }
    }

    private void serve() {
        try {
            server.setSoTimeout(1000);
            while (!closed && System.currentTimeMillis() < until) {
                Socket s;
                try { s = server.accept(); } catch (java.net.SocketTimeoutException e) { continue; }
                try {
                    s.setSoTimeout(15_000);
                    answer(s.getInputStream(), s.getOutputStream());
                } catch (Throwable ignored) {
                    // a phone that went away
                } finally {
                    try { s.close(); } catch (IOException ignored) { }
                }
            }
        } catch (Throwable ignored) {
        } finally {
            close();
        }
    }

    private void answer(InputStream in, OutputStream out) throws Exception {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int ch, n = 0;
        while ((ch = in.read()) >= 0 && n++ < 8192) {
            head.write(ch);
            byte[] b = head.toByteArray();
            int l = b.length;
            if (l >= 4 && b[l - 4] == '\r' && b[l - 3] == '\n' && b[l - 2] == '\r' && b[l - 1] == '\n') break;
        }
        String h = head.toString("ISO-8859-1");
        String[] first = h.split("\r\n", 2)[0].split(" ");
        if (first.length < 2) return;
        String method = first[0], target = first[1];
        if (target.equals(path) && method.equals("GET")) { respond(out, "200 OK", "text/html; charset=utf-8", PAGE.getBytes(StandardCharsets.UTF_8)); return; }
        if (target.equals(path + "/state") && method.equals("GET")) { respond(out, "200 OK", "application/json", state().toString().getBytes(StandardCharsets.UTF_8)); return; }
        if (target.equals(path + "/set") && method.equals("POST")) {
            int len = 0;
            for (String line : h.split("\r\n")) if (line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:")) {
                try { len = Integer.parseInt(line.substring(15).trim()); } catch (NumberFormatException ignored) { }
            }
            if (len <= 0 || len > 16384) { respond(out, "413 Payload Too Large", "application/json", "{}".getBytes(StandardCharsets.UTF_8)); return; }
            byte[] body = new byte[len];
            int got = 0;
            while (got < len) { int r = in.read(body, got, len - got); if (r < 0) break; got += r; }
            Map<String, String> f = form(new String(body, 0, got, StandardCharsets.ISO_8859_1));
            String result = apply(f);
            JSONObject o = state();
            o.put("result", result);
            respond(out, "200 OK", "application/json", o.toString().getBytes(StandardCharsets.UTF_8));
            return;
        }
        respond(out, "404 Not Found", "text/plain", "Tiada".getBytes(StandardCharsets.UTF_8));
    }

    /** Carries out a change; returns what to tell the parent. */
    private String apply(Map<String, String> f) throws Exception {
        Parent p = Parent.load(app);
        if (!p.pin.isEmpty() && !p.pin.equals(f.get("pin"))) return "PIN salah.";
        String what = f.containsKey("what") ? f.get("what") : "";
        switch (what) {
            case "rules":
                p.limitMin = clampInt(f.get("limitMin"), 0, 600);
                p.bedtime = clampInt(f.get("bedtime"), -1, 23);
                p.learnFirst = clampInt(f.get("learnFirst"), 0, 100);
                p.learnSubject = clampInt(f.get("learnSubject"), -1, 3);
                if (f.containsKey("newPin")) {   // empty: unchanged; "0": no PIN; 4-6 digits: the new PIN
                    String np = f.get("newPin").replaceAll("[^0-9]", "");
                    if (np.equals("0")) p.pin = "";
                    else if (np.length() >= 4) p.pin = np;
                }
                p.save(app);
                return "Peraturan disimpan.";
            case "addtime":
                p.playedMs = Math.max(0, p.playedMs - 15 * 60_000L);
                p.save(app);
                return "Tambah 15 minit masa main hari ini.";
            case "kids": {
                LearnStore store = new LearnStore(app);
                for (int i = 0; i < LearnStore.KIDS; i++) {
                    LearnStore.Kid k = store.kids[i];
                    String name = f.get("name" + i);
                    if (name != null && !name.trim().isEmpty()) k.name = name.trim().length() > 14 ? name.trim().substring(0, 14) : name.trim();
                    int age = clampInt(f.get("age" + i), 5, 15);
                    if (age != k.age) { k.age = age; for (int s = 0; s < 4; s++) k.level[s] = 0; }   // the new age's level
                    k.mudah = "1".equals(f.get("mudah" + i));
                    k.baca = "1".equals(f.get("baca" + i));
                }
                store.save();
                return "Maklumat anak disimpan. Buka semula Kelas Bijak untuk melihatnya.";
            }
            case "message": {
                String msg = f.containsKey("message") ? f.get("message").trim() : "";
                if (msg.isEmpty()) return "Mesej kosong.";
                if (msg.length() > 120) msg = msg.substring(0, 120);
                p.message = msg;
                p.messageAt = System.currentTimeMillis();
                p.save(app);
                final String shown = msg;
                main.post(() -> {
                    Extras.motto = "📩 " + shown;   // on the home screen, big under the clock, until the next minute's line
                    Extras.mottoAt = SystemClock.uptimeMillis();
                    Toast.makeText(app, "📩 Mesej: " + shown, Toast.LENGTH_LONG).show();
                    Toast.makeText(app, "📩 Mesej: " + shown, Toast.LENGTH_LONG).show();
                });
                return "Mesej dihantar ke TV.";
            }
            case "links": {
                Extras x = Extras.load(app);
                String music = f.getOrDefault("music", "").trim(), video = f.getOrDefault("video", "").trim(), game = f.getOrDefault("game", "").trim();
                int got = 0;
                if (!Extras.parse(music).empty()) { x.musicLink = music; x.music = true; got++; }
                if (!Extras.parse(video).empty()) { x.link = video; x.video = true; got++; }
                if (!Extras.parse(game).empty()) { x.gameMusicLink = game; if (x.gameMusic == 3) x.gameMusic = 0; got++; }
                if ("1".equals(f.get("videoOff"))) { x.video = false; got++; }
                x.save(app);
                return got > 0 ? "Disimpan. Tekan Home di TV untuk lihat perubahan." : "Tiada pautan YouTube yang sah.";
            }
            case "lighten": {
                final Object lock = new Object();
                final String[] res = {null};
                main.post(() -> Lighten.run(app, (msg, closedN, freed) -> { synchronized (lock) { res[0] = msg; lock.notifyAll(); } }));
                synchronized (lock) {
                    long end = System.currentTimeMillis() + 12_000;
                    while (res[0] == null && System.currentTimeMillis() < end) lock.wait(500);
                }
                return res[0] == null ? "Sedang meringankan… semak semula sebentar lagi." : res[0];
            }
            default:
                return "";
        }
    }

    private JSONObject state() throws Exception {
        JSONObject o = new JSONObject();
        // the stick
        ActivityManager am = app.getSystemService(ActivityManager.class);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        if (am != null) am.getMemoryInfo(mi);
        StatFs st = new StatFs(Environment.getDataDirectory().getPath());
        JSONObject dev = new JSONObject();
        dev.put("model", Build.MODEL).put("android", Build.VERSION.RELEASE)
                .put("ramFreeMb", mi.availMem / 1048576).put("ramTotalMb", mi.totalMem / 1048576).put("ramLow", mi.lowMemory)
                .put("diskFreeMb", st.getAvailableBytes() / 1048576).put("diskTotalMb", st.getTotalBytes() / 1048576)
                .put("uptimeMin", SystemClock.elapsedRealtime() / 60_000);
        try { dev.put("version", app.getPackageManager().getPackageInfo(app.getPackageName(), 0).versionName); } catch (Exception ignored) { }
        o.put("device", dev);
        // the rules and today
        Parent p = Parent.load(app);
        o.put("rules", new JSONObject().put("limitMin", p.limitMin).put("bedtime", p.bedtime).put("learnFirst", p.learnFirst)
                .put("pin", !p.pin.isEmpty()).put("playedMin", p.playedMs / 60_000).put("rightsToday", p.rightsToday).put("learnSubject", p.learnSubject)
                .put("blocked", p.blocked() == null ? "" : p.blocked()));
        // the children
        LearnStore store = new LearnStore(app);
        JSONArray kids = new JSONArray();
        for (LearnStore.Kid k : store.kids) {
            JSONArray lv = new JSONArray();
            for (int s = 0; s < 4; s++) lv.put(store.level(k, s));
            kids.put(new JSONObject().put("name", k.name).put("age", k.age).put("stars", k.stars).put("days", k.days)
                    .put("mudah", k.mudah).put("baca", k.baca).put("answered", k.answered).put("right", k.right)
                    .put("mistakes", k.mistakes.size()).put("badges", k.badges.size()).put("level", lv)
                    .put("today", new JSONArray(java.util.Arrays.asList(k.today[0], k.today[1], k.today[2], k.today[3])))
                    .put("week", new JSONArray(java.util.Arrays.asList(k.week[0], k.week[1], k.week[2], k.week[3])))
                    .put("bonus", k.bonusSubject()));
        }
        o.put("kids", kids);
        Extras x = Extras.load(app);
        o.put("video", x.video).put("music", x.music);
        return o;
    }

    private static int clampInt(String s, int lo, int hi) {
        try { return Math.max(lo, Math.min(hi, Integer.parseInt(s.trim()))); } catch (Exception e) { return lo; }
    }

    private static Map<String, String> form(String body) {
        Map<String, String> m = new HashMap<>();
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            try { m.put(pair.substring(0, eq), URLDecoder.decode(pair.substring(eq + 1), "UTF-8")); } catch (Exception ignored) { }
        }
        return m;
    }

    private static void respond(OutputStream out, String status, String type, byte[] body) throws IOException {
        String head = "HTTP/1.1 " + status + "\r\nContent-Type: " + type + "\r\nContent-Length: " + body.length
                + "\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }

    private static final String PAGE = "<!doctype html><html lang=ms><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'>"
            + "<title>Ibu Bapa · Free My TV</title><style>"
            + "body{font-family:system-ui,sans-serif;background:#0b1020;color:#e2e8f0;margin:0;padding:16px;max-width:640px;margin:auto}"
            + "h1{font-size:22px;margin:4px 0 2px}h2{font-size:17px;margin:0 0 10px;color:#fde68a}.sub{color:#94a3b8;font-size:13px;margin-bottom:12px}"
            + ".card{background:#151c33;border-radius:16px;padding:14px;margin:12px 0;box-shadow:0 2px 10px #0006}"
            + ".grid{display:grid;grid-template-columns:1fr 1fr;gap:8px}.stat{background:#0f172a;border-radius:12px;padding:10px}"
            + ".stat b{display:block;font-size:20px;color:#fff}.stat span{font-size:12px;color:#94a3b8}"
            + "label{display:block;font-size:13px;color:#cbd5e1;margin:10px 0 4px}input,select,textarea{width:100%;box-sizing:border-box;"
            + "padding:10px;border-radius:10px;border:1px solid #334155;background:#0f172a;color:#fff;font-size:16px}"
            + "button{width:100%;margin-top:12px;padding:13px;border:0;border-radius:12px;font-size:16px;font-weight:700;background:#22c55e;color:#fff}"
            + "button.alt{background:#334155}button.warn{background:#f59e0b}.kid{border-top:1px solid #1e293b;padding-top:10px;margin-top:10px}"
            + ".row{display:flex;gap:8px}.row>*{flex:1}.chk{display:flex;align-items:center;gap:8px;margin-top:8px}.chk input{width:auto}"
            + ".bar{height:8px;background:#1e293b;border-radius:4px;overflow:hidden}.bar i{display:block;height:100%;background:#22c55e}"
            + "#toast{position:fixed;left:16px;right:16px;bottom:16px;background:#22c55e;color:#fff;padding:14px;border-radius:12px;display:none;font-weight:700}"
            + "</style><h1>👨‍👩‍👧 Halaman Ibu Bapa</h1><div class=sub>Free My TV · halaman ini tamat dalam 30 minit</div>"
            + "<div class=card><h2>📺 Stick</h2><div class=grid id=dev></div><button class=warn onclick=\"send({what:'lighten'})\">⚡ Ringankan stick sekarang</button></div>"
            + "<div class=card><h2>⏰ Hari ini</h2><div id=today></div></div>"
            + "<div class=card><h2>🛡️ Peraturan permainan</h2>"
            + "<label>Had masa main sehari (Papan Permainan & Retro Arked)</label><select id=limitMin>"
            + "<option value=0>Tiada had</option><option value=30>30 minit</option><option value=45>45 minit</option><option value=60>1 jam</option>"
            + "<option value=90>1 jam 30 minit</option><option value=120>2 jam</option></select>"
            + "<label>Waktu tidur (permainan ditutup sehingga 6 pagi)</label><select id=bedtime><option value=-1>Tiada</option>"
            + "<option value=20>8 malam</option><option value=21>9 malam</option><option value=22>10 malam</option><option value=23>11 malam</option></select>"
            + "<label>Belajar dulu: jawapan betul dalam Kelas Bijak sebelum boleh main</label><select id=learnFirst><option value=0>Tidak perlu</option>"
            + "<option value=5>5 jawapan betul</option><option value=10>10 jawapan betul</option><option value=20>20 jawapan betul</option></select>"
            + "<label>Subjek wajib untuk 'belajar dulu'</label><select id=learnSubject><option value=-1>Mana-mana subjek</option>"
            + "<option value=0>Matematik</option><option value=1>Bahasa Melayu</option><option value=2>English</option><option value=3>Sains & Umum</option></select>"
            + "<label>PIN baru (4 digit, supaya anak tak boleh ubah · kosong = tak tukar · taip 0 untuk buang PIN)</label><input id=newPin inputmode=numeric maxlength=6 placeholder='contoh 1234'>"
            + "<button onclick=rules()>Simpan peraturan</button><button class=alt onclick=\"send({what:'addtime'})\">➕ Tambah 15 minit hari ini</button></div>"
            + "<div class=card><h2>🧒 Anak-anak (Kelas Bijak)</h2><div id=kids></div><button onclick=kids()>Simpan maklumat anak</button></div>"
            + "<div class=card><h2>📩 Mesej ke TV</h2><textarea id=msg rows=2 placeholder='Contoh: Makan malam dah siap! 🍛'></textarea>"
            + "<button onclick=\"send({what:'message',message:v('msg')})\">Hantar ke TV</button></div>"
            + "<div class=card><h2>🎵 Lagu & video skrin utama</h2><div class=sub>YouTube: Kongsi → Salin pautan, kemudian tampal</div>"
            + "<label>Lagu skrin utama</label><textarea id=music rows=2></textarea><label>Video latar</label><textarea id=video rows=2></textarea>"
            + "<label>Lagu dalam permainan</label><textarea id=game rows=2></textarea>"
            + "<div class=chk><input type=checkbox id=videoOff><span>Matikan video latar (jimat RAM)</span></div>"
            + "<button onclick=\"send({what:'links',music:v('music'),video:v('video'),game:v('game'),videoOff:c('videoOff')})\">Simpan</button></div>"
            + "<div class=card id=pinbox style=display:none><label>PIN ibu bapa</label><input id=pin inputmode=numeric maxlength=6></div>"
            + "<div id=toast></div><script>"
            + "const P=location.pathname;let S=null;const v=i=>document.getElementById(i).value;const c=i=>document.getElementById(i).checked?'1':'0';"
            + "const SUB=['Matematik','BM','English','Sains'];"
            + "function toast(t){const e=document.getElementById('toast');e.textContent=t;e.style.display='block';setTimeout(()=>e.style.display='none',3500)}"
            + "function stat(b,s){return '<div class=stat><b>'+b+'</b><span>'+s+'</span></div>'}"
            + "function esc(s){return String(s).replace(/[&<>\\\"']/g,m=>({'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;',\"'\":'&#39;'}[m]))}"
            + "function show(s){S=s;const d=s.device;"
            + "document.getElementById('dev').innerHTML=stat(d.ramFreeMb+' MB','RAM kosong / '+d.ramTotalMb+' MB'+(d.ramLow?' ⚠️':''))"
            + "+stat((d.diskFreeMb/1024).toFixed(1)+' GB','storan kosong')+stat(Math.floor(d.uptimeMin/60)+' j '+(d.uptimeMin%60)+' m','sejak dihidupkan')"
            + "+stat(esc(d.model),'Android '+esc(d.android)+' · Free My TV '+esc(d.version||''));"
            + "const r=s.rules;document.getElementById('today').innerHTML=stat(r.playedMin+' minit','sudah main hari ini'+(r.limitMin?' / '+r.limitMin:''))"
            + "+'<div style=height:8px></div>'+stat(r.rightsToday,'jawapan betul Kelas Bijak hari ini')"
            + "+(r.blocked?'<div class=sub style=margin-top:8px>🔒 '+esc(r.blocked)+'</div>':'<div class=sub style=margin-top:8px>✅ Permainan dibuka</div>');"
            + "document.getElementById('limitMin').value=r.limitMin;document.getElementById('bedtime').value=r.bedtime;document.getElementById('learnFirst').value=r.learnFirst;document.getElementById('learnSubject').value=r.learnSubject;"
            + "document.getElementById('pinbox').style.display=r.pin?'block':'none';"
            + "let h='';s.kids.forEach((k,i)=>{const acc=k.answered?Math.round(100*k.right/k.answered):0;"
            + "h+='<div class=kid><div class=row><div><label>Nama</label><input id=name'+i+' value=\"'+esc(k.name)+'\"></div>'"
            + "+'<div style=flex:0.5><label>Umur</label><input id=age'+i+' type=number min=5 max=15 value='+k.age+'></div></div>'"
            + "+'<div class=chk><input type=checkbox id=mudah'+i+(k.mudah?' checked':'')+'><span>Mod Mudah (petunjuk, masa lebih)</span></div>'"
            + "+'<div class=chk><input type=checkbox id=baca'+i+(k.baca?' checked':'')+'><span>Bacakan soalan dengan suara</span></div>'"
            + "+'<div class=sub style=margin-top:8px>⭐ '+k.stars+' bintang · 🔥 '+k.days+' hari · '+k.answered+' dijawab · '+acc+'% betul · '+k.mistakes+' perlu ulang kaji</div>'"
            + "+'<div class=sub>Tahap: '+k.level.map((l,j)=>SUB[j]+' '+l).join(' · ')+'</div>'"
            + "+'<div class=sub>Hari ini: '+k.today.map((n,j)=>SUB[j]+' '+n).join(' · ')+'<br>Minggu ini: '+k.week.map((n,j)=>SUB[j]+' '+n).join(' · ')"
            + "+'<br>✨ Bintang ×2 hari ini: '+SUB[k.bonus]+'</div>'"
            + "+'<div class=bar><i style=width:'+acc+'%></i></div></div>'});document.getElementById('kids').innerHTML=h;}"
            + "async function send(o){if(S&&S.rules.pin)o.pin=v('pin');const b=new URLSearchParams(o);"
            + "try{const r=await fetch(P+'/set',{method:'POST',body:b});const s=await r.json();show(s);toast(s.result||'Siap')}catch(e){toast('Tidak dapat sambung ke TV. Buka semula kod QR.')}}"
            + "function rules(){send({what:'rules',limitMin:v('limitMin'),bedtime:v('bedtime'),learnFirst:v('learnFirst'),learnSubject:v('learnSubject'),newPin:v('newPin')})}"
            + "function kids(){const o={what:'kids'};S.kids.forEach((k,i)=>{o['name'+i]=v('name'+i);o['age'+i]=v('age'+i);o['mudah'+i]=c('mudah'+i);o['baca'+i]=c('baca'+i)});send(o)}"
            + "fetch(P+'/state').then(r=>r.json()).then(show).catch(()=>toast('Tidak dapat sambung ke TV'));"
            + "setInterval(()=>fetch(P+'/state').then(r=>r.json()).then(s=>{S=s;const d=s.device;}).catch(()=>{}),30000);"
            + "</script></html>";
}
