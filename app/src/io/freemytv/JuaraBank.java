package io.freemytv;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Fork extra: general knowledge for Siapa Juara? (and the "Sains & Umum" bank of Kelas Bijak). Each row is
 * {category, question, right answer, three wrong answers}. Only facts that don't change with time. Questions asked
 * recently are remembered across games so a new game brings different ones.
 */
final class JuaraBank {
    private JuaraBank() {}

    static final String[][] Q = {
            // ---------------------------------------------------------------- Malaysia
            {"Malaysia", "Apakah ibu negara Malaysia?", "Kuala Lumpur", "Putrajaya", "Johor Bahru", "Ipoh"},
            {"Malaysia", "Di manakah pusat pentadbiran kerajaan persekutuan?", "Putrajaya", "Kuala Lumpur", "Shah Alam", "Cyberjaya"},
            {"Malaysia", "Berapakah bilangan negeri di Malaysia?", "13", "11", "12", "14"},
            {"Malaysia", "Bilakah Hari Kebangsaan Malaysia?", "31 Ogos", "16 September", "1 Mei", "31 Disember"},
            {"Malaysia", "Hari Malaysia disambut pada…", "16 September", "31 Ogos", "1 Januari", "9 September"},
            {"Malaysia", "Pada tahun berapakah Persekutuan Tanah Melayu merdeka?", "1957", "1963", "1945", "1965"},
            {"Malaysia", "Malaysia dibentuk pada tahun…", "1963", "1957", "1965", "1971"},
            {"Malaysia", "Siapakah Perdana Menteri Malaysia yang pertama?", "Tunku Abdul Rahman", "Tun Abdul Razak", "Tun Hussein Onn", "Tun Dr. Mahathir"},
            {"Malaysia", "Apakah bunga kebangsaan Malaysia?", "Bunga raya", "Melur", "Orkid", "Teratai"},
            {"Malaysia", "Gunung manakah yang tertinggi di Malaysia?", "Gunung Kinabalu", "Gunung Tahan", "Gunung Ledang", "Gunung Jerai"},
            {"Malaysia", "Sungai manakah yang terpanjang di Malaysia?", "Sungai Rajang", "Sungai Pahang", "Sungai Perak", "Sungai Kelantan"},
            {"Malaysia", "Negeri manakah yang terbesar di Malaysia?", "Sarawak", "Sabah", "Pahang", "Perak"},
            {"Malaysia", "Negeri manakah yang terkecil di Malaysia?", "Perlis", "Melaka", "Pulau Pinang", "Negeri Sembilan"},
            {"Malaysia", "Apakah ibu negeri Sabah?", "Kota Kinabalu", "Sandakan", "Tawau", "Lahad Datu"},
            {"Malaysia", "Apakah ibu negeri Sarawak?", "Kuching", "Miri", "Sibu", "Bintulu"},
            {"Malaysia", "Apakah ibu negeri Kelantan?", "Kota Bharu", "Kuala Terengganu", "Pasir Mas", "Tumpat"},
            {"Malaysia", "Apakah ibu negeri Terengganu?", "Kuala Terengganu", "Kemaman", "Dungun", "Kota Bharu"},
            {"Malaysia", "Apakah ibu negeri Pahang?", "Kuantan", "Temerloh", "Bentong", "Raub"},
            {"Malaysia", "Apakah ibu negeri Kedah?", "Alor Setar", "Sungai Petani", "Kulim", "Kangar"},
            {"Malaysia", "Apakah ibu negeri Perak?", "Ipoh", "Taiping", "Teluk Intan", "Kuala Kangsar"},
            {"Malaysia", "Apakah ibu negeri Selangor?", "Shah Alam", "Petaling Jaya", "Klang", "Kuala Lumpur"},
            {"Malaysia", "Apakah ibu negeri Johor?", "Johor Bahru", "Muar", "Batu Pahat", "Kluang"},
            {"Malaysia", "Apakah ibu negeri Negeri Sembilan?", "Seremban", "Port Dickson", "Nilai", "Kuala Pilah"},
            {"Malaysia", "Apakah ibu negeri Perlis?", "Kangar", "Arau", "Padang Besar", "Alor Setar"},
            {"Malaysia", "Apakah ibu negeri Pulau Pinang?", "George Town", "Butterworth", "Bayan Lepas", "Bukit Mertajam"},
            {"Malaysia", "Apakah mata wang Malaysia?", "Ringgit", "Rupiah", "Baht", "Peso"},
            {"Malaysia", "Apakah tajuk lagu kebangsaan Malaysia?", "Negaraku", "Jalur Gemilang", "Tanah Pusaka", "Malaysia Berjaya"},
            {"Malaysia", "Bendera Malaysia dikenali sebagai…", "Jalur Gemilang", "Sang Saka Merah Putih", "Bintang Timur", "Panji Negara"},
            {"Malaysia", "Berapakah jalur merah dan putih pada Jalur Gemilang?", "14", "13", "12", "15"},
            {"Malaysia", "Bintang pada Jalur Gemilang mempunyai berapa bucu?", "14", "13", "5", "8"},
            {"Malaysia", "Haiwan manakah yang terdapat pada Jata Negara?", "Harimau", "Gajah", "Seladang", "Badak"},
            {"Malaysia", "Apakah bangunan tertinggi di Malaysia?", "Merdeka 118", "Menara Berkembar Petronas", "Menara Kuala Lumpur", "The Exchange 106"},
            {"Malaysia", "Pulau manakah yang dikenali sebagai Pulau Lagenda?", "Langkawi", "Tioman", "Redang", "Pangkor"},
            {"Malaysia", "Selat yang memisahkan Semenanjung Malaysia dengan Sumatera ialah…", "Selat Melaka", "Selat Johor", "Selat Sunda", "Selat Tebrau"},
            {"Malaysia", "Pulau Borneo dikongsi oleh Malaysia, Indonesia dan…", "Brunei", "Singapura", "Filipina", "Thailand"},
            {"Malaysia", "Negara manakah berjiran dengan Semenanjung Malaysia di utara?", "Thailand", "Indonesia", "Vietnam", "Myanmar"},
            {"Malaysia", "Siapakah laksamana terkenal Kesultanan Melayu Melaka?", "Hang Tuah", "Mat Kilau", "Tok Janggut", "Dol Said"},
            {"Malaysia", "Siapakah pengasas Kesultanan Melayu Melaka?", "Parameswara", "Hang Tuah", "Sultan Mansur Shah", "Tun Perak"},
            {"Malaysia", "Melaka jatuh ke tangan Portugis pada tahun…", "1511", "1641", "1824", "1400"},
            {"Malaysia", "Hari Guru di Malaysia disambut pada…", "16 Mei", "1 Mei", "31 Ogos", "16 September"},
            {"Malaysia", "Negeri manakah digelar 'Negeri di Bawah Bayu'?", "Sabah", "Sarawak", "Terengganu", "Kelantan"},
            {"Malaysia", "Negeri manakah digelar 'Bumi Kenyalang'?", "Sarawak", "Sabah", "Pahang", "Johor"},
            {"Malaysia", "Negeri manakah digelar 'Pulau Mutiara'?", "Pulau Pinang", "Langkawi", "Labuan", "Melaka"},
            {"Malaysia", "Gelaran 'Darul Takzim' milik negeri…", "Johor", "Kedah", "Perak", "Pahang"},
            {"Malaysia", "Gelaran 'Darul Naim' milik negeri…", "Kelantan", "Terengganu", "Kedah", "Selangor"},
            {"Malaysia", "Gelaran 'Darul Aman' milik negeri…", "Kedah", "Perlis", "Kelantan", "Johor"},
            {"Malaysia", "Gelaran 'Darul Iman' milik negeri…", "Terengganu", "Kelantan", "Pahang", "Perak"},
            {"Malaysia", "Gelaran 'Darul Makmur' milik negeri…", "Pahang", "Selangor", "Johor", "Kedah"},
            {"Malaysia", "Gelaran 'Darul Ehsan' milik negeri…", "Selangor", "Perak", "Pahang", "Melaka"},
            {"Malaysia", "Gelaran 'Darul Ridzuan' milik negeri…", "Perak", "Kedah", "Johor", "Terengganu"},
            {"Malaysia", "Gelaran 'Darul Khusus' milik negeri…", "Negeri Sembilan", "Melaka", "Perlis", "Selangor"},
            {"Malaysia", "Gelaran 'Indera Kayangan' milik negeri…", "Perlis", "Kedah", "Pulau Pinang", "Perak"},
            {"Malaysia", "Burung kenyalang ialah nama lain bagi burung…", "Enggang", "Helang", "Merak", "Tiong"},
            {"Malaysia", "Siapakah angkasawan pertama Malaysia?", "Sheikh Muszaphar Shukor", "Faiz Khaleed", "Nicol David", "Lee Chong Wei"},

            // ---------------------------------------------------------------- Budaya
            {"Budaya", "Makanan manakah dianggap hidangan kebangsaan Malaysia?", "Nasi lemak", "Sushi", "Pizza", "Nasi goreng pattaya"},
            {"Budaya", "Tarian buluh dari Sabah dipanggil…", "Magunatip", "Zapin", "Joget", "Kuda Kepang"},
            {"Budaya", "Alat muzik yang dipalu, bulat dan diperbuat daripada kulit kambing ialah…", "Kompang", "Gambus", "Seruling", "Sape"},
            {"Budaya", "Alat muzik bertali tradisional orang Ulu di Sarawak ialah…", "Sape", "Kompang", "Gendang", "Serunai"},
            {"Budaya", "Permainan tradisional dengan papan berlubang dan biji guli ialah…", "Congkak", "Gasing", "Batu Seremban", "Galah Panjang"},
            {"Budaya", "Wau ialah sejenis…", "Layang-layang", "Gasing", "Kuih", "Perahu"},
            {"Budaya", "Wau bulan sangat terkenal di negeri…", "Kelantan", "Johor", "Perak", "Selangor"},
            {"Budaya", "Kuih bulat hijau berisi gula melaka dan bersalut kelapa ialah…", "Onde-onde", "Kuih lapis", "Karipap", "Apam balik"},
            {"Budaya", "Hari Gawai disambut di negeri…", "Sarawak", "Sabah", "Kelantan", "Perlis"},
            {"Budaya", "Pesta Kaamatan disambut di negeri…", "Sabah", "Sarawak", "Pahang", "Johor"},
            {"Budaya", "Deepavali juga dikenali sebagai Pesta…", "Cahaya", "Bunga", "Air", "Tanglung"},
            {"Budaya", "Umat Islam berpuasa pada bulan…", "Ramadan", "Syawal", "Muharam", "Rejab"},
            {"Budaya", "Hari Raya Aidilfitri jatuh pada 1 haribulan…", "Syawal", "Ramadan", "Zulhijah", "Safar"},
            {"Budaya", "Bahan utama tempe ialah…", "Kacang soya", "Kacang tanah", "Beras", "Jagung"},
            {"Budaya", "Buah manakah digelar 'Raja Buah'?", "Durian", "Manggis", "Rambutan", "Nangka"},
            {"Budaya", "Buah manakah digelar 'Ratu Buah'?", "Manggis", "Durian", "Cempedak", "Langsat"},
            {"Budaya", "Gasing dimainkan dengan cara…", "Dipusingkan dengan tali", "Ditendang", "Diterbangkan", "Dibaling ke kolam"},
            {"Budaya", "Silat ialah seni…", "Mempertahankan diri", "Melukis", "Menari", "Memasak"},
            {"Budaya", "Pakaian tradisional lelaki Melayu ialah…", "Baju Melayu", "Cheongsam", "Sari", "Kimono"},
            {"Budaya", "Kimono ialah pakaian tradisional dari negara…", "Jepun", "China", "Korea", "India"},

            // ---------------------------------------------------------------- Dunia
            {"Dunia", "Apakah ibu negara Jepun?", "Tokyo", "Osaka", "Kyoto", "Seoul"},
            {"Dunia", "Apakah ibu negara Thailand?", "Bangkok", "Phuket", "Chiang Mai", "Hanoi"},
            {"Dunia", "Apakah ibu negara Perancis?", "Paris", "Lyon", "Rom", "Madrid"},
            {"Dunia", "Apakah ibu negara United Kingdom?", "London", "Manchester", "Dublin", "Edinburgh"},
            {"Dunia", "Apakah ibu negara Australia?", "Canberra", "Sydney", "Melbourne", "Perth"},
            {"Dunia", "Apakah ibu negara Mesir?", "Kaherah", "Iskandariah", "Riyadh", "Amman"},
            {"Dunia", "Apakah ibu negara Arab Saudi?", "Riyadh", "Makkah", "Jeddah", "Madinah"},
            {"Dunia", "Apakah ibu negara China?", "Beijing", "Shanghai", "Hong Kong", "Guangzhou"},
            {"Dunia", "Apakah ibu negara Korea Selatan?", "Seoul", "Busan", "Pyongyang", "Tokyo"},
            {"Dunia", "Apakah ibu negara Vietnam?", "Hanoi", "Ho Chi Minh", "Da Nang", "Phnom Penh"},
            {"Dunia", "Apakah ibu negara Filipina?", "Manila", "Cebu", "Davao", "Jakarta"},
            {"Dunia", "Apakah ibu negara Brunei?", "Bandar Seri Begawan", "Kuala Belait", "Tutong", "Miri"},
            {"Dunia", "Apakah ibu negara Kanada?", "Ottawa", "Toronto", "Vancouver", "Montreal"},
            {"Dunia", "Apakah ibu negara Jerman?", "Berlin", "Munich", "Frankfurt", "Hamburg"},
            {"Dunia", "Apakah ibu negara Sepanyol?", "Madrid", "Barcelona", "Seville", "Lisbon"},
            {"Dunia", "Apakah ibu negara Turki?", "Ankara", "Istanbul", "Izmir", "Bursa"},
            {"Dunia", "Apakah ibu negara Itali?", "Rom", "Milan", "Venice", "Napoli"},
            {"Dunia", "Apakah mata wang Jepun?", "Yen", "Won", "Yuan", "Ringgit"},
            {"Dunia", "Apakah mata wang Thailand?", "Baht", "Rupiah", "Dong", "Peso"},
            {"Dunia", "Apakah mata wang Indonesia?", "Rupiah", "Ringgit", "Baht", "Rupee"},
            {"Dunia", "Benua manakah yang terbesar?", "Asia", "Afrika", "Eropah", "Amerika Utara"},
            {"Dunia", "Lautan manakah yang terbesar?", "Lautan Pasifik", "Lautan Atlantik", "Lautan Hindi", "Lautan Artik"},
            {"Dunia", "Sungai yang paling panjang di dunia ialah…", "Sungai Nil", "Sungai Mekong", "Sungai Thames", "Sungai Yangtze"},
            {"Dunia", "Gunung tertinggi di dunia ialah…", "Gunung Everest", "Gunung Kinabalu", "Gunung Fuji", "Gunung Kilimanjaro"},
            {"Dunia", "Gurun panas yang terbesar di dunia ialah…", "Sahara", "Gobi", "Kalahari", "Arab"},
            {"Dunia", "Menara Eiffel terletak di bandar…", "Paris", "London", "Rom", "New York"},
            {"Dunia", "Piramid Giza terletak di negara…", "Mesir", "Mexico", "Iraq", "Maghribi"},
            {"Dunia", "Tembok Besar terletak di negara…", "China", "Jepun", "India", "Mongolia"},
            {"Dunia", "Taj Mahal terletak di negara…", "India", "Pakistan", "Turki", "Iran"},
            {"Dunia", "Patung Liberty terletak di negara…", "Amerika Syarikat", "Perancis", "Kanada", "Australia"},
            {"Dunia", "Benua manakah yang paling sejuk?", "Antartika", "Eropah", "Asia", "Amerika Utara"},
            {"Dunia", "Berapakah bilangan benua di dunia?", "7", "5", "6", "8"},
            {"Dunia", "Berapakah bilangan lautan di dunia?", "5", "3", "4", "7"},
            {"Dunia", "Negara manakah yang berbentuk seperti kasut but?", "Itali", "Sepanyol", "Greece", "Chile"},
            {"Dunia", "Negara manakah yang paling luas di dunia?", "Rusia", "Kanada", "China", "Amerika Syarikat"},
            {"Dunia", "Bahasa rasmi negara Brazil ialah…", "Portugis", "Sepanyol", "Inggeris", "Perancis"},
            {"Dunia", "Pizza berasal dari negara…", "Itali", "Amerika Syarikat", "Perancis", "Greece"},
            {"Dunia", "Sushi berasal dari negara…", "Jepun", "China", "Korea", "Thailand"},
            {"Dunia", "Kaabah terletak di bandar…", "Makkah", "Madinah", "Riyadh", "Jeddah"},
            {"Dunia", "Kanggaru ialah haiwan asli negara…", "Australia", "Afrika Selatan", "India", "Brazil"},

            // ---------------------------------------------------------------- Sains
            {"Sains", "Planet manakah yang paling dekat dengan Matahari?", "Utarid", "Zuhrah", "Bumi", "Marikh"},
            {"Sains", "Planet manakah yang paling besar?", "Musytari", "Zuhal", "Bumi", "Neptun"},
            {"Sains", "Planet manakah digelar 'Planet Merah'?", "Marikh", "Zuhrah", "Musytari", "Utarid"},
            {"Sains", "Planet manakah terkenal dengan cincinnya yang cantik?", "Zuhal", "Marikh", "Bumi", "Utarid"},
            {"Sains", "Berapakah bilangan planet dalam Sistem Suria?", "8", "7", "9", "10"},
            {"Sains", "Apakah satelit semula jadi Bumi?", "Bulan", "Matahari", "Marikh", "Bintang Kejora"},
            {"Sains", "Gas apakah yang kita perlukan untuk bernafas?", "Oksigen", "Karbon dioksida", "Nitrogen", "Helium"},
            {"Sains", "Gas apakah yang kita hembus keluar?", "Karbon dioksida", "Oksigen", "Hidrogen", "Neon"},
            {"Sains", "Air tulen mendidih pada suhu…", "100 °C", "50 °C", "0 °C", "200 °C"},
            {"Sains", "Air membeku menjadi ais pada suhu…", "0 °C", "10 °C", "100 °C", "−50 °C"},
            {"Sains", "Apakah formula kimia air?", "H₂O", "CO₂", "O₂", "NaCl"},
            {"Sains", "Organ manakah yang mengepam darah ke seluruh badan?", "Jantung", "Paru-paru", "Hati", "Perut"},
            {"Sains", "Berapakah bilangan tulang dalam badan orang dewasa?", "206", "106", "306", "156"},
            {"Sains", "Organ manakah yang paling besar dalam badan manusia?", "Kulit", "Hati", "Otak", "Usus"},
            {"Sains", "Bahagian tumbuhan manakah yang membuat makanan?", "Daun", "Akar", "Bunga", "Batang"},
            {"Sains", "Proses tumbuhan membuat makanan dipanggil…", "Fotosintesis", "Respirasi", "Penyejatan", "Pendebungaan"},
            {"Sains", "Labah-labah mempunyai berapa kaki?", "8", "6", "10", "4"},
            {"Sains", "Serangga mempunyai berapa kaki?", "6", "8", "4", "10"},
            {"Sains", "Ikan bernafas menggunakan…", "Insang", "Paru-paru", "Kulit", "Hidung"},
            {"Sains", "Anak katak dipanggil…", "Berudu", "Anak ikan", "Ulat", "Kepompong"},
            {"Sains", "Mengapa kita nampak kilat dahulu sebelum dengar guruh?", "Cahaya lebih laju daripada bunyi", "Mata lebih laju daripada telinga", "Guruh datang dari jauh", "Kilat lebih besar"},
            {"Sains", "Magnet boleh menarik…", "Paku besi", "Pemadam getah", "Kayu", "Kertas"},
            {"Sains", "Pelangi mempunyai berapa warna?", "7", "5", "6", "9"},
            {"Sains", "Warna biru dicampur warna kuning menjadi…", "Hijau", "Ungu", "Oren", "Coklat"},
            {"Sains", "Warna merah dicampur warna kuning menjadi…", "Oren", "Hijau", "Ungu", "Merah jambu"},
            {"Sains", "Alat untuk mengukur suhu badan ialah…", "Termometer", "Pembaris", "Penimbang", "Kompas"},
            {"Sains", "Alat untuk melihat benda yang sangat kecil ialah…", "Mikroskop", "Teleskop", "Periskop", "Kanta mata"},
            {"Sains", "Alat untuk melihat bintang dan planet ialah…", "Teleskop", "Mikroskop", "Kamera", "Kompas"},
            {"Sains", "Bumi berputar sekali pada paksinya dalam masa lebih kurang…", "24 jam", "12 jam", "7 hari", "30 hari"},
            {"Sains", "Bumi mengelilingi Matahari sekali dalam masa lebih kurang…", "365 hari", "30 hari", "24 jam", "100 hari"},
            {"Sains", "Tahun lompat mempunyai berapa hari?", "366", "365", "360", "367"},
            {"Sains", "Bintang yang paling dekat dengan Bumi ialah…", "Matahari", "Bulan", "Bintang Kejora", "Marikh"},
            {"Sains", "Siapakah manusia pertama menjejakkan kaki di Bulan?", "Neil Armstrong", "Yuri Gagarin", "Buzz Aldrin", "Isaac Newton"},
            {"Sains", "Siapakah yang terkenal dengan teori graviti selepas melihat epal jatuh?", "Isaac Newton", "Albert Einstein", "Thomas Edison", "Galileo"},
            {"Sains", "Tenaga daripada cahaya Matahari dipanggil tenaga…", "Suria", "Angin", "Nuklear", "Kimia"},
            {"Sains", "Kulit kita menghasilkan vitamin apabila terkena cahaya matahari. Vitamin apa?", "Vitamin D", "Vitamin C", "Vitamin A", "Vitamin K"},
            {"Sains", "Buah oren kaya dengan…", "Vitamin C", "Vitamin D", "Lemak", "Garam"},
            {"Sains", "Kanak-kanak mempunyai berapa batang gigi susu?", "20", "32", "10", "28"},
            {"Sains", "Bunyi tidak boleh bergerak di dalam…", "Vakum (ruang kosong)", "Air", "Udara", "Besi"},
            {"Sains", "Bahan manakah konduktor elektrik yang baik?", "Kuprum", "Plastik", "Getah", "Kayu"},
            {"Sains", "Tiga keadaan jirim ialah pepejal, cecair dan…", "Gas", "Ais", "Api", "Tanah"},
            {"Sains", "Bahagian mata yang berwarna dipanggil…", "Iris", "Kanta", "Retina", "Bulu mata"},

            // ---------------------------------------------------------------- Haiwan
            {"Haiwan", "Haiwan manakah yang paling besar di dunia?", "Paus biru", "Gajah", "Jerung paus", "Zirafah"},
            {"Haiwan", "Haiwan darat manakah yang paling besar?", "Gajah", "Badak sumbu", "Kuda nil", "Zirafah"},
            {"Haiwan", "Haiwan darat manakah yang paling laju?", "Cheetah", "Kuda", "Singa", "Kanggaru"},
            {"Haiwan", "Haiwan manakah yang paling tinggi?", "Zirafah", "Gajah", "Unta", "Kuda"},
            {"Haiwan", "Burung terbesar yang tidak boleh terbang ialah…", "Burung unta", "Penguin", "Ayam", "Merak"},
            {"Haiwan", "Haiwan yang boleh menukar warna kulitnya ialah…", "Sesumpah", "Kucing", "Katak", "Arnab"},
            {"Haiwan", "Satu-satunya mamalia yang boleh terbang ialah…", "Kelawar", "Burung hantu", "Tupai", "Rama-rama"},
            {"Haiwan", "Bonggol unta sebenarnya menyimpan…", "Lemak", "Air", "Pasir", "Udara"},
            {"Haiwan", "Lebah menghasilkan…", "Madu", "Susu", "Sutera", "Lilin sahaja"},
            {"Haiwan", "Ulat sutera menghasilkan…", "Sutera", "Madu", "Kapas", "Bulu"},
            {"Haiwan", "Haiwan manakah membawa anaknya dalam kantung?", "Kanggaru", "Arnab", "Kucing", "Monyet"},
            {"Haiwan", "Tapir Malaysia berwarna…", "Hitam dan putih", "Coklat", "Kelabu", "Oren berjalur"},
            {"Haiwan", "Makanan utama panda ialah…", "Buluh", "Ikan", "Daging", "Pisang"},
            {"Haiwan", "Burung hantu aktif mencari makanan pada waktu…", "Malam", "Pagi", "Tengah hari", "Petang"},
            {"Haiwan", "Penyu belimbing terkenal bertelur di pantai negeri…", "Terengganu", "Perlis", "Selangor", "Negeri Sembilan"},
            {"Haiwan", "Haiwan yang digelar 'Raja Rimba' ialah…", "Singa", "Harimau", "Gajah", "Beruang"},
            {"Haiwan", "Monyet berhidung panjang yang terdapat di Borneo ialah…", "Monyet belanda", "Orang utan", "Beruk", "Siamang"},
            {"Haiwan", "Kancil juga dikenali dalam bahasa Inggeris sebagai…", "Mouse-deer", "Squirrel", "Rabbit", "Goat"},
            {"Haiwan", "Haiwan manakah bertelur?", "Penyu", "Kucing", "Lembu", "Kambing"},
            {"Haiwan", "Haiwan yang bernafas melalui kulit dan paru-paru ialah…", "Katak", "Ikan", "Ayam", "Lembu"},
            {"Haiwan", "Ikan manakah sebenarnya mamalia?", "Ikan paus", "Ikan jerung", "Ikan tuna", "Ikan keli"},
            {"Haiwan", "Gajah menggunakan belalainya untuk…", "Minum dan mengambil makanan", "Melihat", "Mendengar", "Terbang"},

            // ---------------------------------------------------------------- Sukan
            {"Sukan", "Berapakah pemain satu pasukan bola sepak di padang?", "11", "9", "7", "12"},
            {"Sukan", "Datuk Lee Chong Wei terkenal dalam sukan…", "Badminton", "Bola sepak", "Skuasy", "Renang"},
            {"Sukan", "Datuk Nicol David terkenal dalam sukan…", "Skuasy", "Tenis", "Badminton", "Golf"},
            {"Sukan", "Pasukan bola sepak kebangsaan Malaysia digelar…", "Harimau Malaya", "Garuda", "Gajah Putih", "Singa"},
            {"Sukan", "Sukan Olimpik diadakan setiap…", "4 tahun", "2 tahun", "1 tahun", "5 tahun"},
            {"Sukan", "Berapakah gelang pada logo Olimpik?", "5", "4", "6", "7"},
            {"Sukan", "Pingat untuk johan (tempat pertama) ialah pingat…", "Emas", "Perak", "Gangsa", "Besi"},
            {"Sukan", "Berapakah pemain satu pasukan bola keranjang di gelanggang?", "5", "6", "7", "11"},
            {"Sukan", "Larian maraton sejauh lebih kurang…", "42 km", "10 km", "21 km", "100 km"},
            {"Sukan", "Datuk Azizulhasni Awang terkenal dalam sukan…", "Berbasikal", "Memanah", "Renang", "Lompat tinggi"},
            {"Sukan", "Pandelela Rinong terkenal dalam sukan…", "Terjun", "Gimnastik", "Larian", "Boling"},
            {"Sukan", "Sukan sepak takraw dimainkan tanpa menggunakan…", "Tangan", "Kaki", "Kepala", "Lutut"},
            {"Sukan", "Dalam badminton, pukulan pertama dipanggil…", "Servis", "Smesy", "Gol", "Lob"},
            {"Sukan", "Sukan manakah menggunakan bulu tangkis?", "Badminton", "Tenis", "Ping pong", "Hoki"},
            {"Sukan", "Dalam bola sepak, pemain yang boleh memegang bola dengan tangan ialah…", "Penjaga gol", "Penyerang", "Pertahanan", "Pengadil"},
            {"Sukan", "Piala Thomas ialah kejohanan berpasukan sukan…", "Badminton", "Bola sepak", "Hoki", "Ragbi"},

            // ---------------------------------------------------------------- Minda (bahasa & nombor)
            {"Minda", "Berapakah hari dalam seminggu?", "7", "5", "6", "10"},
            {"Minda", "Berapakah bulan dalam setahun?", "12", "10", "11", "13"},
            {"Minda", "Bulan manakah yang paling pendek?", "Februari", "Januari", "April", "Jun"},
            {"Minda", "Berapakah minit dalam sejam?", "60", "100", "30", "24"},
            {"Minda", "Berapakah jam dalam sehari?", "24", "12", "60", "48"},
            {"Minda", "Satu dozen bersamaan dengan…", "12", "10", "6", "20"},
            {"Minda", "Bentuk yang mempunyai 6 sisi dipanggil…", "Heksagon", "Pentagon", "Oktagon", "Segi empat"},
            {"Minda", "Pentagon mempunyai berapa sisi?", "5", "6", "4", "8"},
            {"Minda", "7 × 8 = ?", "56", "54", "64", "48"},
            {"Minda", "12 × 12 = ?", "144", "124", "132", "164"},
            {"Minda", "100 ÷ 4 = ?", "25", "20", "40", "24"},
            {"Minda", "Nombor perdana yang paling kecil ialah…", "2", "1", "0", "3"},
            {"Minda", "Nombor Roman 'X' bermaksud…", "10", "5", "50", "100"},
            {"Minda", "Nombor Roman 'L' bermaksud…", "50", "10", "100", "500"},
            {"Minda", "1 kilogram bersamaan dengan…", "1000 gram", "100 gram", "10 gram", "500 gram"},
            {"Minda", "1 meter bersamaan dengan…", "100 cm", "10 cm", "1000 cm", "50 cm"},
            {"Minda", "Sudut tegak (sudut tepat) bernilai…", "90°", "45°", "180°", "360°"},
            {"Minda", "Jumlah semua sudut dalam segi tiga ialah…", "180°", "90°", "360°", "270°"},
            {"Minda", "Apakah antonim bagi 'rajin'?", "Malas", "Pandai", "Cepat", "Kuat"},
            {"Minda", "Apakah sinonim bagi 'gembira'?", "Suka", "Sedih", "Marah", "Takut"},
            {"Minda", "'Bagai aur dengan tebing' bermaksud…", "Saling membantu", "Bergaduh", "Malas bekerja", "Takut air"},
            {"Minda", "'Sediakan payung sebelum hujan' bermaksud…", "Bersedia lebih awal", "Suka hujan", "Membeli payung", "Takut basah"},
            {"Minda", "Apakah perkataan Inggeris bagi 'kucing'?", "Cat", "Dog", "Rat", "Cow"},
            {"Minda", "Hari selepas Jumaat ialah…", "Sabtu", "Khamis", "Ahad", "Isnin"},
            {"Minda", "Separuh daripada 1 jam ialah…", "30 minit", "15 minit", "45 minit", "60 minit"},
            {"Minda", "Jika hari ini Isnin, 3 hari lagi ialah hari…", "Khamis", "Rabu", "Jumaat", "Selasa"},

            // ---------------------------------------------------------------- Teknologi
            {"Teknologi", "Bahagian komputer yang dikenali sebagai 'otak komputer' ialah…", "CPU", "Monitor", "Tetikus", "Pembesar suara"},
            {"Teknologi", "Peranti yang digunakan untuk menaip ialah…", "Papan kekunci", "Tetikus", "Pencetak", "Monitor"},
            {"Teknologi", "WWW ialah singkatan bagi…", "World Wide Web", "World Wild West", "Web Wide World", "Wide World Window"},
            {"Teknologi", "Sistem Android dibangunkan oleh syarikat…", "Google", "Apple", "Samsung", "Microsoft"},
            {"Teknologi", "Syarikat yang logonya epal digigit ialah…", "Apple", "Google", "Samsung", "Nokia"},
            {"Teknologi", "Unit storan manakah yang paling besar?", "Gigabait", "Megabait", "Kilobait", "Bait"},
            {"Teknologi", "Alat yang mencetak dokumen di atas kertas ialah…", "Pencetak", "Pengimbas", "Router", "Kamera"},
            {"Teknologi", "Kata laluan yang paling selamat ialah…", "Campuran huruf, nombor dan simbol", "123456", "Nama sendiri", "Tarikh lahir"},
            {"Teknologi", "Jika orang tidak dikenali di internet meminta alamat rumah, kita patut…", "Tidak beri dan beritahu ibu bapa", "Terus beri", "Beri nombor telefon", "Ajak berjumpa"},
    };

    private static final String PREF = "juara", KEY = "recent";

    /** `n` questions in random order, avoiding the ones asked in recent games (kept in preferences). */
    static List<String[]> pick(Context c, int n, Random rnd) {
        SharedPreferences sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        Set<String> recent = new HashSet<>(sp.getStringSet(KEY, new HashSet<String>()));
        List<String[]> fresh = new ArrayList<>(), old = new ArrayList<>();
        for (String[] q : Q) (recent.contains(q[1]) ? old : fresh).add(q);
        if (fresh.size() < n) { recent.clear(); fresh.addAll(old); old.clear(); }   // every question asked: start over
        Collections.shuffle(fresh, rnd);
        List<String[]> out = new ArrayList<>(fresh.subList(0, Math.min(n, fresh.size())));
        for (String[] q : out) recent.add(q[1]);
        sp.edit().putStringSet(KEY, recent).apply();
        return out;
    }

    /** One row as a quiz question, the options shuffled. */
    static LearnBank.Q asQ(String[] row, Random rnd) {
        List<String> opts = new ArrayList<>();
        for (int i = 2; i < 6; i++) opts.add(row[i]);
        Collections.shuffle(opts, rnd);
        return new LearnBank.Q(LearnBank.SAINS, 3, "j:" + row[1], row[1], opts.toArray(new String[0]), opts.indexOf(row[2]), row[0]);
    }

    /** Adds every row to Kelas Bijak's "Sains & Umum" bank. */
    static void addTo(List<LearnBank.Item> items) {
        for (String[] r : Q) items.add(new LearnBank.Item(LearnBank.SAINS, 2, r[1], "Jawapan: " + r[2], r[2], r[3], r[4], r[5]));
    }
}
