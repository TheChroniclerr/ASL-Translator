"""Explicit desktop-only training. Android uses shared Java features and a binary forest."""
from __future__ import annotations

import argparse
import hashlib
import json
import struct
import shutil
from pathlib import Path
from collections import defaultdict
import numpy as np
import joblib
from sklearn.ensemble import RandomForestClassifier

LABELS = ["A", "E", "I", "O", "U", "OTHER"]
VERSIONS = {"f63": "wrist-palm-aspect-v1", "f93": "wrist-palm-geometry-v2"}


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def load(path):
    with Path(path).open(encoding="utf-8") as source:
        header = json.loads(next(source))
        if header.get("kind") != "vowel-refinement-features" or header.get("schemaVersion") != 1:
            raise ValueError("Unsupported Java feature export")
        rows = [json.loads(line) for line in source]
    ids, groups = set(), {}
    for row in rows:
        if row["id"] in ids or row["split"] not in ("train", "validation", "test"):
            raise ValueError("Duplicate identity or invalid split")
        ids.add(row["id"])
        if groups.setdefault(row["group"], row["split"]) != row["split"]:
            raise ValueError("Session/duplicate group leaked across splits")
        if row["category"] not in range(6) or row["domain"] not in ("public", "phone"):
            raise ValueError("Invalid label/domain")
        for field, count in (("f63", 63), ("f93", 93)):
            values = row[field]
            if values is not None and (len(values) != count or not np.isfinite(values).all()):
                raise ValueError("Invalid feature vector")
    return header, rows


def fit_identity(rows):
    # Test content never influences fitting, selection, or the frozen identity.
    selected = sorted((r for r in rows if r["split"] != "test"), key=lambda r: r["id"])
    return hashlib.sha256(json.dumps(selected, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def mirrored(values):
    result = np.asarray(values, dtype=np.float32).copy()
    result[0:63:3] *= -1
    return result


def training_arrays(rows, field):
    selected = [r for r in rows if r["split"] == "train" and r[field] is not None and r["handCount"] == 1]
    x, y, weights = [], [], []
    for label in range(6):
        for domain in ("public", "phone"):
            group = [r for r in selected if r["category"] == label and r["domain"] == domain]
            if not group:
                raise ValueError(f"Missing valid {domain} training data for {LABELS[label]} ({field})")
            clips = defaultdict(list)
            for row in group:
                clips[row["clip"]].append(row)
            for clip in clips.values():
                # Equal category totals; half public/phone within category; equal clips.
                # Mirrors split an example's weight rather than changing its class balance.
                weight = 1.0 / (12 * len(clips) * len(clip) * 2)
                for row in clip:
                    x.extend((row[field], mirrored(row[field])))
                    y.extend((label, label)); weights.extend((weight, weight))
    # Preserve the relative weights while keeping a convenient mean of one.
    weights = np.asarray(weights); weights *= len(weights) / weights.sum()
    return np.asarray(x, dtype=np.float32), np.asarray(y), weights


def predict_rows(model, rows, field):
    scores = [None] * len(rows)
    valid = [i for i, row in enumerate(rows) if row[field] is not None and row["handCount"] == 1]
    if valid:
        predicted = model.predict_proba(np.asarray([rows[i][field] for i in valid], dtype=np.float32))
        for index, values in zip(valid, predicted): scores[index] = values.tolist()
    return scores


def accepted(scores, minimum, margin):
    if scores is None: return -1
    best = int(np.argmax(scores))
    runner_up = max(score for i, score in enumerate(scores) if i != best)
    return best if best < 5 and scores[best] >= minimum and scores[best] - runner_up >= margin else -1


def replay(frames, minimum, margin):
    """Matches VowelClassifier: 500 ms hold; reset on invalid/missing/ambiguous data or >250 ms gap."""
    candidate, since, previous = -1, -1, -1
    displays = []
    for row, scores in sorted(frames, key=lambda pair: pair[0]["elapsedMs"]):
        timestamp = row.get("timestampMs", row["elapsedMs"])
        if previous >= 0 and (timestamp <= previous or timestamp - previous > 250):
            candidate, since = -1, -1
        previous = timestamp
        prediction = accepted(scores, minimum, margin) if row["handCount"] == 1 else -1
        if prediction < 0:
            candidate, since = -1, -1
            displays.append((row["elapsedMs"], -1)); continue
        if candidate != prediction:
            candidate, since = prediction, timestamp
        displays.append((row["elapsedMs"], candidate if timestamp - since >= 500 else -1))
    return displays


def metrics(rows, scores, minimum, margin, domain):
    matrix = np.zeros((6, 6), dtype=int)  # final column means Unknown
    clips = defaultdict(list); delays = []
    tracked, total = 0, 0
    per_class_tracked, per_class_total = np.zeros(6), np.zeros(6)
    for row, prediction in zip(rows, scores):
        if row["domain"] != domain: continue
        clips[row["clip"]].append((row, prediction))
        total += 1; tracked += int(row["handCount"] == 1)
        per_class_total[row["category"]] += 1; per_class_tracked[row["category"]] += int(row["handCount"] == 1)
    for frames in clips.values():
        actual = frames[0][0]["category"]
        if domain == "public":
            displayed = accepted(frames[0][1], minimum, margin)
        else:
            displays = replay(frames, minimum, margin)
            wrong = [(t, label) for t, label in displays if label >= 0 and label != actual]
            correct = [(t, label) for t, label in displays if label == actual]
            # Any incorrect stabilized vowel takes precedence over a later correction.
            displayed = wrong[0][1] if wrong else actual if correct else -1
            if correct and not wrong: delays.append(correct[0][0])
        matrix[actual, displayed if displayed >= 0 else 5] += 1
    precision, recall = [], []
    for label in range(5):
        precision.append(float(matrix[label, label] / max(1, matrix[:, label].sum())))
        recall.append(float(matrix[label, label] / max(1, matrix[label].sum())))
    f1 = [2*p*r / (p+r) if p+r else 0 for p, r in zip(precision, recall)]
    return {"precision": precision, "recall": recall, "macroF1": float(np.mean(f1)),
            "eoF1": float(np.mean([f1[1], f1[3]])), "confusionMatrix": matrix.tolist(),
            "clipsOrImages": int(matrix.sum()), "classCounts": matrix.sum(axis=1).tolist(),
            "rejectionRate": float(matrix[:, 5].sum() / max(1, matrix.sum())),
            "falseOtherRate": float(matrix[5, :5].sum() / max(1, matrix[5].sum())),
            "trackingCoverage": tracked/max(1, total),
            "trackingCoverageByClass": (per_class_tracked / np.maximum(1, per_class_total)).tolist(),
            "medianRecognitionDelayMs": float(np.median(delays)) if delays else None}


def frame_metrics(rows, scores, minimum, margin):
    copies, selected_scores = [], []
    for row, prediction in zip(rows, scores):
        if row["domain"] != "phone": continue
        copy = dict(row); copy["domain"] = "public"; copy["clip"] = row["id"]
        copies.append(copy); selected_scores.append(prediction)
    return metrics(copies, selected_scores, minimum, margin, "public")


def gates(candidate, baseline):
    reasons = []
    for label in (1, 3):
        if candidate["precision"][label] < .90 or candidate["recall"][label] < .80:
            reasons.append(f"{LABELS[label]} misses precision/recall target")
        def f1(report):
            p, r = report["precision"][label], report["recall"][label]
            return 2*p*r/(p+r) if p+r else 0
        if f1(candidate) <= f1(baseline) + 1e-12:
            reasons.append(f"{LABELS[label]} F1 does not exceed baseline")
    for label in (0, 2, 4):
        for metric in ("precision", "recall"):
            if candidate[metric][label] + .05 + 1e-12 < baseline[metric][label]:
                reasons.append(f"{LABELS[label]} {metric} regresses by more than five points")
    if candidate["falseOtherRate"] > .05: reasons.append("Other false detection exceeds 5%")
    if any(count == 0 for count in candidate["classCounts"]): reasons.append("Missing evaluation category")
    if any(count < required for count, required in zip(candidate["classCounts"], (10, 20, 10, 20, 10, 10))):
        reasons.append("Complete the guided 80-hold evaluation session before promotion")
    return reasons


def write_binary(model, path, field, minimum, margin):
    def utf(out, value):
        data = value.encode("ascii"); out.write(struct.pack(">H", len(data))); out.write(data)
    with Path(path).open("wb") as out:
        out.write(struct.pack(">ii", 0x41534C56, 2)); utf(out, VERSIONS[field])
        out.write(struct.pack(">i", int(field[1:])))
        for label in LABELS: utf(out, label)
        out.write(struct.pack(">ddi", minimum, margin, len(model.estimators_)))
        for estimator in model.estimators_:
            tree = estimator.tree_; out.write(struct.pack(">i", tree.node_count))
            if tree.node_count > 16383: raise ValueError("Tree exceeds Android node limit")
            for i in range(tree.node_count):
                leaf = tree.children_left[i] < 0
                values = tree.value[i][0].astype(float); values /= values.sum()
                out.write(struct.pack(">idiii", -1 if leaf else int(tree.feature[i]), float(tree.threshold[i]),
                                      int(tree.children_left[i]), int(tree.children_right[i]), int(np.argmax(values))))
                if leaf: out.write(struct.pack(">6d", *values))


def baseline_scores(rows): return [row["baselineScores"] for row in rows]


def fit(features_path, output):
    header, rows = load(features_path); output = Path(output)
    output.mkdir(parents=True, exist_ok=True)
    if (output / "candidate.json").exists(): raise ValueError("Choose a new output directory; frozen candidates are not overwritten")
    validation = [r for r in rows if r["split"] == "validation"]
    for domain in ("public", "phone"):
        if set(r["category"] for r in validation if r["domain"] == domain) != set(range(6)):
            raise ValueError(f"Validation must include all six categories from {domain}")
    base = metrics(validation, baseline_scores(validation), header["baselineScore"], header["baselineMargin"], "phone")
    base_public = metrics(validation, baseline_scores(validation), header["baselineScore"], header["baselineMargin"], "public")
    best, candidates = None, []
    for field in VERSIONS:
        x, y, weights = training_arrays(rows, field)
        for depth in (12, 20):
            for leaf in (2, 5):
                model = RandomForestClassifier(n_estimators=256, max_depth=depth, min_samples_leaf=leaf,
                    max_features="sqrt", max_leaf_nodes=8192, random_state=470, n_jobs=1)
                model.fit(x, y, sample_weight=weights)
                scores = predict_rows(model, validation, field)
                chosen = None
                for score_step in range(40, 91, 5):
                    for margin_step in range(5, 31, 5):
                        minimum, margin = score_step/100, margin_step/100
                        phone = metrics(validation, scores, minimum, margin, "phone")
                        public = metrics(validation, scores, minimum, margin, "public")
                        reasons = gates(phone, base)
                        public_regression = any(public[key][i] + .05 < base_public[key][i] for key in ("precision", "recall") for i in (0, 2, 4))
                        rank = (not reasons and not public_regression, phone["eoF1"], phone["macroF1"], public["macroF1"], -minimum, -margin)
                        if chosen is None or rank > chosen[0]: chosen = (rank, minimum, margin, phone, public)
                summary = {"features": field, "depth": depth, "minLeaf": leaf, "minimumScore": chosen[1],
                           "minimumMargin": chosen[2], "phoneValidation": chosen[3], "publicValidation": chosen[4]}
                candidates.append(summary)
                print(f"{field}, depth {depth}, leaf {leaf}: E/O F1={chosen[3]['eoF1']:.3f}", flush=True)
                if best is None or chosen[0] > best[0]: best = (chosen[0], model, summary)
    _, model, selected = best
    binary = output / "candidate.bin"
    write_binary(model, binary, selected["features"], selected["minimumScore"], selected["minimumMargin"])
    joblib.dump(model, output / "candidate.joblib")
    metadata = {"schemaVersion": 1, "status": "experimental; not promoted", "fitIdentity": fit_identity(rows),
                "modelSha256": digest(binary), "estimatorSha256": digest(output / "candidate.joblib"),
                "provenance": header, "selected": selected, "candidates": candidates,
                "baselinePhoneValidation": base, "baselinePublicValidation": base_public,
                "training": "256 trees; sqrt features; max 8192 leaves; seed 470; equal class/domain/clip weights; mirrors"}
    (output / "candidate.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    # Parity runs exclude test rows until the explicit final evaluation command.
    parity_rows = [r for r in rows if r["split"] != "test"]
    write_parity(output, header, parity_rows, model, selected["features"], selected["minimumScore"], selected["minimumMargin"])
    print("Candidate frozen. Run Java verify, then collect a fresh final test session before evaluate.")


def write_parity(output, header, rows, model, field, minimum, margin):
    originals = list(rows)
    mirrors = []
    for row in originals:
        copy = dict(row); copy["id"] += ":mirror"; copy["clip"] += ":mirror"
        for name in VERSIONS:
            copy[name] = mirrored(row[name]).tolist() if row[name] is not None else None
        mirrors.append(copy)
    rows = originals + mirrors
    scores = predict_rows(model, rows, field)
    clips, displays = defaultdict(list), {}
    for row, score in zip(rows, scores):
        if row["domain"] == "phone": clips[row["clip"]].append((row, score))
    for frames in clips.values():
        by_time = dict(replay(frames, minimum, margin))
        for row, _ in frames: displays[row["id"]] = by_time[row["elapsedMs"]]
    with (output / "parity-features.jsonl").open("w", encoding="utf-8") as data, (output / "python-predictions.jsonl").open("w", encoding="utf-8") as predictions:
        data.write(json.dumps(header) + "\n")
        for row, score in zip(rows, scores):
            data.write(json.dumps(row) + "\n")
            predictions.write(json.dumps({"id": row["id"], "scores": score, "displayedLabel": displays.get(row["id"], -1)}) + "\n")


def evaluate(features_path, output):
    header, rows = load(features_path); output = Path(output)
    metadata = json.loads((output / "candidate.json").read_text(encoding="utf-8"))
    if metadata["fitIdentity"] != fit_identity(rows): raise ValueError("Training/validation content changed after candidate freeze")
    for key in ("baselineSha256", "manifestSha256", "publicLandmarksSha256", "baselineScore", "baselineMargin"):
        if header[key] != metadata["provenance"][key]: raise ValueError("Public data or baseline changed after candidate freeze")
    if digest(output / "candidate.bin") != metadata["modelSha256"] or digest(output / "candidate.joblib") != metadata["estimatorSha256"]:
        raise ValueError("Frozen model checksum changed")
    if (output / "evaluation.json").exists(): raise ValueError("This candidate has already been tested; keep the report and collect a fresh holdout for future tuning")
    test = [r for r in rows if r["split"] == "test"]
    if set(r["category"] for r in test if r["domain"] == "phone") != set(range(6)):
        raise ValueError("Final phone test requires all six categories")
    # This file is produced by this explicit trainer, never supplied by the phone ZIP.
    model = joblib.load(output / "candidate.joblib")
    selected = metadata["selected"]; scores = predict_rows(model, test, selected["features"])
    phone = metrics(test, scores, selected["minimumScore"], selected["minimumMargin"], "phone")
    base = metrics(test, baseline_scores(test), header["baselineScore"], header["baselineMargin"], "phone")
    public = metrics(test, scores, selected["minimumScore"], selected["minimumMargin"], "public")
    base_public = metrics(test, baseline_scores(test), header["baselineScore"], header["baselineMargin"], "public")
    reasons = gates(phone, base)
    for label in (0, 2, 4):
        for metric in ("precision", "recall"):
            if public[metric][label] + .05 < base_public[metric][label]: reasons.append(f"Public {LABELS[label]} {metric} regression")
    report = {"phone": phone, "baselinePhone": base, "publicRegression": public, "baselinePublic": base_public,
              "phoneFrameClassification": frame_metrics(test, scores, selected["minimumScore"], selected["minimumMargin"]),
              "baselinePhoneFrameClassification": frame_metrics(test, baseline_scores(test), header["baselineScore"], header["baselineMargin"]),
              "acceptanceTargetsMet": not reasons, "unmetTargets": reasons,
              "testPhoneZipSha256": header["phoneZipSha256"], "testFeatureExportSha256": digest(features_path),
              "phoneParticipantCount": len({r.get("participant", "unspecified") for r in test if r["domain"] == "phone"}),
              "limitation": "Session-separated samples from the recorded participant(s); not proof of unseen-person accuracy. Public test was evaluated previously.",
              "modelSha256": metadata["modelSha256"]}
    (output / "evaluation.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    lines = ["# Vowel refinement evaluation", "", report["limitation"], "", "| Letter | Precision | Recall | Baseline recall |", "|---|---:|---:|---:|"]
    for i in range(5): lines.append(f"| {LABELS[i]} | {phone['precision'][i]:.2%} | {phone['recall'][i]:.2%} | {base['recall'][i]:.2%} |")
    lines += ["", f"Other false detections: {phone['falseOtherRate']:.2%}. Rejection: {phone['rejectionRate']:.2%}. Tracking coverage: {phone['trackingCoverage']:.2%}.",
              f"Median recognition delay: {phone['medianRecognitionDelayMs']} ms.", "", "Rows: actual A/E/I/O/U/Other; columns: displayed A/E/I/O/U/Unknown.", "", "```text", *map(str, phone["confusionMatrix"]), "```", "", "Targets met: " + str(not reasons), *["- " + reason for reason in reasons]]
    raw = report["phoneFrameClassification"]
    lines += ["", "## Frame classification before stabilization", "", "| Letter | Precision | Recall |", "|---|---:|---:|"]
    for i in range(5): lines.append(f"| {LABELS[i]} | {raw['precision'][i]:.2%} | {raw['recall'][i]:.2%} |")
    lines += ["", "Tracking coverage by A/E/I/O/U/Other: " + str(phone["trackingCoverageByClass"])]
    (output / "evaluation.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    write_parity(output, header, rows, model, selected["features"], selected["minimumScore"], selected["minimumMargin"])
    print("Final evaluation saved. Targets met:", not reasons, "No app asset was changed.")


def package(output):
    output = Path(output)
    metadata = json.loads((output / "candidate.json").read_text(encoding="utf-8"))
    report = json.loads((output / "evaluation.json").read_text(encoding="utf-8"))
    verification = json.loads((output / "java-verification.json").read_text(encoding="utf-8"))
    checksum = digest(output / "candidate.bin")
    if not report["acceptanceTargetsMet"] or report["unmetTargets"]: raise ValueError("Acceptance targets are not met; retain the baseline")
    if any(record["modelSha256"] != checksum for record in (metadata, report, verification)): raise ValueError("Model checksum mismatch")
    if verification["featureDataSha256"] != digest(output / "parity-features.jsonl") or verification["predictionsSha256"] != digest(output / "python-predictions.jsonl"):
        raise ValueError("Java parity verification is stale; rerun verify after final evaluation")
    bundle = output / "android-bundle"; bundle.mkdir(exist_ok=False)
    shutil.copyfile(output / "candidate.bin", bundle / "vowel_forest.bin")
    shutil.copyfile(output / "evaluation.md", bundle / "evaluation.md")
    selected = metadata["selected"]
    properties = {"model.sha256": checksum, "features": VERSIONS[selected["features"]], "labels": ",".join(LABELS),
                  "dataset": "SignAlphaSet v2 (CC BY 4.0) + opt-in session-separated phone landmarks",
                  "trainer": "scikit-learn 1.9.1; Python 3.12; " + metadata["training"],
                  "minimumScore": selected["minimumScore"], "minimumWinningMargin": selected["minimumMargin"],
                  "test.pipelineTargetsMet": "true", "calibration": "validation-only phone sequences; 500 ms hold",
                  "phoneZip.sha256": metadata["provenance"]["phoneZipSha256"],
                  "phoneTestZip.sha256": report["testPhoneZipSha256"],
                  "publicLandmarks.sha256": metadata["provenance"]["publicLandmarksSha256"]}
    (bundle / "vowel_model.properties").write_text("\n".join(f"{key}={value}" for key, value in properties.items()) + "\n", encoding="ascii")
    print("Verified bundle created at", bundle, "Review and copy explicitly; no app assets were changed.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("fit", "evaluate", "package"))
    parser.add_argument("--features"); parser.add_argument("--output", required=True)
    arguments = parser.parse_args()
    if arguments.command == "package": package(arguments.output)
    elif arguments.features is None: parser.error("--features is required for fit/evaluate")
    else: (fit if arguments.command == "fit" else evaluate)(arguments.features, arguments.output)
