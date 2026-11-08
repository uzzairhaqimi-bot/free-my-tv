package io.freemytv;

import java.util.Random;

/** Fork extra: short motivational lines (written for this fork) that change every minute on the home screen. */
final class Quotes {
    private static final String[] LINES = {
            "Langkah kecil hari ini, lompatan besar esok.",
            "Rehat bila penat, tapi jangan berhenti.",
            "Setiap pakar dulu pun seorang pemula.",
            "Buat yang terbaik, selebihnya serah pada usaha.",
            "Hari yang sukar tak kekal lama. Orang yang tabah, kekal.",
            "Mula dulu. Sempurna kemudian.",
            "Disiplin hari ini, kebebasan esok.",
            "Jangan bandingkan bab satu awak dengan bab dua puluh orang lain.",
            "Sikit-sikit, lama-lama jadi bukit.",
            "Gagal itu guru, bukan penamat.",
            "Fokus pada apa yang awak boleh kawal.",
            "Senyum dulu, masalah kemudian.",
            "Rezeki tak salah alamat. Teruskan berusaha.",
            "Kerja keras dalam senyap. Biar hasil bersuara.",
            "Satu jam belajar hari ini lebih baik daripada sepuluh jam menyesal esok.",
            "Awak lebih kuat daripada yang awak sangka.",
            "Mimpi besar, mula kecil, bergerak sekarang.",
            "Tak perlu laju. Asalkan tak berhenti.",
            "Bersyukur dengan apa yang ada, berusaha untuk apa yang belum.",
            "Esok bermula dengan pilihan malam ini.",
            "Masa terbaik untuk mula ialah sekarang.",
            "Jaga diri. Awak aset paling penting.",
            "Peluang datang kepada yang bersedia.",
            "Kalau orang lain boleh, awak pun boleh.",
            "Jatuh tujuh kali, bangun lapan kali.",
            "Tenang. Satu-satu.",
            "Hasil tak pernah mengkhianati usaha.",
            "Kurangkan alasan, tambahkan tindakan.",
            "Belajar sesuatu yang baru setiap hari.",
            "Jangan tunggu motivasi. Bina tabiat.",
            "Ilmu itu pelita hidup.",
            "Bila letih, ingat kenapa awak mula.",
            "Berani cuba lebih baik daripada menyesal tak cuba.",
            "Hari ini peluang baru untuk jadi lebih baik.",
            "Orang yang konsisten akan mengalahkan orang yang berbakat tapi malas.",
            "Simpan tenaga untuk perkara yang penting.",
            "Doa, usaha, tawakal.",
            "Setiap hari, satu peratus lebih baik.",
            "Biar lambat asal selamat sampai.",
            "Awak sedang menulis cerita awak sendiri. Jadikan ia menarik.",
    };

    private static final Random R = new Random();
    private static int last = -1;

    static String next() {
        int i;
        do { i = R.nextInt(LINES.length); } while (i == last && LINES.length > 1);
        last = i;
        return LINES[i];
    }
}
