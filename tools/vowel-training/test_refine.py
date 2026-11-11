import json
import tempfile
import unittest
from pathlib import Path
import numpy as np
import refine


def row(category=0, domain="phone", split="validation", clip="clip", time=0, values=True):
    return {"id": f"{domain}:{clip}:{time}", "category": category, "domain": domain, "split": split,
            "clip": clip, "group": clip, "elapsedMs": time, "handCount": 1 if values else 0,
            "f63": [float(category)] * 63 if values else None,
            "f93": [float(category)] * 93 if values else None, "baselineScores": None}


class RefinementTest(unittest.TestCase):
    def test_five_hundred_ms_and_unknown_reset(self):
        frames = [(row(time=t), [1, 0, 0, 0, 0, 0]) for t in range(0, 701, 100)]
        self.assertEqual(-1, refine.replay(frames, .5, .05)[4][1])
        self.assertEqual(0, refine.replay(frames, .5, .05)[5][1])
        frames[3] = (row(time=300, values=False), None)
        self.assertTrue(all(label == -1 for _, label in refine.replay(frames, .5, .05)))

    def test_gap_and_two_hands_reset(self):
        frames = [(row(time=t), [1, 0, 0, 0, 0, 0]) for t in (0, 100, 200, 600, 700, 800)]
        self.assertTrue(all(label == -1 for _, label in refine.replay(frames, .5, .05)))
        frames[0][0]["handCount"] = 2
        self.assertEqual(-1, refine.replay(frames, .5, .05)[0][1])

    def test_wrong_stabilized_letter_wins_over_later_correct(self):
        frames = [row(category=3, time=t) for t in range(0, 1601, 100)]
        scores = [[1, 0, 0, 0, 0, 0] if t < 800 else [0, 0, 0, 1, 0, 0] for t in range(0, 1601, 100)]
        report = refine.metrics(frames, scores, .5, .05, "phone")
        self.assertEqual(1, report["confusionMatrix"][3][0])
        self.assertEqual(0, report["recall"][3])

    def test_missing_tracking_is_not_dropped(self):
        report = refine.metrics([row(category=3, values=False)], [None], .5, .05, "phone")
        self.assertEqual(1, report["confusionMatrix"][3][5]); self.assertEqual(0, report["trackingCoverage"])

    def test_mirrors_preserve_geometry_and_identity(self):
        features = np.arange(93, dtype=np.float32)
        mirror = refine.mirrored(features)
        np.testing.assert_array_equal(features[63:], mirror[63:])
        np.testing.assert_array_equal(features, refine.mirrored(mirror))

    def test_equal_class_domain_clip_weight_and_no_test_training(self):
        rows = []
        for category in range(6):
            for domain in ("public", "phone"):
                for time in (0, 100): rows.append(row(category, domain, "train", f"{category}-{domain}", time))
        rows.append(row(0, "phone", "test", "not-training"))
        x, y, weights = refine.training_arrays(rows, "f63")
        self.assertEqual(48, len(x))
        totals = [weights[y == i].sum() for i in range(6)]
        np.testing.assert_allclose(totals, [totals[0]] * 6)
        base = refine.fit_identity(rows); rows[-1]["f63"][0] = 999
        self.assertEqual(base, refine.fit_identity(rows))

    def test_leaked_group_and_nonfinite_data_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "features.jsonl"
            header = {"kind": "vowel-refinement-features", "schemaVersion": 1}
            rows = [row(split="train"), row(split="test", time=100)]
            path.write_text("\n".join(map(json.dumps, [header] + rows)), encoding="utf-8")
            with self.assertRaises(ValueError): refine.load(path)
            rows = [row()]; rows[0]["f63"][0] = float("nan")
            path.write_text("\n".join(map(json.dumps, [header] + rows)), encoding="utf-8")
            with self.assertRaises(ValueError): refine.load(path)

    def test_ambiguous_and_other_scores_rejected(self):
        self.assertEqual(-1, refine.accepted([.5, .5, 0, 0, 0, 0], .4, .05))
        self.assertEqual(-1, refine.accepted([0, 0, 0, 0, 0, 1], .4, .05))

    def test_frame_and_stabilized_metrics_are_separate(self):
        rows = [row(time=0), row(time=100)]
        scores = [[1, 0, 0, 0, 0, 0]] * 2
        self.assertEqual(1, refine.frame_metrics(rows, scores, .5, .05)["recall"][0])
        self.assertEqual(0, refine.metrics(rows, scores, .5, .05, "phone")["recall"][0])

    def test_replay_uses_source_inference_timestamps(self):
        frames = [(row(time=t), [1, 0, 0, 0, 0, 0]) for t in range(0, 701, 100)]
        for index, (sample, _) in enumerate(frames): sample["timestampMs"] = 100000 + index * 70
        # Seven UI receipts can span 600 ms while the camera input spans only 420 ms.
        self.assertTrue(all(label == -1 for _, label in refine.replay(frames[:7], .5, .05)))

    def test_unmet_targets_cannot_be_packaged(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "candidate.json").write_text("{}", encoding="utf-8")
            (root / "evaluation.json").write_text(json.dumps({"acceptanceTargetsMet": False, "unmetTargets": ["O recall"]}), encoding="utf-8")
            (root / "java-verification.json").write_text("{}", encoding="utf-8")
            (root / "candidate.bin").write_bytes(b"unchanged baseline required")
            with self.assertRaisesRegex(ValueError, "Acceptance targets"): refine.package(root)
            self.assertFalse((root / "android-bundle").exists())

    def test_packaging_requires_fresh_java_verification_and_preserves_model(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in ("candidate.bin", "parity-features.jsonl", "python-predictions.jsonl"):
                (root / name).write_bytes(name.encode())
            checksum = refine.digest(root / "candidate.bin")
            metadata = {"modelSha256": checksum, "selected": {"features": "f93", "minimumScore": .6, "minimumMargin": .1},
                        "training": "fixture", "provenance": {"phoneZipSha256": "phone", "publicLandmarksSha256": "public"}}
            (root / "candidate.json").write_text(json.dumps(metadata), encoding="utf-8")
            (root / "evaluation.json").write_text(json.dumps({"acceptanceTargetsMet": True, "unmetTargets": [], "modelSha256": checksum, "testPhoneZipSha256": "holdout"}), encoding="utf-8")
            (root / "evaluation.md").write_text("Software fixture", encoding="utf-8")
            verification = {"modelSha256": checksum, "featureDataSha256": "stale", "predictionsSha256": refine.digest(root / "python-predictions.jsonl")}
            (root / "java-verification.json").write_text(json.dumps(verification), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "stale"): refine.package(root)
            verification["featureDataSha256"] = refine.digest(root / "parity-features.jsonl")
            (root / "java-verification.json").write_text(json.dumps(verification), encoding="utf-8")
            refine.package(root)
            self.assertEqual(checksum, refine.digest(root / "android-bundle/vowel_forest.bin"))
            self.assertIn("wrist-palm-geometry-v2", (root / "android-bundle/vowel_model.properties").read_text())


if __name__ == "__main__": unittest.main()
