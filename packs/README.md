# Pek soalan harian (Kelas Bijak)

Free My TV (fork uzzair-extras) memuat turun `packs/soalan.json` dari cabang `uzzair-extras` sekali sehari
(`LearnStore.packUrl`) dan menambah soalannya ke Kelas Bijak. Setiap muat turun menggantikan pek sebelumnya di TV.

Format:

```json
{"version": "YYYY-MM-DD", "items": [{"s": 1, "l": 2, "q": "Soalan", "a": ["betul", "salah", "salah", "salah"], "e": "Penerangan", "d": "YYYY-MM-DD"}]}
```

- `s`: 1 Bahasa Melayu, 2 English, 3 Sains & Umum (Matematik dijana oleh aplikasi).
- `l`: tahap 1-8 (1-2 umur 7-8, 3-4 umur 9-10, 5-6 umur 11-12, 7-8 umur 13-14).
- `a[0]` jawapan betul; 3 jawapan salah selepasnya.
- `d`: tarikh soalan ditambah.
- Soalan dalam BM yang betul; fakta yang tidak berubah dengan masa; tiada soalan yang pernah ada sebelum ini.
- Saiz fail di bawah 2 MB.

## Menambah soalan

Tulis soalan baru dalam satu fail senarai (contohnya `packs/baru.json`: `[{"s":..,"l":..,"q":..,"a":[..],"e":..}]`),
kemudian:

```bash
python3 packs/kemas.py packs/baru.json
```

Skrip itu menolak semuanya jika ada soalan yang berulang (dengan pek, dengan `arkib.txt` atau dengan bank soalan
aplikasi), kemudian menambahnya dengan tarikh hari ini, membuang soalan yang lebih lama daripada 30 hari, dan
menyimpan teks setiap soalan dalam `arkib.txt` supaya soalan yang sudah dibuang tidak diulang. TV tidak memuat
turun `arkib.txt`.
