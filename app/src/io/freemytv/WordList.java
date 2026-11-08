package io.freemytv;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Fork extra (Teka Kata): everyday five-letter words in Bahasa Melayu and English, chosen for children. */
final class WordList {
    private static final String BM_RAW = "rumah makan minum tidur malam pagi" +
            " bunga pokok sungai pantai bukit hutan kebun sawah padang jalan kedai pasar sekolah" +
            " ibu ayah abang kakak adik nenek datuk kawan guru murid doktor petani nelayan" +
            " kucing ayam itik lembu kambing harimau gajah burung ikan ular katak semut lebah" +
            " meja kerusi pintu tingkap almari katil bantal selimut cawan pinggan sudu garpu" +
            " merah hijau biru kuning putih hitam ungu coklat" +
            " besar kecil tinggi rendah panas sejuk cepat lambat manis masam pedas masin" +
            " hujan angin awan petir pelangi bulan bintang langit" +
            " kasut baju seluar topi beg buku pensel kertas warna lukis" +
            " lagu tarian muzik gitar drum bola wayang" +
            " roti nasi kuih buah epal pisang durian rambutan nanas tembikai betik" +
            " sabun tuala berus gigi mandi basuh sapu masak" +
            " kereta basikal kapal motor lori teksi" +
            " suka gembira sedih marah takut berani malu senyum ketawa" +
            " jari kaki tangan mata hidung mulut telinga rambut kepala bahu lutut perut" +
            " dunia benua pulau tasik lautan gunung" +
            " rakit layang jambu kelapa pulut santan gulai sayur" +
            " tikar pasir batu tanah rumput daun akar dahan ranting" +
            " belon kotak loceng kunci tali jarum benang gunting" +
            " budak orang raja ratu hakim askar polis bomba" +
            " tahun bulan minggu hari jam minit saat" +
            " surat setem poskad telefon radio" +
            " sihat sakit ubat jururawat hospital klinik" +
            " ilmu sains seni sukan bahasa sejarah";
    private static final String EN_RAW = "apple house water happy smile tiger zebra horse mouse sheep" +
            " bread juice lemon mango grape peach melon pizza pasta salad sugar honey" +
            " chair table couch shelf clock light lamps plant grass stone river ocean beach cloud storm" +
            " green black white brown purple" +
            " beach train plane truck boats wheel" +
            " teach learn study write sport dance music piano drums" +
            " brain heart mouth teeth hands thumb" +
            " world earth space stars comet orbit" +
            " paper pencil books ruler eraser chalk board" +
            " quick slow brave funny proud sleep dream laugh shout" +
            " friend mother father uncle aunty child" +
            " seven three eight forty sixty" +
            " ocean coral shark whale squid crabs" +
            " robot magic giant ghost fairy" +
            " candy toast cream bacon"
            ;

    static List<String> words(boolean bm) {
        List<String> out = new ArrayList<>();
        for (String w : (bm ? BM_RAW : EN_RAW).split("\\s+")) {
            String u = w.trim().toUpperCase(Locale.ROOT);
            if (u.length() == 5 && u.matches("[A-Z]+") && !out.contains(u)) out.add(u);
        }
        return out;
    }

    /** Today's word: the same for everyone on the same day. */
    static String daily(boolean bm) {
        List<String> l = words(bm);
        Calendar c = Calendar.getInstance();
        long seed = c.get(Calendar.YEAR) * 1000L + c.get(Calendar.DAY_OF_YEAR) + (bm ? 0 : 500);
        return l.get(new Random(seed).nextInt(l.size()));
    }

    static String random(boolean bm, Random r) {
        List<String> l = words(bm);
        return l.get(r.nextInt(l.size()));
    }
}
