"""model_agreement.py 계산 확인 (가상 예시 sample/로). 실행: python3 -I docs/evaluation/test_model_agreement.py"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from model_agreement import compute  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))


class ModelAgreementTest(unittest.TestCase):
    def setUp(self):
        self.result = compute(os.path.join(HERE, "sample", "judgments.csv"),
                              os.path.join(HERE, "sample", "labels.csv"))

    def test_정답표가_없는_개념은_정확도에서_뺀다(self):
        self.assertEqual(self.result["labeled_concepts"], 3)
        self.assertEqual(self.result["all_concepts"], 4)

    def test_모델별_다수_의견_일치율(self):
        self.assertEqual(self.result["model_agreement"]["gpt"]["numerator"], 1)
        self.assertEqual(self.result["model_agreement"]["claude"]["numerator"], 2)
        self.assertEqual(self.result["model_agreement"]["gemini"]["denominator"], 3)
        self.assertEqual(self.result["majority_agreement"]["numerator"], 1)
        self.assertEqual(self.result["majority_agreement"]["denominator"], 2)

    def test_불일치_보류_근거_실패_호출_실패(self):
        self.assertEqual(self.result["disagreement_rate"]["rate"], 0.3333)
        self.assertEqual(self.result["hold_rate"]["numerator"], 1)
        self.assertEqual(self.result["evidence_failure_rate"]["denominator"], 10)
        self.assertEqual(self.result["model_failure_rate"]["rate"], 0.2)


if __name__ == "__main__":
    unittest.main()
