#!/usr/bin/env python3
"""Tambah soalan baru ke pek, buang yang lama, dan simpan arkib supaya tiada ulangan.

  python3 packs/kemas.py packs/baru.json

baru.json ialah senarai soalan baru dalam format pek ([{"s":..,"l":..,"q":..,"a":[..4..],"e":..}, ...]).
Skrip ini:
  1. menolak semuanya (tiada yang ditulis) jika ada soalan yang sudah ada dalam pek, dalam arkib.txt
     (semua soalan yang pernah masuk, termasuk yang sudah dibuang), dalam bank soalan aplikasi
     (LearnBankData/LearnBankMore/JuaraBank.java), atau berganda sesama sendiri;
  2. menambahnya dengan "d" = tarikh hari ini (waktu Malaysia) dan menukar "version";
  3. membuang soalan yang lebih lama daripada HARI hari (TV hanya memuat turun soalan.json);
  4. menambah teks soalan baru ke arkib.txt, dan memadam baru.json.
"""
import datetime, json, os, re, sys
from zoneinfo import ZoneInfo

HARI = 30
DIR = os.path.dirname(os.path.abspath(__file__))
PEK, ARKIB = os.path.join(DIR, "soalan.json"), os.path.join(DIR, "arkib.txt")
JAVA = [os.path.join(DIR, "..", "app", "src", "io", "freemytv", n)
        for n in ("LearnBankData.java", "LearnBankMore.java", "JuaraBank.java")]


def kunci(q):
    return re.sub(r"\s+", " ", q).strip().lower()


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    baru = json.load(open(sys.argv[1], encoding="utf-8"))
    d = json.load(open(PEK, encoding="utf-8"))
    arkib = [l.rstrip("\n") for l in open(ARKIB, encoding="utf-8") if l.strip()] if os.path.exists(ARKIB) else []
    ada = {kunci(x) for x in arkib} | {kunci(x["q"]) for x in d["items"]}
    for j in JAVA:
        ada |= {kunci(m) for m in re.findall(r'"((?:[^"\\]|\\.)*)"', open(j, encoding="utf-8").read())}
    salah, dilihat = [], set()
    for it in baru:
        q = it.get("q", "")
        k = kunci(q)
        if k in ada or k in dilihat:
            salah.append("ulang: " + q)
        dilihat.add(k)
        a = it.get("a", [])
        if len(a) != 4 or len(set(a)) != 4:
            salah.append("perlu 4 jawapan berlainan: " + q)
        if it.get("s") not in (1, 2, 3) or not isinstance(it.get("l"), int) or not 1 <= it["l"] <= 8:
            salah.append("s/l tidak sah: " + q)
        if not it.get("e"):
            salah.append("tiada penerangan: " + q)
    if salah:
        sys.exit("Tiada apa ditulis. Betulkan soalan ini:\n  " + "\n  ".join(salah))
    hari_ini = datetime.datetime.now(ZoneInfo("Asia/Kuala_Lumpur")).date()
    for it in d["items"]:
        it.setdefault("d", d["version"])
    for it in baru:
        d["items"].append({"s": it["s"], "l": it["l"], "q": it["q"], "a": it["a"], "e": it["e"], "d": hari_ini.isoformat()})
        arkib.append(it["q"].replace("\n", " "))
    had = (hari_ini - datetime.timedelta(days=HARI)).isoformat()
    sebelum = len(d["items"])
    d["items"] = [it for it in d["items"] if it["d"] > had]
    d["version"] = hari_ini.isoformat()
    teks = json.dumps(d, ensure_ascii=False, indent=1) + "\n"
    if len(teks.encode("utf-8")) > 2_000_000:
        sys.exit("Tiada apa ditulis: soalan.json akan melebihi 2 MB.")
    open(PEK, "w", encoding="utf-8").write(teks)
    open(ARKIB, "w", encoding="utf-8").write("\n".join(arkib) + "\n")
    os.remove(sys.argv[1])
    print(f"+{len(baru)} soalan; {sebelum - len(d['items'])} dibuang (sebelum {had}); "
          f"{len(d['items'])} dalam pek ({len(teks.encode()) // 1024} KB); arkib {len(arkib)}")


main()
