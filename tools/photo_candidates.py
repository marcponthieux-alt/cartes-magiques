"""Cherche des photos libres de droits sur Wikimedia Commons pour chaque symbole.
Sortie : candidates/<id>_<k>.jpg, candidates/meta.json, planches/planche_<n>.jpg"""
import json, os, re, time, urllib.parse, urllib.request
from PIL import Image, ImageDraw

UA = "CartesMagiques/1.0 (https://github.com/marcponthieux-alt/cartes-magiques; educational app)"
QUERIES = {
    "poste": "orienteering control flag forest",
    "decouvert": "forest clearing meadow",
    "foret": "open beech forest",
    "difficile": "dense thicket undergrowth",
    "encombre": "bracken fern meadow",
    "lac": "forest lake shore",
    "ruisseau": "forest brook",
    "marais": "bog marsh wetland",
    "puits": "old stone well",
    "falaise": "rock cliff in forest",
    "bloc": "erratic boulder forest",
    "groupe_blocs": "boulders forest group",
    "pierreux": "stony ground forest stones",
    "affleurement": "bare rock outcrop",
    "talus": "earth bank slope forest path",
    "depression": "sinkhole doline forest",
    "route": "forest road asphalt",
    "carrossable": "gravel forest track",
    "sentier": "forest footpath trail",
    "cloture": "wooden fence pasture",
    "batiment": "forest hut cabin",
    "gros_arbre": "solitary old oak tree",
}
OK = re.compile(r"^(CC0|Public domain|PD|CC BY(-SA)? [0-9.]+|CC BY(-SA)?)", re.I)

def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()

def strip(html):
    return re.sub(r"<[^>]+>", "", html or "").strip()

os.makedirs("candidates", exist_ok=True); os.makedirs("planches", exist_ok=True)
meta = {}
for sid, q in QUERIES.items():
    params = {
        "action": "query", "format": "json", "generator": "search",
        "gsrsearch": q + " filetype:bitmap", "gsrnamespace": "6", "gsrlimit": "12",
        "prop": "imageinfo", "iiprop": "url|extmetadata|size", "iiurlwidth": "640",
    }
    url = "https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode(params)
    try:
        data = json.loads(get(url))
    except Exception as e:
        print("search failed", sid, e); continue
    pages = sorted(data.get("query", {}).get("pages", {}).values(), key=lambda p: p.get("index", 99))
    k = 0
    for p in pages:
        if k >= 6: break
        ii = (p.get("imageinfo") or [{}])[0]
        em = ii.get("extmetadata", {})
        lic = strip(em.get("LicenseShortName", {}).get("value"))
        if not OK.match(lic) or ii.get("width", 0) < 800: continue
        thumb = ii.get("thumburl")
        if not thumb: continue
        try:
            raw = get(thumb)
        except Exception as e:
            print("dl failed", p["title"], e); continue
        fn = f"candidates/{sid}_{k}.jpg"
        open(fn, "wb").write(raw)
        meta[f"{sid}_{k}"] = {
            "symbol": sid, "title": p["title"], "artist": strip(em.get("Artist", {}).get("value"))[:120],
            "license": lic, "license_url": em.get("LicenseUrl", {}).get("value", ""),
            "page": ii.get("descriptionurl", ""),
        }
        k += 1
        time.sleep(0.3)
    print(sid, k)
json.dump(meta, open("candidates/meta.json", "w"), ensure_ascii=False, indent=1)

# Planches de contrôle : 4 symboles par planche, 6 candidates par ligne
ids = list(QUERIES)
for n in range(0, len(ids), 4):
    sheet = Image.new("RGB", (6 * 210 + 10, 4 * 190 + 10), "white"); d = ImageDraw.Draw(sheet)
    for r, sid in enumerate(ids[n:n + 4]):
        d.text((5, r * 190 + 2), sid, fill="black")
        for k in range(6):
            fn = f"candidates/{sid}_{k}.jpg"
            if not os.path.exists(fn): continue
            im = Image.open(fn).convert("RGB"); im.thumbnail((200, 165))
            sheet.paste(im, (5 + k * 210, r * 190 + 18))
            d.text((8 + k * 210, r * 190 + 20), str(k), fill="red")
    sheet.save(f"planches/planche_{n // 4}.jpg", quality=85)
