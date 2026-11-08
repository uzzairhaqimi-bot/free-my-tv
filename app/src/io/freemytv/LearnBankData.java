package io.freemytv;

import java.util.List;

/**
 * Fork extra (Kelas Bijak): the written question banks, in this fork's own words. Each Item lists the right answer
 * first. minLevel 1-2 suits ages 7-8, 3-4 ages 9-10, 5-6 ages 11-12, 7-8 ages 13-14.
 * "@spell:BM" / "@spell:EN" items ask which spelling is right (wrong spellings are made from the word).
 */
final class LearnBankData {
    private static final int BM = LearnBank.BM, EN = LearnBank.EN, SA = LearnBank.SAINS;

    private static LearnBank.Item i(int s, int lvl, String prompt, String explain, String... answers) {
        return new LearnBank.Item(s, lvl, prompt, explain, answers);
    }

    static void fill(List<LearnBank.Item> l) {
        // ---------------------------------------------------------------- Bahasa Melayu: makna & sinonim
        String[][] sinonim = {
                {"cantik", "indah", "hodoh", "lambat", "basah"}, {"pandai", "bijak", "malas", "lemah", "pendek"},
                {"besar", "luas", "kecil", "nipis", "rendah"}, {"cepat", "pantas", "perlahan", "berat", "senyap"},
                {"gembira", "riang", "sedih", "marah", "takut"}, {"rajin", "tekun", "malas", "lalai", "leka"},
                {"mula", "awal", "akhir", "tamat", "lepas"}, {"kuat", "gagah", "lemah", "letih", "kecil"},
                {"berani", "gagah berani", "penakut", "pemalu", "sombong"}, {"bersih", "suci", "kotor", "comot", "busuk"},
                {"marah", "berang", "tenang", "gembira", "sabar"}, {"sunyi", "sepi", "bising", "riuh", "ramai"},
                {"lapar", "kebuluran", "kenyang", "puas", "dahaga"}, {"jauh", "terpencil", "dekat", "rapat", "hampir"},
                {"tolong", "bantu", "halang", "tolak", "biar"}, {"gigih", "cekal", "putus asa", "lalai", "malas"},
                {"teliti", "cermat", "cuai", "lalai", "gopoh"}, {"tamak", "haloba", "pemurah", "dermawan", "jujur"},
                {"megah", "bangga", "malu", "segan", "hina"}, {"lazat", "sedap", "tawar", "pahit", "basi"},
        };
        for (String[] s : sinonim) {
            l.add(i(BM, s[0].length() > 6 ? 3 : 1, "Sinonim (maksud sama) bagi \"" + s[0] + "\"?",
                    "\"" + s[0] + "\" sama maksud dengan \"" + s[1] + "\".", s[1], s[2], s[3], s[4]));
            l.add(i(BM, s[0].length() > 6 ? 3 : 2, "Antonim (lawan) bagi \"" + s[0] + "\"?",
                    "Lawan \"" + s[0] + "\" ialah \"" + s[2] + "\".", s[2], s[1], s[3], s[4]));
        }
        // ---------------------------------------------------------------- BM: imbuhan & tatabahasa
        String[][] imbuhan = {
                {"Ibu sedang ___ nasi di dapur.", "menanak", "tanak", "ditanak", "bertanak"},
                {"Adik ___ basikal ke sekolah.", "menunggang", "tunggang", "ditunggang", "penunggang"},
                {"Buku itu ___ oleh Ali semalam.", "dibaca", "membaca", "baca", "pembaca"},
                {"Kami ___ di padang setiap petang.", "bermain", "main", "dimainkan", "permainan"},
                {"Ayah ___ pagar rumah dengan cat biru.", "mengecat", "cat", "dicat", "pengecat"},
                {"Surat itu sudah ___ kepada guru.", "diberikan", "memberi", "beri", "pemberian"},
                {"Pelajar itu ___ soalan dengan yakin.", "menjawab", "jawab", "dijawab", "jawapan"},
                {"Kakak ___ baju baharu untuk hari raya.", "menjahit", "jahit", "dijahit", "penjahit"},
                {"Rumah itu ___ oleh tukang kayu yang mahir.", "dibina", "membina", "bina", "pembina"},
                {"Mereka ___ di hadapan dewan.", "berbaris", "baris", "dibariskan", "barisan"},
        };
        for (String[] s : imbuhan) l.add(i(BM, 3, "Pilih perkataan yang betul: " + s[0], "Jawapan: " + s[1] + ".", s[1], s[2], s[3], s[4]));
        String[][] penjodoh = {
                {"Seekor ___ kucing", "ekor", "buah", "biji", "orang"},
                {"Sebuah ___", "kereta", "telur", "kucing", "guru"},
                {"Sebiji ___", "telur", "rumah", "pokok", "kasut"},
                {"Sepasang ___", "kasut", "kereta", "ikan", "buku"},
                {"Sebatang ___", "pensel", "rumah", "telur", "baju"},
                {"Sehelai ___", "kertas", "kereta", "epal", "kerusi"},
                {"Sekuntum ___", "bunga", "ikan", "pokok", "rumah"},
        };
        l.add(i(BM, 2, "Penjodoh bilangan yang betul: \"Tiga ___ kucing\"", "Haiwan menggunakan \"ekor\".", "ekor", "buah", "biji", "orang"));
        for (int k = 1; k < penjodoh.length; k++) {
            String[] s = penjodoh[k];
            l.add(i(BM, 2, "Padanan yang betul: \"" + s[0] + "\"", "\"" + s[0].replace("___", s[1]) + "\" ialah padanan yang betul.", s[1], s[2], s[3], s[4]));
        }
        // ---------------------------------------------------------------- BM: peribahasa (warisan tradisional)
        String[][] peribahasa = {
                {"bagai aur dengan tebing", "saling membantu", "selalu bergaduh", "sangat kaya", "suka berbohong"},
                {"seperti katak di bawah tempurung", "berfikiran sempit / kurang pengetahuan", "sangat rajin", "suka menolong", "sangat kuat"},
                {"bersatu teguh, bercerai roboh", "perpaduan membawa kekuatan", "bergaduh itu baik", "kerja seorang lebih cepat", "jangan berkawan"},
                {"melentur buluh biarlah dari rebungnya", "mendidik anak sejak kecil", "menebang pokok", "bekerja di kebun", "membina rumah"},
                {"sediakan payung sebelum hujan", "bersedia sebelum sesuatu berlaku", "suka berjalan", "takut hujan", "membeli payung baharu"},
                {"berat sama dipikul, ringan sama dijinjing", "bekerjasama susah dan senang", "angkat barang berat", "bekerja seorang diri", "malas bekerja"},
                {"seperti aur ditarik sungsang", "sesuatu yang sukar dilakukan", "sangat mudah", "sangat cepat", "sangat cantik"},
                {"air dicincang tidak akan putus", "pertalian keluarga sukar diputuskan", "air sungai mengalir", "memotong air", "bergaduh dengan kawan"},
                {"hujan emas di negeri orang, hujan batu di negeri sendiri", "tanah air sendiri tetap lebih baik", "negeri orang lebih kaya", "cuaca tidak menentu", "suka mengembara"},
                {"di mana bumi dipijak, di situ langit dijunjung", "menghormati adat tempat kita berada", "suka memanjat", "tinggal di atas bukit", "melihat langit"},
                {"seperti kacang lupakan kulit", "lupa asal usul / tidak mengenang budi", "suka makan kacang", "sangat berjimat", "sangat bersih"},
                {"sikit-sikit, lama-lama jadi bukit", "usaha sedikit demi sedikit membawa hasil besar", "mendaki bukit", "malas berusaha", "terlalu tamak"},
                {"tak kenal maka tak cinta", "kenali sesuatu dahulu sebelum menilai", "jangan berkawan", "cinta pandang pertama", "lupa nama orang"},
                {"genggam bara api biar sampai jadi arang", "lakukan sesuatu hingga berjaya", "bermain api", "memasak", "takut panas"},
        };
        for (String[] s : peribahasa) l.add(i(BM, 4, "Maksud peribahasa: \"" + s[0] + "\"?", "\"" + s[0] + "\" bermaksud " + s[1] + ".", s[1], s[2], s[3], s[4]));
        String[] ejaanBm = {"sekolah", "perpustakaan", "kebersihan", "tanggungjawab", "kemerdekaan", "pengangkutan", "matahari",
                "kerjasama", "persekitaran", "keselamatan", "pertandingan", "rambutan", "kemudahan", "perayaan", "kebudayaan"};
        for (String w : ejaanBm) l.add(i(BM, w.length() > 10 ? 4 : 1, "@spell:BM", "Ejaan yang betul: " + w + ".", w));
        String[][] bmLanjut = {
                {"Ayat manakah yang menggunakan kata sendi \"di\" dengan betul?", "Dia tinggal di Ipoh.", "Buku itu dibawah meja.", "Saya di beri hadiah.", "Kucing itu di tangkap."},
                {"\"Kerusi itu patah. Kerusi itu perlu dibaiki.\" Gabungan ayat yang terbaik ialah", "Kerusi yang patah itu perlu dibaiki.", "Kerusi itu patah dan kerusi.", "Patah kerusi dibaiki itu.", "Kerusi itu perlu patah."},
                {"Kata ganti nama diri pertama ialah", "saya", "dia", "mereka", "kamu"},
                {"Perkataan manakah kata adjektif?", "tinggi", "berlari", "di", "dan"},
                {"Perkataan manakah kata kerja?", "menulis", "cantik", "pada", "meja"},
                {"Kata hubung dalam ayat \"Saya lapar tetapi tiada makanan.\" ialah", "tetapi", "saya", "lapar", "makanan"},
                {"Bentuk jamak bagi \"budak\" ialah", "budak-budak", "budaks", "berbudak", "kebudakan"},
                {"Ayat pasif bagi \"Ali menendang bola.\" ialah", "Bola ditendang oleh Ali.", "Ali ditendang bola.", "Bola menendang Ali.", "Ali bola tendang."},
        };
        for (String[] s : bmLanjut) l.add(i(BM, 5, s[0], "Jawapan: " + s[1], s[1], s[2], s[3], s[4]));

        // ---------------------------------------------------------------- English: vocabulary
        String[][] enVocab = {
                {"happy", "glad", "angry", "tired", "hungry"}, {"big", "large", "tiny", "thin", "short"},
                {"fast", "quick", "slow", "late", "weak"}, {"smart", "clever", "lazy", "silly", "noisy"},
                {"begin", "start", "finish", "stop", "close"}, {"shout", "yell", "whisper", "smile", "listen"},
                {"brave", "courageous", "afraid", "shy", "rude"}, {"tiny", "minute", "huge", "giant", "wide"},
                {"ancient", "very old", "brand new", "modern", "young"}, {"enormous", "huge", "small", "narrow", "light"},
                {"generous", "willing to give", "selfish", "greedy", "unkind"}, {"curious", "eager to know", "bored", "sleepy", "careless"},
                {"fragile", "easily broken", "very strong", "heavy", "hard"}, {"reluctant", "unwilling", "eager", "ready", "excited"},
                {"abundant", "plentiful", "scarce", "rare", "empty"}, {"persevere", "keep trying", "give up", "forget", "rest"},
        };
        for (int k = 0; k < enVocab.length; k++) {
            String[] s = enVocab[k];
            int lvl = k < 6 ? 1 : k < 10 ? 3 : 5;
            l.add(i(EN, lvl, "Which word means the same as \"" + s[0] + "\"?", "\"" + s[0] + "\" means \"" + s[1] + "\".", s[1], s[2], s[3], s[4]));
            l.add(i(EN, lvl + 1, "What is the opposite of \"" + s[0] + "\"?", "The opposite of \"" + s[0] + "\" is \"" + s[2] + "\".", s[2], s[1], s[3], s[4]));
        }
        // ---------------------------------------------------------------- English: grammar
        String[][] verbs = {   // base, past, wrong past forms
                {"go", "went", "goed", "gone", "going"}, {"eat", "ate", "eated", "eaten", "eating"},
                {"run", "ran", "runned", "runs", "running"}, {"write", "wrote", "writed", "written", "writes"},
                {"swim", "swam", "swimmed", "swum", "swims"}, {"buy", "bought", "buyed", "brought", "buying"},
                {"catch", "caught", "catched", "catches", "catching"}, {"teach", "taught", "teached", "teaches", "tought"},
                {"think", "thought", "thinked", "thinks", "thank"}, {"fly", "flew", "flied", "flown", "flies"},
        };
        for (String[] v : verbs) l.add(i(EN, 3, "Yesterday I ___ (" + v[0] + ").", "The past tense of \"" + v[0] + "\" is \"" + v[1] + "\".", v[1], v[2], v[3], v[4]));
        String[][] plurals = {{"child", "children", "childs", "childes", "childrens"}, {"mouse", "mice", "mouses", "mices", "mousees"},
                {"box", "boxes", "boxs", "boxies", "boxen"}, {"baby", "babies", "babys", "babyes", "babis"},
                {"tooth", "teeth", "tooths", "teeths", "toothes"}, {"leaf", "leaves", "leafs", "leafes", "leavs"},
                {"sheep", "sheep", "sheeps", "sheepes", "shoop"}, {"person", "people", "persons'", "peoples", "personen"}};
        for (String[] s : plurals) l.add(i(EN, 2, "The plural of \"" + s[0] + "\" is", "One " + s[0] + ", many " + s[1] + ".", s[1], s[2], s[3], s[4]));
        String[][] enGrammar = {
                {"She ___ to school every day.", "walks", "walk", "walking", "walked to"},
                {"They ___ playing football now.", "are", "is", "am", "be"},
                {"I have ___ apple.", "an", "a", "the a", "two"},
                {"This is the ___ book in the library.", "best", "goodest", "better", "most good"},
                {"He is ___ than his brother.", "taller", "tallest", "more tall", "tall"},
                {"If it rains, we ___ stay at home.", "will", "would have", "was", "are"},
                {"The cake ___ by my mother yesterday.", "was baked", "baked", "is baking", "bakes"},
                {"Neither Ali nor his friends ___ late.", "were", "was", "is", "be"},
                {"By next year, she ___ here for ten years.", "will have worked", "works", "worked", "is working"},
                {"Choose the correct sentence.", "Its tail is long.", "It's tail is long.", "Its' tail is long.", "It tail is long."},
        };
        for (int k = 0; k < enGrammar.length; k++) {
            String[] s = enGrammar[k];
            l.add(i(EN, k < 4 ? 2 : k < 7 ? 5 : 7, s[0], "Answer: " + s[1], s[1], s[2], s[3], s[4]));
        }
        String[] enSpell = {"because", "friend", "beautiful", "necessary", "library", "February", "separate", "environment",
                "government", "knowledge", "restaurant", "Wednesday", "tomorrow", "different", "interesting"};
        for (String w : enSpell) l.add(i(EN, w.length() > 8 ? 4 : 2, "@spell:EN", "Correct spelling: " + w + ".", w));

        // ---------------------------------------------------------------- Sains & umum
        String[][] sains = {
                {"1", "Haiwan manakah mamalia?", "Kucing", "Ayam", "Ikan keli", "Katak"},
                {"1", "Tumbuhan memerlukan ___ untuk membuat makanan.", "Cahaya matahari", "Bunyi", "Garam", "Plastik"},
                {"1", "Organ untuk bernafas ialah", "Paru-paru", "Perut", "Jantung", "Ginjal"},
                {"1", "Air membeku menjadi ais pada suhu", "0 °C", "100 °C", "50 °C", "10 °C"},
                {"2", "Planet yang paling hampir dengan Matahari ialah", "Utarid", "Bumi", "Marikh", "Musytari"},
                {"2", "Haiwan yang bertelur ialah", "Penyu", "Lembu", "Kambing", "Kucing"},
                {"2", "Bahagian tumbuhan yang menyerap air ialah", "Akar", "Daun", "Bunga", "Buah"},
                {"2", "Bunyi bergerak paling laju melalui", "Pepejal", "Udara", "Vakum", "Gas"},
                {"3", "Air mendidih pada suhu", "100 °C", "0 °C", "50 °C", "212 °C"},
                {"3", "Gas yang kita sedut untuk hidup ialah", "Oksigen", "Karbon dioksida", "Nitrogen", "Helium"},
                {"3", "Planet terbesar dalam Sistem Suria ialah", "Musytari", "Zuhal", "Bumi", "Uranus"},
                {"3", "Magnet menarik objek yang diperbuat daripada", "Besi", "Kayu", "Plastik", "Kaca"},
                {"3", "Fotosintesis berlaku di bahagian", "Daun", "Akar", "Batang", "Bunga"},
                {"4", "Tulang yang melindungi otak ialah", "Tengkorak", "Rusuk", "Tulang belakang", "Pelvis"},
                {"4", "Bulan mengelilingi Bumi kira-kira sekali dalam", "Sebulan", "Sehari", "Setahun", "Seminggu"},
                {"4", "Bumi berputar pada paksinya sekali dalam kira-kira", "24 jam", "12 jam", "7 hari", "365 hari"},
                {"4", "Bahan yang mengalirkan elektrik dengan baik ialah", "Kuprum", "Getah", "Kayu", "Plastik"},
                {"5", "Simbol kimia bagi air ialah", "H₂O", "CO₂", "O₂", "NaCl"},
                {"5", "Unit bagi daya ialah", "Newton", "Joule", "Watt", "Meter"},
                {"5", "Sel darah yang melawan kuman ialah", "Sel darah putih", "Sel darah merah", "Platelet", "Plasma"},
                {"5", "Proses air menjadi wap dipanggil", "Penyejatan", "Kondensasi", "Pembekuan", "Peleburan"},
                {"6", "Bahagian sel yang mengawal aktiviti sel ialah", "Nukleus", "Membran sel", "Sitoplasma", "Vakuol"},
                {"6", "Tenaga yang tersimpan dalam makanan ialah tenaga", "Kimia", "Kinetik", "Bunyi", "Cahaya"},
                {"6", "pH larutan neutral ialah", "7", "1", "14", "0"},
                {"7", "Jumlah kromosom dalam sel badan manusia ialah", "46", "23", "48", "44"},
                {"7", "Laju cahaya kira-kira", "300 000 km/s", "300 km/s", "3 000 km/s", "30 km/s"},
                {"7", "Formula ketumpatan ialah", "Jisim ÷ Isi padu", "Isi padu ÷ Jisim", "Jisim × Isi padu", "Daya ÷ Luas"},
                {"8", "Hukum Ohm menyatakan V = ", "I × R", "I ÷ R", "R ÷ I", "I + R"},
                // umum Malaysia
                {"1", "Ibu negara Malaysia ialah", "Kuala Lumpur", "Putrajaya", "Johor Bahru", "Ipoh"},
                {"2", "Malaysia mencapai kemerdekaan pada tahun", "1957", "1963", "1945", "1965"},
                {"2", "Bilangan negeri di Malaysia ialah", "13", "11", "14", "15"},
                {"3", "Gunung tertinggi di Malaysia ialah", "Gunung Kinabalu", "Gunung Tahan", "Gunung Ledang", "Gunung Jerai"},
                {"3", "Sungai terpanjang di Malaysia ialah", "Sungai Rajang", "Sungai Pahang", "Sungai Perak", "Sungai Kelantan"},
                {"4", "Malaysia dibentuk pada tahun", "1963", "1957", "1965", "1970"},
                {"4", "Negeri paling besar di Malaysia ialah", "Sarawak", "Sabah", "Pahang", "Perak"},
                {"5", "Selat yang memisahkan Semenanjung Malaysia dan Sumatera ialah", "Selat Melaka", "Selat Johor", "Selat Sunda", "Selat Tebrau"},
        };
        for (String[] s : sains) {
            l.add(i(SA, Integer.parseInt(s[0]), s[1], "Jawapan: " + s[2] + ".", s[2], s[3], s[4], s[5]));
        }
    }
}
