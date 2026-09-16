"""
final_plants.json 으로 plant_species 초기 데이터 SQL 을 만든다.

사용법 (back 디렉터리에서):
    python3 db/seed/generate_plant_species_sql.py > db/seed/plant_species.sql

규칙
- species_id 는 JSON 의 id 와 같게 넣는다. 도감 상세·식물 등록이 이 id 로 조회한다.
- scientific_name 은 DB 에서 unique 다. JSON 에는 같은 학명이 여러 번 나오므로
  가장 작은 id 만 원래 학명을 쓰고, 나머지는 "학명 [국명]" 으로 구분한다.
  사진 식별 흐름은 원래 학명으로 찾으므로 첫 번째 행과 연결된다.
- 이미 들어 있는 id 는 건너뛰므로 여러 번 실행해도 된다.
"""
import json
import pathlib

SRC = pathlib.Path(__file__).resolve().parents[2] / "src/main/resources/data/final_plants.json"


def q(value):
    """SQL 문자열 리터럴. None 은 NULL."""
    if value is None:
        return "NULL"
    return "'" + str(value).replace("'", "''") + "'"


def watering(p):
    days = p.get("waterSpring")
    if days is None:
        return "NORMAL"
    if days <= 3:
        return "FREQUENT"
    if days >= 14:
        return "INFREQUENT"
    return "NORMAL"


def sunlight(p):
    lux = p.get("lightLux") or ""
    if "낮은" in lux or "반음지" in lux or "음지" == lux.strip():
        return "LOW"
    if "중간" in lux or "반양지" in lux:
        return "MEDIUM"
    if "높은" in lux or "양지" in lux:
        return "HIGH"
    return "MEDIUM"


def difficulty(p):
    d = p.get("displayDifficulty") or ""
    if "초보" in d:
        return "EASY"
    if "전문가" in d:
        return "HARD"
    return "NORMAL"


def main():
    plants = sorted(json.loads(SRC.read_text(encoding="utf-8")), key=lambda p: p["id"])
    seen = set()
    rows = []
    for p in plants:
        name = p["scientificName"].strip()
        if name in seen:
            name = f"{name} [{p['koreanName'].strip()}]"
        seen.add(p["scientificName"].strip())

        rows.append("(" + ", ".join([
            str(p["id"]),
            q(name),
            q(p["koreanName"].strip()),
            q(watering(p)),
            q(sunlight(p)),
            q(difficulty(p)),
            "false" if p.get("isToxic") else "true",
            q((p.get("temp") or None) and p["temp"][:50]),
            q(p.get("description")),
            q(p.get("toxicityInfo")),
            q(p.get("imageUrl")),
            "true",
            "now()",
            "now()",
        ]) + ")")

    print("-- plant_species 초기 데이터 (generate_plant_species_sql.py 로 생성, 직접 수정하지 말 것)")
    print("-- 앱을 한 번 띄워 테이블이 만들어진 뒤 실행한다. 여러 번 실행해도 중복되지 않는다.")
    print("BEGIN;")
    print("INSERT INTO plant_species (species_id, scientific_name, korean_name, watering_cycle_code,")
    print("    sunlight_level_code, difficulty_level, is_pet_friendly, optimal_temp_celsius,")
    print("    management_tip_detail, toxicity_info, official_image_url, is_verified_by_admin,")
    print("    created_at, updated_at)")
    print("VALUES")
    print(",\n".join(rows))
    print("ON CONFLICT (species_id) DO NOTHING;")
    print()
    print("-- species_id 를 직접 넣었으므로, 사진 식별로 새 식물이 추가될 때 id 가 겹치지 않게 번호를 맞춘다.")
    print("SELECT setval(pg_get_serial_sequence('plant_species', 'species_id'),")
    print("              (SELECT MAX(species_id) FROM plant_species));")
    print("COMMIT;")


if __name__ == "__main__":
    main()
