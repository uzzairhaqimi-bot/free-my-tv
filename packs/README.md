# Pek soalan mingguan (Kelas Bijak)

Free My TV (fork uzzair-extras) memuat turun `packs/soalan.json` dari cabang `uzzair-extras` sekali sehari
(`LearnStore.packUrl`) dan menambah soalannya ke Kelas Bijak.

Format:

```json
{"version": "YYYY-MM-DD", "items": [{"s": 1, "l": 2, "q": "Soalan", "a": ["betul", "salah", "salah", "salah"], "e": "Penerangan"}]}
```

- `s`: 1 Bahasa Melayu, 2 English, 3 Sains & Umum (Matematik dijana oleh aplikasi).
- `l`: tahap 1-8 (1-2 umur 7-8, 3-4 umur 9-10, 5-6 umur 11-12, 7-8 umur 13-14).
- `a[0]` jawapan betul; 3 jawapan salah selepasnya.
- Soalan dalam BM yang betul; fakta yang tidak berubah dengan masa; tiada soalan yang sama dengan pek sebelumnya.
- Saiz fail di bawah 2 MB.
