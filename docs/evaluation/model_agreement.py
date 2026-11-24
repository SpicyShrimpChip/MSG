#!/usr/bin/env python3
"""세 모델 비교 실험 지표 계산 (오프라인, 표준 라이브러리만, AI를 부르지 않는다).

입력
  judgments.csv : model-judgments-export.sql로 내보낸 파일
  labels.csv    : 사람이 미리 정한 정답표. 열: run_id, concept_key, expert_status
                  (understood / partial / not_understood / insufficient_evidence, 대소문자 무관)
출력: 지표와 표본 수 (JSON). 정답표에 없는 개념은 정확도 계산에서 뺀다.

지표 (모두 비율 0~1과 분모를 함께 낸다. 분모가 0이면 null)
  model_agreement[provider] : 그 모델이 판단한 정답표 개념 중 전문가와 같은 상태의 비율
  majority_agreement        : 다수 의견(UNANIMOUS·MAJORITY)이 있는 정답표 개념 중 전문가와 같은 비율
  disagreement_rate         : 판단한 모델이 2개 이상인 개념 중 DISAGREEMENT 비율 (정답표와 무관)
  hold_rate                 : 다수 의견이 insufficient_evidence인 개념 비율 (판단 보류)
  evidence_failure_rate     : 모델 답의 근거 중 서버 검증에서 버린 비율 (dropped / (dropped + kept))
  model_failure_rate        : 부른 모델 호출 중 실패·형식 오류 비율

해석 주의: 모델끼리 같은 의견이라는 것은 정확도가 아니다. 정답표가 있는 표본만으로 일치율을 말하고,
표본 수를 함께 밝힌다. 선행연구의 수치를 이 서비스의 성능처럼 쓰지 않는다.
"""
import csv
import json
import sys
from collections import defaultdict

STATUSES = {"understood", "partial", "not_understood", "insufficient_evidence"}


def rate(numerator, denominator):
    return {"rate": None if denominator == 0 else round(numerator / denominator, 4),
            "numerator": numerator, "denominator": denominator}


def read_labels(path):
    labels = {}
    with open(path, newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            status = (row.get("expert_status") or "").strip().lower()
            if status not in STATUSES:
                continue  # 정답이 없거나 잘못된 줄은 빼고 센다
            labels[(row["run_id"].strip(), row["concept_key"].strip())] = status
    return labels


def compute(judgments_path, labels_path):
    labels = read_labels(labels_path)
    concepts = {}
    per_model = defaultdict(lambda: [0, 0])  # provider -> [맞음, 판단한 정답표 개념]
    kept = defaultdict(int)
    dropped_by_result = {}
    calls = [0, 0]  # [실패, 부른 호출]
    seen_calls = set()
    with open(judgments_path, newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            key = (row["run_id"], row["concept_key"])
            concepts[key] = row
            provider = row["provider"].lower()
            status = (row.get("model_status") or "").upper()
            call_key = (row["run_id"], provider)
            if call_key not in seen_calls and status != "NOT_CONFIGURED":
                seen_calls.add(call_key)
                calls[1] += 1
                if status in ("FAILED", "INVALID_OUTPUT"):
                    calls[0] += 1
                dropped_by_result[call_key] = int(row.get("dropped_evidence_model") or 0)
            kept[call_key] += int(row.get("kept_evidence") or 0)
            judged = (row.get("judged_status") or "").strip().lower()
            if judged and key in labels:
                per_model[provider][1] += 1
                per_model[provider][0] += int(judged == labels[key])
    majority = [0, 0]
    disagreement = [0, 0]
    hold = [0, 0]
    for key, row in concepts.items():
        outcome = row["consensus_outcome"].upper()
        consensus = (row.get("consensus_status") or "").strip().lower()
        if int(row.get("judged_count") or 0) >= 2:
            disagreement[1] += 1
            disagreement[0] += int(outcome == "DISAGREEMENT")
        if outcome in ("UNANIMOUS", "MAJORITY", "SINGLE_MODEL"):
            hold[1] += 1
            hold[0] += int(consensus == "insufficient_evidence")
        if outcome in ("UNANIMOUS", "MAJORITY") and key in labels:
            majority[1] += 1
            majority[0] += int(consensus == labels[key])
    total_dropped = sum(dropped_by_result.values())
    total_kept = sum(kept.values())
    return {
        "labeled_concepts": sum(1 for key in concepts if key in labels),
        "all_concepts": len(concepts),
        "model_agreement": {p: rate(v[0], v[1]) for p, v in sorted(per_model.items())},
        "majority_agreement": rate(*majority),
        "disagreement_rate": rate(*disagreement),
        "hold_rate": rate(*hold),
        "evidence_failure_rate": rate(total_dropped, total_dropped + total_kept),
        "model_failure_rate": rate(*calls),
        "note": "모델 의견 일치는 정확도가 아니다. 정답표가 있는 표본만 일치율을 계산했다.",
    }


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("사용: python3 -I model_agreement.py judgments.csv labels.csv", file=sys.stderr)
        sys.exit(2)
    print(json.dumps(compute(sys.argv[1], sys.argv[2]), ensure_ascii=False, indent=2))
