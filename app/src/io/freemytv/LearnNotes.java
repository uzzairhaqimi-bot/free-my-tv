package io.freemytv;

/**
 * Fork extra (Kelas Bijak, "Belajar Dulu"): short lesson notes written for this fork. Each topic is a title and a few
 * pages; each page is a heading and a few short lines, with an example.
 */
final class LearnNotes {
    static final class Topic {
        final int subject, level;
        final String title;
        final String[][] pages;   // {heading, body}

        Topic(int subject, int level, String title, String[]... pages) {
            this.subject = subject;
            this.level = level;
            this.title = title;
            this.pages = pages;
        }
    }

    private static final int M = LearnBank.MATH, BM = LearnBank.BM, EN = LearnBank.EN, SA = LearnBank.SAINS;

    private static String[] pg(String h, String b) { return new String[]{h, b}; }

    static final Topic[] TOPICS = {
            // ---------------------------------------------------------------- Matematik
            new Topic(M, 1, "Tambah & Tolak",
                    pg("Tambah = gabung", "Bila kita tambah, kita gabungkan dua kumpulan.\nContoh: 23 + 15\nTambah sa dahulu: 3 + 5 = 8\nKemudian puluh: 20 + 10 = 30\nJawapan: 38"),
                    pg("Tolak = ambil keluar", "Bila kita tolak, kita ambil sebahagian keluar.\nContoh: 47 − 12\nSa: 7 − 2 = 5\nPuluh: 40 − 10 = 30\nJawapan: 35"),
                    pg("Tip pantas", "Tambah 9? Tambah 10, kemudian tolak 1.\n36 + 9 → 36 + 10 = 46 → 46 − 1 = 45")),
            new Topic(M, 2, "Sifir & Darab",
                    pg("Darab = tambah berulang", "3 × 4 bermaksud 4 + 4 + 4 = 12.\nTukar susunan pun sama: 4 × 3 = 12."),
                    pg("Tip sifir", "× 5: jawapan berakhir dengan 0 atau 5.\n× 9: hasil tambah digit jawapan = 9 (9 × 7 = 63 → 6 + 3 = 9).\n× 10: tambah 0 di belakang."),
                    pg("Bahagi = kongsi sama rata", "12 ÷ 3: kongsi 12 kepada 3 orang.\nSetiap orang dapat 4, kerana 3 × 4 = 12.")),
            new Topic(M, 3, "Wang & Masa",
                    pg("Ringgit dan sen", "RM1 = 100 sen.\nRM2.50 + RM1.80:\nSen: 50 + 80 = 130 sen = RM1.30\nRinggit: 2 + 1 = 3\nJumlah: RM4.30"),
                    pg("Membaca masa", "1 jam = 60 minit.\nPukul 2:45 tambah 30 minit:\n45 + 30 = 75 minit = 1 jam 15 minit\nJawapan: 3:15")),
            new Topic(M, 4, "Pecahan",
                    pg("Apa itu pecahan?", "Pecahan ialah sebahagian daripada sesuatu.\n3/4: kek dipotong 4 sama besar, kita ambil 3.\nAtas = pengangka, bawah = penyebut."),
                    pg("Pecahan daripada nombor", "2/5 daripada 20:\nBahagi dengan penyebut: 20 ÷ 5 = 4\nDarab dengan pengangka: 4 × 2 = 8"),
                    pg("Tambah pecahan", "Penyebut sama? Tambah pengangka sahaja.\n1/6 + 3/6 = 4/6 = 2/3 (ringkaskan: bahagi atas dan bawah dengan 2)")),
            new Topic(M, 5, "Perpuluhan & Peratus",
                    pg("Perpuluhan", "0.5 = 5/10 = separuh.\nTambah perpuluhan: susun titik perpuluhan sebaris.\n2.4 + 1.35 = 3.75"),
                    pg("Peratus", "Peratus (%) bermaksud 'daripada 100'.\n25% = 25/100 = 1/4\n20% daripada 150 = 150 × 20 ÷ 100 = 30"),
                    pg("Luas & perimeter", "Perimeter = jumlah semua sisi.\nSegi empat tepat 6 × 4: 2 × (6 + 4) = 20 cm\nLuas = panjang × lebar = 6 × 4 = 24 cm²")),
            new Topic(M, 6, "Tertib Operasi & Nisbah",
                    pg("Mana dulu?", "Kurungan → Darab/Bahagi → Tambah/Tolak.\n5 + 3 × 4 = 5 + 12 = 17 (bukan 32!)"),
                    pg("Nombor negatif", "Di bawah sifar ada nombor negatif: −1, −2, −3…\n−3 + 5 = 2\n4 − 7 = −3\nTolak negatif = tambah: 5 − (−2) = 7"),
                    pg("Nisbah", "Ali : Abu = 2 : 3, jumlah guli 25.\nJumlah bahagian = 2 + 3 = 5\nSatu bahagian = 25 ÷ 5 = 5\nAli = 2 × 5 = 10")),
            new Topic(M, 7, "Algebra",
                    pg("Apa itu x?", "x ialah nombor yang belum kita tahu.\nKita cari x dengan 'membuka' langkah satu persatu."),
                    pg("Selesaikan persamaan", "3x + 4 = 19\nTolak 4 di kedua-dua belah: 3x = 15\nBahagi dengan 3: x = 5\nSemak: 3 × 5 + 4 = 19 ✓"),
                    pg("Sudut segi tiga", "Ketiga-tiga sudut segi tiga berjumlah 180°.\n60° dan 70° → sudut ketiga = 180 − 60 − 70 = 50°")),
            new Topic(M, 8, "Kuasa, Purata & Kebarangkalian",
                    pg("Kuasa", "2³ = 2 × 2 × 2 = 8\n5² = 25 (kuasa dua)\n√49 = 7 kerana 7 × 7 = 49"),
                    pg("Purata", "Purata = jumlah ÷ bilangan.\n4, 6, 8 → (4 + 6 + 8) ÷ 3 = 6"),
                    pg("Kebarangkalian", "Kebarangkalian = cara yang kita mahu ÷ semua cara.\n3 merah, 2 biru: dapat merah = 3/5")),
            // ---------------------------------------------------------------- Bahasa Melayu
            new Topic(BM, 1, "Sinonim & Antonim",
                    pg("Sinonim", "Sinonim = perkataan yang sama maksud.\ncantik → indah\npandai → bijak\ncepat → pantas"),
                    pg("Antonim", "Antonim = perkataan berlawanan maksud.\nbesar ↔ kecil\nrajin ↔ malas\nbersih ↔ kotor")),
            new Topic(BM, 2, "Penjodoh Bilangan",
                    pg("Untuk benda apa?", "seekor → haiwan (seekor kucing)\nsebuah → benda besar (sebuah kereta)\nsebiji → benda kecil bulat (sebiji telur)\nsehelai → benda nipis (sehelai kertas)\nsebatang → benda panjang (sebatang pensel)\nsekuntum → bunga (sekuntum bunga)")),
            new Topic(BM, 3, "Imbuhan meN- dan di-",
                    pg("Imbuhan meN-", "meN- untuk ayat aktif (pelaku buat).\nmeN- + tulis → menulis\nmeN- + baca → membaca\nmeN- + sapu → menyapu (s → ny)"),
                    pg("Imbuhan di-", "di- untuk ayat pasif (benda dibuat).\nAli menendang bola → Bola ditendang oleh Ali.\nIngat: imbuhan di- ditulis rapat: dibaca, ditulis."),
                    pg("Kata sendi 'di'", "'di' sebagai kata sendi ditulis jarak, sebelum tempat.\ndi rumah, di bawah meja, di Ipoh")),
            new Topic(BM, 4, "Peribahasa",
                    pg("Apa itu peribahasa?", "Ayat ringkas yang membawa nasihat atau maksud tersirat."),
                    pg("Contoh", "bagai aur dengan tebing → saling membantu\nsediakan payung sebelum hujan → bersedia awal\nsikit-sikit, lama-lama jadi bukit → usaha kecil yang berterusan"),
                    pg("Tip", "Bayangkan gambarnya. Aur (buluh) melindungi tebing, tebing menyokong aur: saling membantu!")),
            new Topic(BM, 5, "Golongan Kata & Ayat",
                    pg("Golongan kata", "Kata nama: meja, Ali, sekolah\nKata kerja: makan, menulis, berlari\nKata adjektif: tinggi, cantik, merah\nKata hubung: dan, tetapi, kerana"),
                    pg("Ayat aktif & pasif", "Aktif: Kakak menjahit baju.\nPasif: Baju dijahit oleh kakak.")),
            // ---------------------------------------------------------------- English
            new Topic(EN, 1, "Nouns, Verbs, Adjectives",
                    pg("Nouns", "A noun is a person, place or thing.\nteacher, Kuala Lumpur, apple"),
                    pg("Verbs", "A verb is an action word.\nrun, eat, write, sing"),
                    pg("Adjectives", "An adjective describes a noun.\na big house, a happy girl, a red ball")),
            new Topic(EN, 2, "Plurals",
                    pg("Most words: add -s", "book → books, cat → cats"),
                    pg("-s, -x, -ch, -sh: add -es", "box → boxes, bus → buses, watch → watches"),
                    pg("Special plurals", "child → children\nmouse → mice\ntooth → teeth\nsheep → sheep\nbaby → babies (y → ies)")),
            new Topic(EN, 3, "Past Tense",
                    pg("Regular verbs: add -ed", "play → played, walk → walked, jump → jumped"),
                    pg("Irregular verbs", "go → went\neat → ate\nrun → ran\nbuy → bought\nthink → thought\nThese must be remembered!")),
            new Topic(EN, 5, "Grammar Tips",
                    pg("He/She/It + s", "Present simple: She walks. He plays. It rains.\nI/You/We/They walk."),
                    pg("Comparing", "tall → taller → tallest\ngood → better → best\nbeautiful → more beautiful → most beautiful"),
                    pg("Its or It's?", "it's = it is (It's raining.)\nits = belongs to it (The cat licks its paw.)")),
            // ---------------------------------------------------------------- Sains & Umum
            new Topic(SA, 1, "Benda Hidup",
                    pg("Ciri benda hidup", "Bernafas, makan, membesar, bergerak dan membiak."),
                    pg("Haiwan", "Mamalia menyusukan anak (kucing, lembu).\nHaiwan bertelur: ayam, penyu, ikan.")),
            new Topic(SA, 2, "Tumbuhan",
                    pg("Bahagian tumbuhan", "Akar: serap air\nBatang: sokong dan salurkan air\nDaun: buat makanan"),
                    pg("Fotosintesis", "Daun guna cahaya matahari, air dan karbon dioksida untuk membuat makanan (glukosa) dan membebaskan oksigen.")),
            new Topic(SA, 3, "Sistem Suria",
                    pg("Matahari & planet", "Lapan planet mengelilingi Matahari:\nUtarid, Zuhrah, Bumi, Marikh, Musytari, Zuhal, Uranus, Neptun."),
                    pg("Bumi & Bulan", "Bumi berputar sekali dalam ~24 jam → siang dan malam.\nBumi mengelilingi Matahari dalam ~365 hari.\nBulan mengelilingi Bumi lebih kurang sebulan.")),
            new Topic(SA, 4, "Jirim & Air",
                    pg("Tiga keadaan jirim", "Pepejal (ais), cecair (air), gas (wap)."),
                    pg("Perubahan", "Membeku: cecair → pepejal (0 °C)\nMendidih: cecair → gas (100 °C)\nPenyejatan: air menjadi wap perlahan-lahan")),
            new Topic(SA, 6, "Sel & Badan",
                    pg("Sel", "Semua benda hidup terdiri daripada sel.\nNukleus mengawal aktiviti sel."),
                    pg("Darah", "Sel darah merah membawa oksigen.\nSel darah putih melawan kuman.\nPlatelet membantu darah membeku.")),
            new Topic(SA, 7, "Daya & Elektrik",
                    pg("Daya", "Daya ialah tolakan atau tarikan, diukur dalam Newton (N)."),
                    pg("Elektrik", "Konduktor (kuprum) mengalirkan elektrik; penebat (getah, plastik) tidak.\nHukum Ohm: V = I × R")),
    };
}
