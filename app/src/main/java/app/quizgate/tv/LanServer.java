/*
 * QuizGate TV · VIDAA Edition
 * Fork of QuizGate TV (https://github.com/mtvtopcom/quizgate-tv), Apache-2.0.
 * NEW FILE added by qualified12; not present in upstream.
 * See NOTICE for the full list of changes.
 */

package app.quizgate.tv;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Enumeration;

/**
 * 极简局域网手机管理端（端口 8787）：
 * 手机同一 Wi-Fi 下浏览器打开 http://<电视IP>:8787 即可查看今日观看时长、星星积分、错题记录，
 * 输入家长口令后可远程立即锁屏或奖励 +15 分钟。
 */
public final class LanServer {
    private static final String TAG = "QuizGate";
    public static final int PORT = 8787;

    private final Context ctx;
    private ServerSocket server;
    private volatile boolean alive = false;

    public LanServer(Context c) {
        this.ctx = c.getApplicationContext();
    }

    public void start() {
        if (alive) return;
        alive = true;
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    server = new ServerSocket(PORT);
                    Log.i(TAG, "lan_server listening on :" + PORT);
                    while (alive) {
                        Socket s = server.accept();
                        handleClient(s);
                    }
                } catch (Throwable e) {
                    if (alive) Log.w(TAG, "lan_server stopped: " + e);
                }
            }
        }, "QuizGateLan");
        t.setDaemon(true);
        t.start();
    }

    public void stop() {
        alive = false;
        if (server != null) {
            try { server.close(); } catch (Throwable ignore) {}
            server = null;
        }
    }

    public static String localIp() {
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs != null && ifs.hasMoreElements()) {
                NetworkInterface ni = ifs.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a.getAddress().length == 4 && !a.isLoopbackAddress()) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Throwable ignore) {}
        return "电视IP";
    }

    private void handleClient(Socket s) {
        try {
            s.setSoTimeout(3000);
            BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
            String reqLine = br.readLine();
            if (reqLine == null) { s.close(); return; }
            String path = "/";
            String[] parts = reqLine.split(" ");
            if (parts.length >= 2) path = parts[1];

            String msg = "";
            if (path.startsWith("/action?")) {
                msg = handleAction(path);
            }

            String html = buildHtml(msg);
            byte[] body = html.getBytes("UTF-8");
            OutputStream os = s.getOutputStream();
            String hdr = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: text/html; charset=utf-8\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            os.write(hdr.getBytes("UTF-8"));
            os.write(body);
            os.flush();
            s.close();
        } catch (Throwable ignore) {
            try { s.close(); } catch (Throwable ignore2) {}
        }
    }

    private String handleAction(String path) {
        Prefs p = new Prefs(ctx);
        String pin = extractParam(path, "pin");
        String cmd = extractParam(path, "cmd");
        if (p.hasPin() && p.verifyPin(pin) != 0) {
            return "家长口令错误，操作被拒绝！";
        }
        if ("lock".equals(cmd)) {
            Intent i = new Intent(ctx, GateService.class).setAction(GateService.ACTION_FORCE_LOCK);
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
            else ctx.startService(i);
            return "已发送远程立即锁屏指令！";
        } else if ("bonus15".equals(cmd)) {
            p.addDailyBonusMin(15);
            Intent i = new Intent(ctx, GateService.class).setAction(GateService.ACTION_BONUS_15);
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
            else ctx.startService(i);
            return "已为今日额外增加 15 分钟观看额度！";
        } else if ("adult_on".equals(cmd)) {
            Intent i = new Intent(ctx, GateService.class).setAction(GateService.ACTION_ADULT_ON);
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
            else ctx.startService(i);
            return "已开启【大人模式】！本次观看不再锁屏答题，下次开机自动恢复儿童管控。";
        } else if ("adult_off".equals(cmd)) {
            Intent i = new Intent(ctx, GateService.class).setAction(GateService.ACTION_ADULT_OFF);
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
            else ctx.startService(i);
            return "已关闭大人模式，恢复儿童规则保护！";
        }
        return "";
    }

    private static String extractParam(String url, String key) {
        int q = url.indexOf('?');
        if (q < 0) return "";
        String[] pairs = url.substring(q + 1).split("&");
        for (int i = 0; i < pairs.length; i++) {
            String[] kv = pairs[i].split("=", 2);
            if (kv.length == 2 && key.equals(kv[0])) return kv[1];
        }
        return "";
    }

    private String buildHtml(String msg) {
        Prefs p = new Prefs(ctx);
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html><head><meta charset='utf-8'>")
          .append("<meta name='viewport' content='width=device-width,initial-scale=1'>")
          .append("<title>答题续时 · 家长手机控制台</title>")
          .append("<style>body{font-family:sans-serif;background:#0b0e14;color:#fff;padding:16px;max-width:600px;margin:auto}")
          .append(".card{background:#161b24;border:1px solid #2d3748;border-radius:12px;padding:16px;margin-bottom:14px}")
          .append(".acc{color:#fdc90c;font-weight:bold}input,button{padding:10px;border-radius:8px;border:1px solid #444;font-size:16px}")
          .append("button{background:#fdc90c;color:#000;font-weight:bold;border:none;margin:4px 4px 0 0;cursor:pointer}")
          .append(".err{color:#ff5252;margin-bottom:10px}</style></head><body>");
        sb.append("<h2>答题续时 · 家长手机端</h2>");
        if (msg != null && !msg.isEmpty()) sb.append("<div class='err'>").append(msg).append("</div>");
        sb.append("<div class='card'><div>当前状态：<span class='acc'>").append(Prefs.phaseName(p.phase())).append("</span></div>")
          .append("<div>今日已看：<span class='acc'>").append(p.dailyUsedMs() / 60_000L).append(" 分钟</span> / 上限 ")
          .append(p.dailyLimitMin() == 0 ? "不限" : (p.dailyLimitMin() + p.dailyBonusMin() + " 分钟")).append("</div>")
          .append("<div>星星积分：<span class='acc'>★ ").append(p.stars()).append("</span></div>")
          .append("<div>宵禁时段：").append(p.curfewName()).append("</div>")
          .append("<div>题源范围：").append(p.gradesSummary()).append(" · ").append(p.subjectsSummary()).append("</div></div>");

        sb.append("<div class='card'><form action='/action' method='get'>")
          .append("<div>家长口令：<input name='pin' type='password' placeholder='输入6位口令' style='width:140px;background:#0b0e14;color:#fff'></div><br>")
          .append("<button type='submit' name='cmd' value='adult_on'>开启大人模式(免控)</button>")
          .append("<button type='submit' name='cmd' value='adult_off'>恢复儿童管控</button>")
          .append("<button type='submit' name='cmd' value='lock'>立即锁屏答题</button>")
          .append("<button type='submit' name='cmd' value='bonus15'>今日+15分钟</button>")
          .append("</form></div>");

        JSONArray arr = p.getWrongArray();
        sb.append("<div class='card'><h3>最近错题记录（").append(arr.length()).append(" 题）</h3>");
        for (int i = 0; i < arr.length() && i < 15; i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            sb.append("<div style='border-top:1px solid #2d3748;padding:8px 0'>")
              .append("<div><b>").append(o.optString("b","")).append("</b> ").append(o.optString("s","")).append("</div>")
              .append("<div style='color:#9aa0a6;font-size:14px'>错选：").append(o.optString("w",""))
              .append(" | 正解：<span class='acc'>").append(o.optString("r","")).append("</span></div></div>");
        }
        sb.append("</div></body></html>");
        return sb.toString();
    }
}
