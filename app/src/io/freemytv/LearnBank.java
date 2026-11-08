package io.freemytv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Fork extra (Kelas Bijak): questions for ages 7 to 14. Maths is generated, so it never runs out; the language and
 * science banks are written for this fork and shuffled, and new packs can be added from the internet (LearnPack).
 * Pure Java with no Android classes, so it can be tested on a computer.
 */
final class LearnBank {
    static final int MATH = 0, BM = 1, EN = 2, SAINS = 3;
    static final String[] SUBJECTS = {"Matematik", "Bahasa Melayu", "English", "Sains & Umum"};
    static final int LEVELS = 8;

    static final class Q {
        final int subject, level;
        final String key, prompt, explain;
        final String[] options;
        final int answer;

        Q(int subject, int level, String key, String prompt, String[] options, int answer, String explain) {
            this.subject = subject;
            this.level = level;
            this.key = key;
            this.prompt = prompt;
            this.options = options;
            this.answer = answer;
            this.explain = explain;
        }

        String correct() { return options[answer]; }
    }

    /** A written question: the right answer first, then wrong ones; shuffled when asked. */
    static final class Item {
        final int subject, minLevel;
        final String prompt, explain;
        final String[] answers;   // [0] is right

        Item(int subject, int minLevel, String prompt, String explain, String... answers) {
            this.subject = subject;
            this.minLevel = minLevel;
            this.prompt = prompt;
            this.explain = explain;
            this.answers = answers;
        }
    }

    final Random rnd;
    final List<Item> items = new ArrayList<>();

    LearnBank(long seed) {
        rnd = new Random(seed);
        LearnBankData.fill(items);
    }

    void add(Item it) { items.add(it); }

    /** A question for this subject and level, avoiding the keys in `recent`. */
    Q next(int subject, int level, Set<String> recent) {
        level = Math.max(1, Math.min(LEVELS, level));
        for (int tries = 0; tries < 40; tries++) {
            Q q = subject == MATH ? math(level) : written(subject, level);
            if (q != null && !recent.contains(q.key)) return q;
        }
        return subject == MATH ? math(level) : written(subject, level);
    }

    /** A question again from its key (Ulang Kaji). Maths keys are not kept; null when it can't be rebuilt. */
    Q fromKey(String key, int level) {
        for (Item it : items) {
            if (key.equals("w:" + it.prompt)) return asked(it, level);
            if (key.equals("s:" + (it.answers.length > 0 ? it.answers[0] : "")) && it.prompt.startsWith("@spell:")) return spelling(it, level);
        }
        return null;
    }

    private Q asked(Item it, int level) {
        List<String> opts = new ArrayList<>();
        opts.add(it.answers[0]);
        List<String> wrong = new ArrayList<>();
        for (int i = 1; i < it.answers.length; i++) wrong.add(it.answers[i]);
        Collections.shuffle(wrong, rnd);
        for (int i = 0; i < Math.min(3, wrong.size()); i++) opts.add(wrong.get(i));
        Collections.shuffle(opts, rnd);
        return new Q(it.subject, level, "w:" + it.prompt, it.prompt, opts.toArray(new String[0]), opts.indexOf(it.answers[0]), it.explain);
    }

    // ------------------------------------------------------------------ written banks

    private Q written(int subject, int level) {
        List<Item> pool = new ArrayList<>();
        for (Item it : items) if (it.subject == subject && it.minLevel <= level && it.minLevel >= level - 3) pool.add(it);
        if (pool.isEmpty()) for (Item it : items) if (it.subject == subject) pool.add(it);
        if (pool.isEmpty()) return null;
        Item it = pool.get(rnd.nextInt(pool.size()));
        if (it.prompt.startsWith("@spell:")) return spelling(it, level);
        return asked(it, level);
    }

    /** "Which spelling is right?": the word and three near misses (swapped, doubled or dropped letters). */
    private Q spelling(Item it, int level) {
        String word = it.answers[0];
        Set<String> wrong = new LinkedHashSet<>();
        for (int i = 1; i < it.answers.length; i++) wrong.add(it.answers[i]);
        int guard = 0;
        while (wrong.size() < 3 && guard++ < 60) {
            String w = misspell(word);
            if (!w.equals(word)) wrong.add(w);
        }
        List<String> opts = new ArrayList<>();
        opts.add(word);
        int n = 0;
        for (String w : wrong) { if (n++ == 3) break; opts.add(w); }
        Collections.shuffle(opts, rnd);
        String lang = it.prompt.substring(7);
        String prompt = lang.equals("BM") ? "Ejaan manakah yang betul?" : "Which spelling is correct?";
        return new Q(it.subject, level, "s:" + word, prompt, opts.toArray(new String[0]), opts.indexOf(word), it.explain);
    }

    private String misspell(String w) {
        if (w.length() < 4) return w + w.charAt(w.length() - 1);
        int i = 1 + rnd.nextInt(w.length() - 2);
        switch (rnd.nextInt(3)) {
            case 0: return w.substring(0, i) + w.charAt(i + 1) + w.charAt(i) + w.substring(i + 2);   // swap
            case 1: return w.substring(0, i) + w.charAt(i) + w.substring(i);                         // double
            default: return w.substring(0, i) + w.substring(i + 1);                                  // drop
        }
    }

    // ------------------------------------------------------------------ maths, generated

    private static final String[] NAMES = {"Ali", "Mei Ling", "Siva", "Aisyah", "Daniel", "Nurul", "Kumar", "Farah", "Wei Jie", "Hana"};
    private static final String[] THINGS = {"guli", "pensel", "buku", "epal", "kek cawan", "pelekat", "bola", "kad"};

    private int r(int lo, int hi) { return lo + rnd.nextInt(hi - lo + 1); }

    private String name() { return NAMES[rnd.nextInt(NAMES.length)]; }

    private String thing() { return THINGS[rnd.nextInt(THINGS.length)]; }

    private Q mathQ(int level, String key, String prompt, String answer, List<String> wrong, String explain) {
        LinkedHashSet<String> opts = new LinkedHashSet<>();
        opts.add(answer);
        for (String w : wrong) if (opts.size() < 4 && !w.equals(answer)) opts.add(w);
        List<String> list = new ArrayList<>(opts);
        Collections.shuffle(list, rnd);
        return new Q(MATH, level, "m:" + key, prompt, list.toArray(new String[0]), list.indexOf(answer), explain);
    }

    /** Near misses around an integer answer, never negative unless the answer is. */
    private List<String> near(int a, int... deltas) {
        List<String> w = new ArrayList<>();
        for (int d : deltas) {
            int v = a + d;
            if (a >= 0 && v < 0) v = a + Math.abs(d) + 1;
            w.add(String.valueOf(v));
        }
        for (int k = 1; w.size() < 6; k++) w.add(String.valueOf(a + k * (k % 2 == 0 ? -1 : 1) * (1 + rnd.nextInt(3))));
        Collections.shuffle(w, rnd);
        return w;
    }

    private static String fmt(double v) {
        if (Math.abs(v - Math.round(v)) < 1e-9) return String.valueOf(Math.round(v));
        String s = String.format(Locale.US, "%.2f", v);
        while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private static String rm(int sen) {
        return String.format(Locale.US, "RM%d.%02d", sen / 100, sen % 100);
    }

    private static int gcd(int a, int b) { return b == 0 ? Math.abs(a) : gcd(b, a % b); }

    private static String frac(int n, int d) {
        int g = gcd(n, d);
        n /= g;
        d /= g;
        if (d == 1) return String.valueOf(n);
        return n + "/" + d;
    }

    Q math(int level) {
        int kind;
        switch (level) {
            case 1: kind = rnd.nextInt(3); break;            // + − within 100, word problem
            case 2: kind = 2 + rnd.nextInt(3); break;        // up to 1000, tables 2-5, money
            case 3: kind = 4 + rnd.nextInt(4); break;        // tables to 12, division, time, money
            case 4: kind = 6 + rnd.nextInt(4); break;        // ×, fraction of, perimeter
            case 5: kind = 9 + rnd.nextInt(4); break;        // fractions, decimals, %, area
            case 6: kind = 12 + rnd.nextInt(4); break;       // order of ops, negatives, ratio, %
            case 7: kind = 15 + rnd.nextInt(4); break;       // algebra, squares, angles, mean
            default: kind = 17 + rnd.nextInt(5); break;      // algebra, powers, probability, mean
        }
        switch (kind) {
            case 0: {
                int a = r(10, 60), b = r(5, 39);
                return mathQ(level, "a" + a + "+" + b, a + " + " + b + " = ?", String.valueOf(a + b), near(a + b, -10, 10, -1, 1), a + " + " + b + " = " + (a + b));
            }
            case 1: {
                int a = r(30, 99), b = r(5, a - 5);
                return mathQ(level, "s" + a + "-" + b, a + " − " + b + " = ?", String.valueOf(a - b), near(a - b, -10, 10, 1, -1), a + " − " + b + " = " + (a - b));
            }
            case 2: {
                String n = name(), t = thing();
                int a = r(12, 60);
                boolean give = rnd.nextBoolean();
                int b = give ? r(3, a - 2) : r(5, 30);
                int ans = give ? a - b : a + b;
                String p = give ? n + " ada " + a + " " + t + ". Dia beri " + b + " kepada kawan. Berapa tinggal?"
                        : n + " ada " + a + " " + t + ". Ibu beri " + b + " lagi. Berapa semuanya?";
                return mathQ(level, "w" + a + (give ? "-" : "+") + b, p, String.valueOf(ans), near(ans, -10, 10, -1, 1),
                        a + (give ? " − " : " + ") + b + " = " + ans);
            }
            case 3: {
                int a = r(100, 600), b = r(50, 399);
                return mathQ(level, "a" + a + "+" + b, a + " + " + b + " = ?", String.valueOf(a + b), near(a + b, -100, 100, -10, 10), a + " + " + b + " = " + (a + b));
            }
            case 4: {
                int a = r(2, level <= 2 ? 5 : 12), b = r(2, 12);
                return mathQ(level, "x" + a + "*" + b, a + " × " + b + " = ?", String.valueOf(a * b), near(a * b, -a, a, -b, b, 1),
                        a + " × " + b + " = " + (a * b));
            }
            case 5: {
                int a = r(1, 9) * 10 + r(0, 1) * 5, b = r(1, 9) * 10;   // sen
                int s1 = r(1, 9) * 100 + a, s2 = r(1, 5) * 100 + b;
                return mathQ(level, "rm" + s1 + "+" + s2, rm(s1) + " + " + rm(s2) + " = ?", rm(s1 + s2),
                        java.util.Arrays.asList(rm(s1 + s2 + 10), rm(s1 + s2 - 10), rm(s1 + s2 + 100), rm(s1 + s2 - 100)),
                        rm(s1) + " + " + rm(s2) + " = " + rm(s1 + s2));
            }
            case 6: {
                int b = r(2, 12), ans = r(2, 12), a = b * ans;
                return mathQ(level, "d" + a + "/" + b, a + " ÷ " + b + " = ?", String.valueOf(ans), near(ans, -1, 1, 2, -2),
                        a + " ÷ " + b + " = " + ans + " kerana " + b + " × " + ans + " = " + a);
            }
            case 7: {
                int h = r(1, 10), m = r(0, 11) * 5, add = r(2, 11) * 5;
                int t = h * 60 + m + add, nh = ((t / 60) - 1) % 12 + 1, nm = t % 60;
                String now = String.format(Locale.US, "%d:%02d", h, m), ans = String.format(Locale.US, "%d:%02d", nh, nm);
                List<String> w = new ArrayList<>();
                w.add(String.format(Locale.US, "%d:%02d", nh, (nm + 10) % 60));
                w.add(String.format(Locale.US, "%d:%02d", nh % 12 + 1, nm));
                w.add(String.format(Locale.US, "%d:%02d", nh, (nm + 55) % 60));
                return mathQ(level, "t" + now + "+" + add, "Sekarang pukul " + now + ". Pukul berapa " + add + " minit lagi?", ans, w,
                        now + " + " + add + " minit = " + ans);
            }
            case 8: {
                int a = r(12, 99), b = r(3, 9);
                return mathQ(level, "x" + a + "*" + b, a + " × " + b + " = ?", String.valueOf(a * b), near(a * b, -b, b, -10, 10),
                        a + " × " + b + " = " + (a * b));
            }
            case 9: {
                int d = new int[]{2, 3, 4, 5, 10}[rnd.nextInt(5)], n = r(1, d - 1), total = d * r(2, 12);
                int ans = total / d * n;
                return mathQ(level, "f" + n + "/" + d + "of" + total, n + "/" + d + " daripada " + total + " = ?", String.valueOf(ans),
                        near(ans, -total / d, total / d, -1, 2), total + " ÷ " + d + " × " + n + " = " + ans);
            }
            case 10: {
                int l = r(3, 20), w = r(2, 15);
                boolean area = rnd.nextBoolean() && level >= 5;
                int ans = area ? l * w : 2 * (l + w);
                String p = "Segi empat tepat " + l + " cm × " + w + " cm. Berapakah " + (area ? "luasnya (cm²)?" : "perimeternya (cm)?");
                List<String> wrong = near(ans, 0, 1, -2);
                wrong.add(0, String.valueOf(area ? 2 * (l + w) : l * w));
                wrong.add(1, String.valueOf(area ? l + w : l + w));
                return mathQ(level, (area ? "ar" : "pe") + l + "x" + w, p, String.valueOf(ans), wrong,
                        area ? "Luas = panjang × lebar = " + l + " × " + w + " = " + ans
                                : "Perimeter = 2 × (" + l + " + " + w + ") = " + ans);
            }
            case 11: {
                int d = new int[]{4, 5, 6, 8, 10, 12}[rnd.nextInt(6)], a = r(1, d - 1), b = r(1, d - 1);
                String ans = frac(a + b, d);
                List<String> w = new ArrayList<>();
                w.add((a + b) + "/" + (d + d));
                w.add(frac(Math.abs(a - b) + 1, d));
                w.add(frac(a + b + 1, d));
                w.add(frac(a * b, d));
                return mathQ(level, "fa" + a + "+" + b + "/" + d, a + "/" + d + " + " + b + "/" + d + " = ?", ans, w,
                        "Penyebut sama: tambah pengangka. " + a + " + " + b + " = " + (a + b) + ", jadi " + (a + b) + "/" + d + " = " + ans);
            }
            case 12: {
                double a = r(10, 99) / 10.0, b = r(10, 99) / 10.0;
                String ans = fmt(a + b);
                return mathQ(level, "da" + a + "+" + b, fmt(a) + " + " + fmt(b) + " = ?", ans,
                        java.util.Arrays.asList(fmt(a + b + 1), fmt(a + b - 0.1), fmt(a + b + 0.1), fmt(a + b + 10)),
                        fmt(a) + " + " + fmt(b) + " = " + ans);
            }
            case 13: {
                int p = new int[]{10, 20, 25, 50, 75, 5, 15, 30}[rnd.nextInt(8)], n = r(1, 20) * 20;
                int ans = n * p / 100;
                return mathQ(level, "pc" + p + "of" + n, p + "% daripada " + n + " = ?", String.valueOf(ans), near(ans, -5, 5, 10, -10),
                        n + " × " + p + "/100 = " + ans);
            }
            case 14: {
                int a = r(2, 20), b = r(2, 9), c = r(2, 9);
                boolean plus = rnd.nextBoolean();
                int ans = plus ? a + b * c : a * b - c;
                String p = plus ? a + " + " + b + " × " + c + " = ?" : a + " × " + b + " − " + c + " = ?";
                int wrong1 = plus ? (a + b) * c : a * (b - c);
                return mathQ(level, "oo" + p, p, String.valueOf(ans), near(ans, wrong1 - ans, -1, 1, 2),
                        "Darab dulu, kemudian tambah/tolak: " + (plus ? b + " × " + c + " = " + (b * c) + ", " + a + " + " + (b * c) + " = " + ans
                                : a + " × " + b + " = " + (a * b) + ", " + (a * b) + " − " + c + " = " + ans));
            }
            case 15: {
                int a = r(-20, 20), b = r(-20, 20);
                if (a == 0) a = 7;
                boolean minus = rnd.nextBoolean();
                int ans = minus ? a - b : a + b;
                String bs = b < 0 ? "(" + b + ")" : String.valueOf(b);
                return mathQ(level, "ng" + a + (minus ? "-" : "+") + b, a + (minus ? " − " : " + ") + bs + " = ?", String.valueOf(ans),
                        near(ans, -2 * b, 2 * b, -1, 1), a + (minus ? " − " : " + ") + bs + " = " + ans);
            }
            case 16: {
                int a = r(1, 5), b = r(1, 5), k = r(2, 9);
                if (a == b) b++;
                String n = name();
                int total = (a + b) * k, ans = a * k;
                return mathQ(level, "ra" + a + ":" + b + "t" + total,
                        n + " dan kawan berkongsi " + total + " guli dengan nisbah " + a + " : " + b + ". Berapa guli " + n + "?",
                        String.valueOf(ans), near(ans, b * k - ans, -k, k, 1),
                        "Jumlah bahagian " + (a + b) + ". Satu bahagian = " + total + " ÷ " + (a + b) + " = " + k + ". " + a + " × " + k + " = " + ans);
            }
            case 17: {
                int x = r(-6, 12), a = r(2, 9), b = r(1, 20) * (rnd.nextBoolean() ? 1 : -1);
                int c = a * x + b;
                String bs = b >= 0 ? " + " + b : " − " + (-b);
                return mathQ(level, "al" + a + "x" + b + "=" + c, "Cari x:  " + a + "x" + bs + " = " + c, "x = " + x,
                        java.util.Arrays.asList("x = " + (x + 1), "x = " + (x - 1), "x = " + (c - b), "x = " + (x + 2), "x = " + (-x)),
                        a + "x = " + c + (b >= 0 ? " − " + b : " + " + (-b)) + " = " + (c - b) + ", jadi x = " + (c - b) + " ÷ " + a + " = " + x);
            }
            case 18: {
                if (rnd.nextBoolean()) {
                    int n = r(2, 15);
                    return mathQ(level, "sq" + n, n + "² = ?", String.valueOf(n * n), near(n * n, -n, n, 2 * n - n * n, 1), n + " × " + n + " = " + (n * n));
                }
                int n = r(2, 15);
                return mathQ(level, "rt" + n, "√" + (n * n) + " = ?", String.valueOf(n), near(n, -1, 1, 2, n), n + " × " + n + " = " + (n * n));
            }
            case 19: {
                int a = r(20, 90), b = r(20, 150 - a);
                int ans = 180 - a - b;
                return mathQ(level, "an" + a + "," + b, "Dua sudut segi tiga ialah " + a + "° dan " + b + "°. Berapakah sudut ketiga?",
                        ans + "°", java.util.Arrays.asList((360 - a - b) + "°", (ans + 10) + "°", (ans - 10) + "°", (90 - a) + "°"),
                        "Jumlah sudut segi tiga = 180°. 180 − " + a + " − " + b + " = " + ans + "°");
            }
            case 20: {
                if (rnd.nextBoolean()) {
                    int base = r(2, 5), e = r(2, base == 2 ? 6 : 3), ans = (int) Math.pow(base, e);
                    return mathQ(level, "pw" + base + "^" + e, base + "^" + e + " = ?", String.valueOf(ans), near(ans, base * e - ans, -base, base, 1),
                            base + " didarab dengan dirinya " + e + " kali = " + ans);
                }
                int red = r(1, 6), blue = r(1, 6), green = r(1, 5), total = red + blue + green;
                String ans = frac(red, total);
                return mathQ(level, "pr" + red + "," + blue + "," + green,
                        "Beg ada " + red + " bola merah, " + blue + " biru dan " + green + " hijau. Kebarangkalian dapat bola merah?",
                        ans, java.util.Arrays.asList(frac(blue, total), frac(green, total), red + "/" + (blue + green), frac(red + 1, total + 1)),
                        "Merah ÷ jumlah = " + red + "/" + total + (ans.equals(red + "/" + total) ? "" : " = " + ans));
            }
            default: {
                int n = 3 + rnd.nextInt(2);
                int[] v = new int[n];
                int sum = 0;
                int mean = r(5, 40);
                for (int i = 0; i < n - 1; i++) { v[i] = mean + r(-8, 8); sum += v[i]; }
                v[n - 1] = mean * n - sum;
                StringBuilder s = new StringBuilder();
                for (int i = 0; i < n; i++) s.append(i == 0 ? "" : ", ").append(v[i]);
                return mathQ(level, "mn" + s, "Purata bagi " + s + " = ?", String.valueOf(mean), near(mean, -1, 1, 2, -2),
                        "Jumlah ÷ bilangan = " + (mean * n) + " ÷ " + n + " = " + mean);
            }
        }
    }
}
