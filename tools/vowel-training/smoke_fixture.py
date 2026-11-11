"""Build synthetic collection-format fixtures from public landmarks. NOT phone evaluation data."""
import argparse
import csv
import json
import uuid
import zipfile
from pathlib import Path


def create(landmarks, manifest, output):
    output = Path(output); output.mkdir(parents=True, exist_ok=True)
    with Path(landmarks).open(encoding="utf-8") as source:
        reader = csv.DictReader(source); columns = reader.fieldnames
        selected = {}
        for row in reader:
            key = (row["letter"], row["split"])
            if row["letter"] in "AEIOUC" and key not in selected: selected[key] = row
    with (output / "public.csv").open("w", newline="", encoding="utf-8") as target:
        writer = csv.DictWriter(target, fieldnames=columns); writer.writeheader(); writer.writerows(selected.values())
    image_ids = {row["id"] for row in selected.values()}
    with Path(manifest).open(encoding="utf-8") as source, (output / "manifest.tsv").open("w", encoding="utf-8", newline="") as target:
        reader = csv.DictReader(source, delimiter="\t"); writer = csv.DictWriter(target, fieldnames=reader.fieldnames, delimiter="\t")
        writer.writeheader(); writer.writerows(row for row in reader if row["id"] in image_ids)
    with zipfile.ZipFile(output / "synthetic-phone.zip", "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("collection.json", json.dumps({"schemaVersion": 1, "synthetic": True}))
        for split in ("train", "validation", "test"):
            session = str(uuid.uuid5(uuid.NAMESPACE_URL, "synthetic-refinement-" + split)); prefix = f"sessions/{session}/"
            archive.writestr(prefix + "session.json", json.dumps({"schemaVersion": 1, "sessionId": session, "split": split, "participant": "SYNTHETIC"}))
            for letter in "AEIOUC":
                row = selected[letter, split]; clip = str(uuid.uuid5(uuid.NAMESPACE_URL, session + letter))
                archive.writestr(prefix + clip + ".json", json.dumps({"schemaVersion": 1, "sessionId": session, "clipId": clip,
                    "label": letter, "requestedHand": "Right", "complete": True, "frameCount": 20, "frames": clip + ".jsonl"}))
                frames = [json.dumps({"elapsedMs": time, "timestampMs": 100000 + time, "width": int(row["width"]), "height": int(row["height"]),
                          "hands": [{"handedness": "Right", "landmarks": [float(row[f"p{i}"]) for i in range(63)]}]}) for time in range(0, 2000, 100)]
                archive.writestr(prefix + clip + ".jsonl", "\n".join(frames) + "\n")
    (output / "README.txt").write_text("SYNTHETIC fixtures repeating public landmarks. Software checks only; never evidence of phone accuracy or a replacement model.\n", encoding="utf-8")
    geometry_parity(output / "geometry-parity")


def geometry_parity(output):
    """Force real sklearn tree branches onto feature IDs 63..92 for Java format checks."""
    import numpy as np
    from sklearn.ensemble import RandomForestClassifier
    import refine
    output.mkdir(parents=True, exist_ok=True)
    random = np.random.default_rng(470)
    values = np.zeros((120, 93), dtype=np.float32)
    values[:, 63:] = random.uniform(-1, 1, (120, 30))
    labels = np.arange(120) % 6
    values[:, 92] = labels / 5
    model = RandomForestClassifier(n_estimators=32, max_depth=6, random_state=470, n_jobs=1).fit(values, labels)
    refine.write_binary(model, output / "candidate.bin", "f93", .5, .05)
    rows = [{"id": f"geometry-{i}", "letter": refine.LABELS[int(label)], "category": int(label), "domain": "public", "split": "validation",
             "group": f"geometry-{i}", "clip": f"geometry-{i}", "elapsedMs": 0, "handCount": 1,
             "f63": [0.0]*63, "f93": features.tolist(), "baselineScores": None} for i, (features, label) in enumerate(zip(values, labels))]
    refine.write_parity(output, {"kind": "vowel-refinement-features", "schemaVersion": 1, "synthetic": True}, rows, model, "f93", .5, .05)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("landmarks"); parser.add_argument("manifest"); parser.add_argument("output")
    args = parser.parse_args(); create(args.landmarks, args.manifest, args.output)
